package com.kARThik.vote.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.kARThik.vote.data.*
import com.kARThik.vote.settings.SettingsStore
import com.kARThik.vote.voting.Buzzer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID

enum class BtRole { NONE, CONTROLLER, TERMINAL }

enum class TerminalPhase {
    CONNECTING,        // not connected / syncing
    WAITING_APPROVAL,  // idle, waiting for controller to approve next voter
    BALLOT,            // approved: candidate list shown
    AWAITING_ACK,      // vote sent (or queued), not yet acknowledged
    SUCCESS,           // acknowledged and recorded
    REJECTED           // controller refused the vote
}

data class BtUiState(
    val role: BtRole = BtRole.NONE,
    val electionId: String = "",
    val electionName: String = "",
    val link: LinkState = LinkState.IDLE,
    val peerName: String? = null,
    val synced: Boolean = false,
    val interrupted: Boolean = false,
    val paused: Boolean = false,
    // controller side
    val terminalReady: Boolean = false,
    val activeSessionId: String? = null,
    val votesRecorded: Int = 0,
    // terminal side
    val phase: TerminalPhase = TerminalPhase.CONNECTING,
    val sessionId: String? = null,
    val waitingTx: String? = null,
    val unconfirmedVotes: Int = 0,
    val rejectReason: String = "",
    val error: String? = null,
    /** Landscape layout requested by the controller; the terminal follows it. */
    val landscape: Boolean = false
)

/**
 * Implements the two-phone protocol on top of [BluetoothLink].
 *
 * Safety properties:
 *  - A vote is stored locally on the terminal as PENDING before it is sent; it only becomes VALID when the
 *    controller acknowledges the transaction id, so nothing is lost if the link drops mid-send.
 *  - Votes are keyed by transaction id; re-sent or duplicated messages are acknowledged but never recorded twice.
 *  - All PENDING votes are re-sent after every reconnect.
 *  - Bluetooth pairing is not voter authentication; this mode is meant for supervised, non-binding votes.
 */
