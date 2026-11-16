package com.kARThik.vote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.CRC32

object BtProtocol {
    /** Two separate service records so the pairing-free and the paired (secure) sockets never get mixed up. */
    val UUID_INSECURE: UUID = UUID.fromString("8f0b1c6e-5a1d-4e8a-9c3b-4d6d6a4f7a11")
    val UUID_SECURE: UUID = UUID.fromString("8f0b1c6e-5a1d-4e8a-9c3b-4d6d6a4f7a12")
    const val MAGIC = 0x4D54
    const val MAX_FRAME = 24 * 1024 * 1024
    const val SERVICE_NAME = "MatamVote"
}

enum class LinkState { IDLE, LISTENING, CONNECTING, CONNECTED }

sealed class LinkEvent {
    data class Connected(val name: String, val address: String) : LinkEvent()
    object Disconnected : LinkEvent()
}

/**
 * Frame format: MAGIC(2) | LENGTH(4) | PAYLOAD | CRC32(4).
 * A bad magic, oversize length or CRC mismatch is treated as corruption and drops the connection.
 */
object Framing {
    fun write(out: DataOutputStream, payload: ByteArray) {
        val crc = CRC32().apply { update(payload) }.value.toInt()
        out.writeShort(BtProtocol.MAGIC)
        out.writeInt(payload.size)
        out.write(payload)
        out.writeInt(crc)
        out.flush()
    }

    fun read(input: DataInputStream): ByteArray {
        if (input.readUnsignedShort() != BtProtocol.MAGIC) throw IOException("Bad frame magic")
        val len = input.readInt()
        if (len < 0 || len > BtProtocol.MAX_FRAME) throw IOException("Bad frame length")
        val payload = ByteArray(len)
        input.readFully(payload)
        val crc = input.readInt()
        if (CRC32().apply { update(payload) }.value.toInt() != crc) throw IOException("Frame checksum mismatch")
        return payload
    }
}

