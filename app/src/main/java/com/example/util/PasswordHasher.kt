package com.example.util

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    fun hashPassword(password: String, salt: String = "ShiPuAiSalt_2026"): String {
        try {
            val spec = PBEKeySpec(
                password.toCharArray(),
                salt.toByteArray(Charsets.UTF_8),
                ITERATIONS,
                KEY_LENGTH
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            return hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to SHA-256 if PBKDF2 algorithm is unavailable
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest((salt + password).toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    fun verifyPassword(password: String, hash: String, salt: String = "ShiPuAiSalt_2026"): Boolean {
        val calculated = hashPassword(password, salt)
        return calculated == hash
    }
}