class BtVotingCoordinator(
    private val context: Context,
    private val repo: MatamRepository,
    private val exporter: ExportManager,
    private val settings: SettingsStore,
    private val scope: CoroutineScope
) {
    val link = BluetoothLink(context, scope)

    private val _ui = MutableStateFlow(BtUiState())
    val ui: StateFlow<BtUiState> = _ui

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val feedback: SharedFlow<String> = _feedback

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private var job: Job? = null
    private var lastRx = 0L
    private val seen = LinkedHashSet<String>()
    private var lastDevice: BluetoothDevice? = null
    private var deviceId = ""

    private fun note(msg: String, toast: Boolean = true) {
        _log.update { (listOf("${com.kARThik.vote.utils.fmtTime(System.currentTimeMillis())}  $msg") + it).take(40) }
        if (toast) _feedback.tryEmit(msg)
    }

    private fun msg(type: String, build: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject().put("t", type).put("mid", UUID.randomUUID().toString()).put("ts", System.currentTimeMillis()).apply(build)

    private suspend fun send(m: JSONObject): Boolean = link.send(m.toString().toByteArray(Charsets.UTF_8))

    // ---------- Lifecycle ----------
    /** Controller first listens for the voting machine; an election is chosen after the phones connect. */
    fun startController() {
        stop()
        job = scope.launch {
            deviceId = settings.ensureDeviceId()
            _ui.value = BtUiState(role = BtRole.CONTROLLER)
            runLoops()      // subscribe first so no connection event can be missed
            link.listen()
            note("Waiting for the voting machine to connect…", false)
        }
    }

    /** Controller picked an election (password already verified by the UI). Sends it to the voting machine. */
    fun selectElection(id: String) {
        scope.launch {
            val st = _ui.value
            if (st.role != BtRole.CONTROLLER || st.activeSessionId != null) return@launch
            val e = repo.getElection(id) ?: run { note("Election not found"); return@launch }
            if (e.status == "COMPLETED") { note("This election is already completed"); return@launch }
            if (e.status == "PAUSED") repo.setStatus(e.id, ElectionStatus.ONGOING)
            _ui.update { it.copy(electionId = e.id, electionName = e.name, paused = false, synced = false,
                terminalReady = false, activeSessionId = null, votesRecorded = 0) }
            _ui.update { it.copy(votesRecorded = validCount(e.id)) }
            note("Election selected: ${e.name}", false)
            if (link.state.value == LinkState.CONNECTED) controllerOnHello()
        }
    }

    /** Controller goes back to the election list; the voting machine returns to its waiting screen. */
    fun clearElection() {
        scope.launch {
            val st = _ui.value
            if (st.role != BtRole.CONTROLLER || st.activeSessionId != null) return@launch
            if (st.electionId.isNotBlank()) {
                val e = repo.getElection(st.electionId)
                if (e?.status == "ONGOING") repo.setStatus(st.electionId, ElectionStatus.PAUSED)
            }
            _ui.update { it.copy(electionId = "", electionName = "", synced = false, terminalReady = false, paused = false, votesRecorded = 0) }
            send(msg("NO_ELECTION"))
        }
    }

    /** Controller toggles landscape on both phones. */
    fun setLandscape(on: Boolean) {
        _ui.update { it.copy(landscape = on) }
        scope.launch { send(msg("ORIENT") { put("landscape", on) }) }
    }

    fun startTerminal(device: BluetoothDevice) {
        stop()
        lastDevice = device
        job = scope.launch {
            deviceId = settings.ensureDeviceId()
            _ui.value = BtUiState(role = BtRole.TERMINAL, phase = TerminalPhase.CONNECTING)
            val auto = settings.flow.first().btAutoReconnect
            runLoops()      // subscribe first so no connection event can be missed
            link.connect(device, auto)
            note("Connecting to controller…", false)
        }
    }

    fun reconnect() {
        val d = lastDevice ?: return
        if (_ui.value.role != BtRole.TERMINAL) return
        scope.launch { link.connect(d, settings.flow.first().btAutoReconnect) }
    }

    fun stop() {
        job?.cancel(); job = null
        link.close()
        _ui.value = BtUiState()
        seen.clear()
    }

    private suspend fun validCount(id: String) = (repo.getResults(id)?.totalVotes) ?: 0

    private fun CoroutineScope.runLoops() {
        launch { link.state.collect { s -> _ui.update { it.copy(link = s) } } }
        launch { link.peerName.collect { n -> _ui.update { it.copy(peerName = n) } } }
        launch { link.error.collect { e -> _ui.update { it.copy(error = e) } } }
        launch {
            link.events.collect { ev ->
                when (ev) {
                    is LinkEvent.Connected -> onConnected(ev)
                    LinkEvent.Disconnected -> onDisconnected()
                }
            }
        }
        launch {
            link.incoming.collect { bytes ->
                lastRx = System.currentTimeMillis()
                val m = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (e: Exception) { return@collect }
                handle(m)
            }
        }
        launch { heartbeat() }
    }

    private suspend fun heartbeat() {
        while (currentCoroutineContext().isActive) {
            delay(4000)
            if (link.state.value == LinkState.CONNECTED) {
                send(msg("PING"))
                // Any received byte counts as life, so a long photo sync is never mistaken for a dead link.
                if (System.currentTimeMillis() - maxOf(lastRx, link.lastActivity) > 45000) {
                    note("Peer stopped responding", false)
                    link.dropConnection()
                }
            }
        }
    }

    // ---------- Connection events ----------
    private suspend fun onConnected(ev: LinkEvent.Connected) {
        lastRx = System.currentTimeMillis()
        val wasInterrupted = _ui.value.interrupted
        _ui.update { it.copy(interrupted = false, synced = false) }
        repo.savePairing(DevicePairingEntity(ev.address, ev.name, _ui.value.role.name, System.currentTimeMillis(), _ui.value.electionId))
        note(if (wasInterrupted) "Reconnected to ${ev.name}" else "Connected to ${ev.name}")
        if (_ui.value.role == BtRole.TERMINAL) {
            _ui.update { it.copy(phase = if (it.phase == TerminalPhase.AWAITING_ACK) it.phase else TerminalPhase.CONNECTING) }
            send(msg("HELLO") { put("device", deviceId); put("role", "TERMINAL") })
        }
    }

    private suspend fun onDisconnected() {
        if (_ui.value.role == BtRole.NONE) return
        _ui.update { it.copy(interrupted = true, synced = false, terminalReady = false) }
        note("Connection interrupted. Any unconfirmed vote is kept safely and will be re-sent on reconnect.")
    }

    // ---------- Message handling ----------
    private suspend fun handle(m: JSONObject) {
        val type = m.optString("t")
        val mid = m.optString("mid")
        // Drop exact duplicates of control messages (votes/acks are idempotent by transaction id instead).
        if (type != "VOTE" && type != "ACK" && type != "PING" && type != "PONG") {
            if (!seen.add(mid)) return
            if (seen.size > 200) seen.remove(seen.first())
        }
        when (type) {
            "PING" -> send(msg("PONG"))
            "PONG" -> {}
            "HELLO" -> if (_ui.value.role == BtRole.CONTROLLER) controllerOnHello()
            "SYNC" -> if (_ui.value.role == BtRole.TERMINAL) terminalOnSync(m)
            "SYNC_OK" -> if (_ui.value.role == BtRole.CONTROLLER) controllerOnSyncOk(m)
            "SYNC_FAIL" -> { note("Terminal rejected sync: ${m.optString("reason")}"); _ui.update { it.copy(synced = false) } }
            "READY" -> if (_ui.value.role == BtRole.CONTROLLER) controllerOnReady()
            "BUSY" -> if (_ui.value.role == BtRole.CONTROLLER) _ui.update { it.copy(terminalReady = false) }
            "APPROVE" -> if (_ui.value.role == BtRole.TERMINAL) terminalOnApprove(m)
            "CANCEL_SESSION" -> if (_ui.value.role == BtRole.TERMINAL) terminalOnCancel(m)
            "STATE" -> if (_ui.value.role == BtRole.TERMINAL) terminalOnState(m)
            "VOTE" -> if (_ui.value.role == BtRole.CONTROLLER) controllerOnVote(m)
            "ACK" -> if (_ui.value.role == BtRole.TERMINAL) terminalOnAck(m)
            "ORIENT" -> if (_ui.value.role == BtRole.TERMINAL) _ui.update { it.copy(landscape = m.optBoolean("landscape")) }
            "NO_ELECTION" -> if (_ui.value.role == BtRole.TERMINAL && _ui.value.phase != TerminalPhase.AWAITING_ACK)
                _ui.update { it.copy(electionId = "", electionName = "", synced = false, phase = TerminalPhase.CONNECTING, sessionId = null, paused = false) }
        }
    }

    // ---------- Controller ----------
    private suspend fun controllerOnHello() {
        send(msg("ORIENT") { put("landscape", _ui.value.landscape) })
        val id = _ui.value.electionId
        if (id.isBlank()) { note("Voting machine connected. Choose an election.", false); return }
        val json = exporter.electionJson(id, forSync = true) ?: return
        val e = repo.getElection(id) ?: return
        note("Voting machine connected. Synchronising election data…", false)
        val hash = repo.configHash(id)
        send(msg("SYNC") {
            put("electionId", id); put("hash", hash); put("status", e.status); put("config", json)
        })
    }

    private suspend fun controllerOnSyncOk(m: JSONObject) {
        val id = _ui.value.electionId
        val mine = repo.configHash(id)
        if (m.optString("hash") != mine || m.optString("electionId") != id) {
            note("Election data does not match on both phones. Reconnect to retry."); return
        }
        _ui.update { it.copy(synced = true) }
        note("Election data synchronised")
        sendState()
    }

    private suspend fun sendState() {
        val e = repo.getElection(_ui.value.electionId) ?: return
        send(msg("STATE") { put("status", e.status) })
    }

    private suspend fun controllerOnReady() {
        val st = _ui.value
        val active = st.activeSessionId
        if (active != null && !st.paused) {
            // Terminal says it is idle but we had approved a voter: the APPROVE was lost. Re-issue the same session.
            send(msg("APPROVE") { put("sessionId", active); put("electionId", st.electionId); put("resume", true) })
        } else {
            _ui.update { it.copy(terminalReady = true) }
        }
    }

    fun approveNext() {
        scope.launch {
            val st = _ui.value
            if (st.role != BtRole.CONTROLLER || st.electionId.isBlank() || !st.synced || !st.terminalReady || st.paused || st.activeSessionId != null) return@launch
            val e = repo.getElection(st.electionId) ?: return@launch
            if (e.status == "READY") repo.setStatus(e.id, ElectionStatus.ONGOING)
            else if (e.status != "ONGOING") { note("Election is ${e.status.lowercase()}; cannot approve a session"); return@launch }
            val sid = repo.openSession(st.electionId, deviceId)
            _ui.update { it.copy(activeSessionId = sid, terminalReady = false) }
            sendState()
            if (send(msg("APPROVE") { put("sessionId", sid); put("electionId", st.electionId) }))
                note("Next voting session authorised")
        }
    }

    fun pause() = scope.launch {
        val id = _ui.value.electionId
        if (_ui.value.role != BtRole.CONTROLLER || id.isBlank()) return@launch
        val err = repo.setStatus(id, ElectionStatus.PAUSED)
        if (err == null) { _ui.update { it.copy(paused = true) }; sendState(); note("Voting paused") }
    }

    fun resume() = scope.launch {
        val id = _ui.value.electionId
        if (_ui.value.role != BtRole.CONTROLLER || id.isBlank()) return@launch
        val err = repo.setStatus(id, ElectionStatus.ONGOING)
        if (err == null) { _ui.update { it.copy(paused = false) }; sendState(); note("Voting resumed") }
    }

    fun cancelActiveSession() = scope.launch {
        val sid = _ui.value.activeSessionId ?: return@launch
        repo.abandonOpenSessions(_ui.value.electionId)
        _ui.update { it.copy(activeSessionId = null) }
        send(msg("CANCEL_SESSION") { put("sessionId", sid) })
        note("Session cancelled")
    }

    private suspend fun controllerOnVote(m: JSONObject) {
        val tx = m.optString("tx")
        val eid = m.optString("electionId")
        val cid = m.optString("candidateId")
        val sid = m.optString("sessionId")
        val ts = m.optLong("ts", System.currentTimeMillis())
        val dev = m.optString("device")
        if (tx.isBlank() || eid != _ui.value.electionId) {
            send(msg("ACK") { put("tx", tx); put("result", "REJECTED"); put("reason", "Election mismatch") }); return
        }
        val outcome = repo.recordVote(eid, cid, sid, tx, dev, ts, VoteStatus.VALID, requireOngoing = false)
        when (outcome) {
            RecordOutcome.RECORDED -> {
                val count = validCount(eid)
                _ui.update { it.copy(votesRecorded = count, activeSessionId = if (it.activeSessionId == sid) null else it.activeSessionId) }
                note("Vote recorded")
                playFeedback()
                send(msg("ACK") { put("tx", tx); put("result", "OK") })
            }
            RecordOutcome.DUPLICATE -> {
                // Same transaction re-sent after a lost ACK: confirm again, record nothing new.
                _ui.update { it.copy(activeSessionId = if (it.activeSessionId == sid) null else it.activeSessionId) }
                send(msg("ACK") { put("tx", tx); put("result", "OK") })
            }
            RecordOutcome.REJECTED -> {
                note("A vote was refused (election closed or invalid candidate)")
                send(msg("ACK") { put("tx", tx); put("result", "REJECTED"); put("reason", "Election closed or invalid candidate") })
            }
        }
    }

    // ---------- Terminal ----------
    private suspend fun terminalOnSync(m: JSONObject) {
        val eid = m.optString("electionId")
        val local = repo.getElection(eid)
        if (local?.status == "COMPLETED") {
            send(msg("SYNC_FAIL") { put("reason", "Election already completed on this device") }); return
        }
        try {
            exporter.importConfig(m.getJSONObject("config"))
            repo.mirrorStatus(eid, m.optString("status", "READY"))
        } catch (e: Exception) {
            val why = e.message ?: e.javaClass.simpleName
            note("Could not import election data: $why")
            send(msg("SYNC_FAIL") { put("reason", "Could not import election data: $why") }); return
        }
        val hash = repo.configHash(eid)
        if (hash != m.optString("hash")) {
            send(msg("SYNC_FAIL") { put("reason", "Verification hash mismatch") }); note("Election verification failed"); return
        }
        val name = repo.getElection(eid)?.name ?: ""
        _ui.update { it.copy(electionId = eid, electionName = name, synced = true, paused = m.optString("status") == "PAUSED") }
        send(msg("SYNC_OK") { put("electionId", eid); put("hash", hash) })
        note("Election data synchronised")
        resendPending()
        announceStatus()
    }

    private suspend fun resendPending() {
        val eid = _ui.value.electionId
        val pend = repo.pendingVotes(eid)
        _ui.update { it.copy(unconfirmedVotes = pend.size) }
        pend.forEach { v -> sendVote(v) }
    }

    private suspend fun sendVote(v: VoteRecordEntity): Boolean = send(msg("VOTE") {
        put("tx", v.transactionId); put("electionId", v.electionId); put("candidateId", v.candidateId)
        put("sessionId", v.sessionId); put("device", v.deviceId); put("ts", v.timestamp)
    })

    /** Tell the controller whether the terminal is idle or mid-ballot (used after connect/reconnect). */
    private suspend fun announceStatus() {
        val st = _ui.value
        when (st.phase) {
            TerminalPhase.BALLOT -> send(msg("BUSY") { put("sessionId", st.sessionId ?: "") })
            TerminalPhase.AWAITING_ACK -> send(msg("BUSY"))
            else -> {
                _ui.update { it.copy(phase = TerminalPhase.WAITING_APPROVAL) }
                send(msg("READY"))
            }
        }
    }

    private suspend fun terminalOnApprove(m: JSONObject) {
        val sid = m.optString("sessionId")
        if (_ui.value.paused && !m.optBoolean("resume")) return
        if (_ui.value.phase == TerminalPhase.AWAITING_ACK) return
        _ui.update { it.copy(phase = TerminalPhase.BALLOT, sessionId = sid, rejectReason = "") }
        note("Controller authorised a new session")
    }

    private fun terminalOnCancel(m: JSONObject) {
        if (_ui.value.sessionId == m.optString("sessionId") && _ui.value.phase == TerminalPhase.BALLOT) {
            _ui.update { it.copy(phase = TerminalPhase.WAITING_APPROVAL, sessionId = null) }
            scope.launch { send(msg("READY")) }
            note("Session cancelled by controller")
        }
    }

    private suspend fun terminalOnState(m: JSONObject) {
        val status = m.optString("status")
        repo.mirrorStatus(_ui.value.electionId, status)
        _ui.update { it.copy(paused = status == "PAUSED") }
        if (status == "COMPLETED") note("Election completed by controller")
    }

    /** Called by the voting UI when the voter confirms a candidate. Stores PENDING first, then sends. */
    fun submitVote(candidateId: String) {
        scope.launch {
            val st = _ui.value
            val sid = st.sessionId ?: return@launch
            if (st.role != BtRole.TERMINAL || st.phase != TerminalPhase.BALLOT) return@launch
            val tx = UUID.randomUUID().toString()
            val outcome = repo.recordVote(st.electionId, candidateId, sid, tx, deviceId, status = VoteStatus.PENDING, requireOngoing = false)
            if (outcome == RecordOutcome.REJECTED) {
                _ui.update { it.copy(phase = TerminalPhase.REJECTED, rejectReason = "Vote could not be stored") }; return@launch
            }
            _ui.update { it.copy(phase = TerminalPhase.AWAITING_ACK, waitingTx = tx, unconfirmedVotes = it.unconfirmedVotes + 1) }
            val v = repo.pendingVotes(st.electionId).firstOrNull { it.transactionId == tx } ?: return@launch
            if (!sendVote(v)) note("Controller not reachable. Your vote is saved and will be confirmed when reconnected.")
        }
    }

    private suspend fun terminalOnAck(m: JSONObject) {
        val tx = m.optString("tx")
        val result = m.optString("result")
        if (result == "OK") {
            repo.markVoteConfirmed(tx)
            val left = repo.pendingVotes(_ui.value.electionId).size
            val mine = _ui.value.waitingTx == tx
            _ui.update {
                it.copy(unconfirmedVotes = left, waitingTx = if (mine) null else it.waitingTx,
                    phase = if (mine) TerminalPhase.SUCCESS else it.phase, sessionId = if (mine) null else it.sessionId)
            }
            if (mine) { note("Vote recorded"); playFeedback() }
        } else {
            // Never silently drop: keep the record but flag it so it does not count as valid.
            repo.recordVoteStatus(tx, VoteStatus.INVALID)
            val left = repo.pendingVotes(_ui.value.electionId).size
            val mine = _ui.value.waitingTx == tx
            _ui.update {
                it.copy(unconfirmedVotes = left, waitingTx = if (mine) null else it.waitingTx,
                    phase = if (mine) TerminalPhase.REJECTED else it.phase, rejectReason = m.optString("reason"))
            }
            note("Controller refused the vote: ${m.optString("reason")}")
        }
    }

    /** Voter-facing screen finished showing the success/reject message. */
    fun terminalNextVoter() {
        scope.launch {
            _ui.update { it.copy(phase = TerminalPhase.WAITING_APPROVAL, sessionId = null, rejectReason = "") }
            if (link.state.value == LinkState.CONNECTED) send(msg("READY"))
        }
    }

    private suspend fun playFeedback() {
        val s = settings.flow.first()
        if (s.buzzerEnabled) Buzzer.play(s.buzzerSound, s.buzzerVolume)
        if (s.vibration) Buzzer.vibrate(context)
    }
}