/** Raw Bluetooth Classic (RFCOMM) transport: listen/connect, framed reads and writes, discovery. */
@SuppressLint("MissingPermission")
class BluetoothLink(private val context: Context, private val scope: CoroutineScope) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val state = MutableStateFlow(LinkState.IDLE)
    val peerName = MutableStateFlow<String?>(null)
    val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 16)
    val discovered = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discovering = MutableStateFlow(false)
    /** Human-readable reason for the last failure, shown in the UI so problems are visible. */
    val error = MutableStateFlow<String?>(null)
    /** Time any byte last arrived. Used for liveness so a long transfer is never mistaken for a dead link. */
    @Volatile var lastActivity = 0L
        private set

    private var socket: BluetoothSocket? = null
    @Volatile private var servers: List<BluetoothServerSocket> = emptyList()
    @Volatile private var pending: BluetoothSocket? = null
    private var out: DataOutputStream? = null
    private val writeLock = Mutex()
    private var worker: Job? = null
    @Volatile private var wanted = false

    val supported get() = adapter != null
    val enabled get() = adapter?.isEnabled == true

    fun hasPermissions(): Boolean {
        fun ok(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ok(android.Manifest.permission.BLUETOOTH_CONNECT) && ok(android.Manifest.permission.BLUETOOTH_SCAN)
        else ok(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun bonded(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList().orEmpty()
    } catch (e: SecurityException) { emptyList() }

    fun deviceByAddress(address: String): BluetoothDevice? = try { adapter?.getRemoteDevice(address) } catch (e: Exception) { null }

    // ---------- Discovery ----------
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null && discovered.value.none { it.address == d.address }) discovered.value = discovered.value + d
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> discovering.value = false
            }
        }
    }
    private var receiverRegistered = false

    fun startDiscovery(): Boolean {
        val a = adapter ?: return false
        return try {
            if (!receiverRegistered) {
                val f = IntentFilter(BluetoothDevice.ACTION_FOUND).apply { addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }
                ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
                receiverRegistered = true
            }
            discovered.value = emptyList()
            if (a.isDiscovering) a.cancelDiscovery()
            discovering.value = a.startDiscovery()
            if (!discovering.value) error.value = "Could not start scanning. On Android 11 or older, turn Location on."
            discovering.value
        } catch (e: SecurityException) { error.value = "Bluetooth permission missing"; false }
    }

    fun stopDiscovery() {
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
        discovering.value = false
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    // ---------- Server (controller) ----------
    /** Opens an insecure and a secure RFCOMM server at once and returns whichever phone connects first. */
    private fun acceptEither(): BluetoothSocket {
        val a = adapter ?: throw IOException("Bluetooth unavailable")
        val opened = mutableListOf<BluetoothServerSocket>()
        var openError: IOException? = null
        try { opened += a.listenUsingInsecureRfcommWithServiceRecord(BtProtocol.SERVICE_NAME, BtProtocol.UUID_INSECURE) } catch (e: IOException) { openError = e }
        try { opened += a.listenUsingRfcommWithServiceRecord(BtProtocol.SERVICE_NAME, BtProtocol.UUID_SECURE) } catch (e: IOException) { openError = e }
        if (opened.isEmpty()) throw openError ?: IOException("Could not open Bluetooth server")
        servers = opened
        state.value = LinkState.LISTENING
        error.value = null
        val results = java.util.concurrent.LinkedBlockingQueue<Any>()
        opened.forEach { srv ->
            Thread {
                try { results.put(srv.accept()) } catch (e: IOException) { results.put(e) }
            }.apply { isDaemon = true; start() }
        }
        try {
            var failures = 0
            var lastFailure: IOException? = null
            while (failures < opened.size) {
                when (val r = results.take()) {
                    is BluetoothSocket -> return r
                    is IOException -> { failures++; lastFailure = r }
                }
            }
            throw lastFailure ?: IOException("accept failed")
        } finally {
            opened.forEach { runCatching { it.close() } }
            servers = emptyList()
        }
    }

    fun listen() {
        wanted = true
        worker?.cancel()
        worker = scope.launch(Dispatchers.IO) {
            while (isActive && wanted) {
                var s: BluetoothSocket? = null
                try {
                    s = acceptEither()
                    handleConnection(s, isServer = true)
                } catch (e: SecurityException) {
                    error.value = "Bluetooth permission missing"; state.value = LinkState.IDLE; return@launch
                } catch (e: IOException) {
                    runCatching { s?.close() }
                    if (!wanted) return@launch
                    error.value = "Waiting for the other phone failed: ${e.message ?: "unknown error"}. Retrying…"
                    delay(1000)
                }
            }
        }
    }

    // ---------- Client (terminal) ----------
    /** Waits for Android's pairing dialog (shown on both phones) to finish. */
    private suspend fun ensureBonded(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return true
        error.value = "Pairing needed: accept the pairing request on both phones."
        if (device.bondState != BluetoothDevice.BOND_BONDING) runCatching { device.createBond() }
        repeat(60) {
            if (!wanted) return false
            if (device.bondState == BluetoothDevice.BOND_BONDED) { error.value = null; return true }
            delay(1000)
        }
        return device.bondState == BluetoothDevice.BOND_BONDED
    }

    private suspend fun openClient(device: BluetoothDevice): BluetoothSocket {
        var last: IOException? = null
        runCatching { adapter?.cancelDiscovery() }
        delay(500)
        for (round in 1..5) {
            if (!wanted) break
            // After two failed rounds, pair properly through Android; many phones refuse unpaired RFCOMM.
            if (round == 3) ensureBonded(device)
            for (secure in listOf(false, true)) {
                val s = try {
                    if (secure) device.createRfcommSocketToServiceRecord(BtProtocol.UUID_SECURE)
                    else device.createInsecureRfcommSocketToServiceRecord(BtProtocol.UUID_INSECURE)
                } catch (e: IOException) { last = e; continue }
                pending = s
                try {
                    s.connect()
                    pending = null
                    return s
                } catch (e: IOException) {
                    last = e
                    runCatching { s.close() }
                    delay(400)
                }
            }
            delay(1000L * round)
        }
        pending = null
        throw last ?: IOException("connect failed")
    }

    fun connect(device: BluetoothDevice, autoReconnect: Boolean) {
        wanted = true
        worker?.cancel()
        worker = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive && wanted) {
                var s: BluetoothSocket? = null
                state.value = LinkState.CONNECTING
                try {
                    adapter?.cancelDiscovery()
                    s = openClient(device)
                    attempt = 0
                    error.value = null
                    handleConnection(s, isServer = false)
                } catch (e: SecurityException) {
                    error.value = "Bluetooth permission missing"; state.value = LinkState.IDLE; return@launch
                } catch (e: IOException) {
                    runCatching { s?.close() }
                    if (wanted) error.value = "Could not connect (${e.message ?: "unknown"}). On the other phone, choose Controller first and wait for \"Waiting for the voting machine\". Both phones need Bluetooth permission."
                }
                if (!wanted) return@launch
                if (!autoReconnect) { state.value = LinkState.IDLE; return@launch }
                attempt++
                state.value = LinkState.CONNECTING
                delay(minOf(1500L * attempt, 6000L))
            }
        }
    }

    /** Reads bytes and records activity so heartbeat checks see progress even during a long frame. */
    private inner class ActivityInput(private val inner: java.io.InputStream) : java.io.InputStream() {
        override fun read(): Int = inner.read().also { if (it >= 0) lastActivity = System.currentTimeMillis() }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            inner.read(b, off, len).also { if (it > 0) lastActivity = System.currentTimeMillis() }
        override fun available(): Int = inner.available()
        override fun close() = inner.close()
    }

    private suspend fun handleConnection(s: BluetoothSocket, isServer: Boolean) = coroutineScope {
        socket = s
        val input = DataInputStream(ActivityInput(s.inputStream).buffered())
        val o = DataOutputStream(s.outputStream.buffered())
        writeLock.withLock { out = o }
        lastActivity = System.currentTimeMillis()
        peerName.value = try { s.remoteDevice.name ?: s.remoteDevice.address } catch (e: SecurityException) { "Device" }
        state.value = LinkState.CONNECTED
        error.value = null
        events.emit(LinkEvent.Connected(peerName.value ?: "Device", s.remoteDevice.address))
        try {
            while (isActive) {
                val payload = withContext(Dispatchers.IO) { Framing.read(input) }
                incoming.emit(payload)
            }
        } catch (e: IOException) {
            // connection lost or corrupted frame
        } finally {
            withContext(NonCancellable) {
                writeLock.withLock { out = null }
                runCatching { s.close() }
                socket = null
                peerName.value = null
                if (wanted) state.value = if (isServer) LinkState.LISTENING else LinkState.CONNECTING
                events.emit(LinkEvent.Disconnected)
            }
        }
    }

    suspend fun send(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val o = out ?: return@withLock false
            try { Framing.write(o, bytes); true } catch (e: IOException) {
                runCatching { socket?.close() }   // reader loop will observe the failure and clean up
                false
            }
        }
    }

    /** Drop the current connection but keep trying (used when the peer stops answering heartbeats). */
    fun dropConnection() { runCatching { socket?.close() } }

    fun close() {
        wanted = false
        worker?.cancel()
        servers.forEach { runCatching { it.close() } }
        runCatching { pending?.close() }
        runCatching { socket?.close() }
        servers = emptyList()
        stopDiscovery()
        state.value = LinkState.IDLE
        peerName.value = null
        error.value = null
    }
}
