package com.yuandian.nightprotocol.recovery

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 紧急恢复凭证：20 位随机码（约 100 bit 熵），只显示一次，由用户离线保管。
 * 只保存 PBKDF2-HMAC-SHA256 哈希。
 */
object RecoveryCode {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ" // 去除易混淆字符 0 O 1 I L
    private const val ITERATIONS = 120_000
    const val MAX_FREE_ATTEMPTS = 5

    fun generate(random: SecureRandom = SecureRandom()): String =
        (1..20).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(5).joinToString("-") { it.joinToString("") }

    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun hash(code: String, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val h = pbkdf2(normalize(code), salt, iterations)
        val b64 = Base64.getEncoder()
        return "pbkdf2_sha256\$$iterations\$${b64.encodeToString(salt)}\$${b64.encodeToString(h)}"
    }

    fun verify(code: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(pbkdf2(normalize(code), salt, iterations), expected)
    }

    /** 连续失败超过阈值后指数退避：15 分钟起，最长 4 小时。 */
    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts < MAX_FREE_ATTEMPTS) return 0L
        val exp = (failedAttempts - MAX_FREE_ATTEMPTS).coerceAtMost(4)
        return (15L * 60_000L shl exp).coerceAtMost(4L * 3_600_000L)
    }

    private fun pbkdf2(code: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
