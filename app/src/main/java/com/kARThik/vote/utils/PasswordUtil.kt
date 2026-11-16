package com.kARThik.vote.utils

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 password hashing. Plain passwords are never stored. */
object PasswordUtil {
    private const val ITERATIONS = 120_000

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(password: String, saltHex: String): String {
        val spec = PBEKeySpec(password.toCharArray(), saltHex.fromHex(), ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
    }

    fun verify(password: String, saltHex: String, expectedHex: String): Boolean =
        MessageDigest.isEqual(hash(password, saltHex).toByteArray(), expectedHex.toByteArray())
}
