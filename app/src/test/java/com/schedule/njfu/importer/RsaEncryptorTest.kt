package com.schedule.njfu.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RsaEncryptorTest {

    private val salt = "c7CVdBScRc7Pagcy"

    private fun decrypt(ciphertextB64: String, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv)
        )
        return cipher.doFinal(Base64.getDecoder().decode(ciphertextB64))
    }

    @Test
    fun `ciphertext decrypts to 64-char random prefix plus password`() {
        val encrypted = RsaEncryptor.encryptPassword("abc123", salt)
        // 金智 AES 方案：CBC 解密只有首块(16字节)依赖 IV，其余块与 IV 无关，可用任意 IV 解出。
        val decrypted = String(decrypt(encrypted, ByteArray(16)))
        assertEquals("rand64 + password", 64 + 6, decrypted.length)
        assertTrue("tail must be the password", decrypted.endsWith("abc123"))
        // 首块是乱码，只有第 16..63 字节可断言字符集
        val prefixTail = decrypted.substring(16, 64)
        assertTrue(
            "random prefix must use the deployed JS charset",
            prefixTail.all { it in RsaEncryptor.RANDOM_CHARSET }
        )
    }

    @Test
    fun `output is plain base64 without Salted__ prefix`() {
        val encrypted = RsaEncryptor.encryptPassword("mySecretPass123", salt) // 15 字符 → 80 字节 → 108 base64 字符
        assertEquals(108, encrypted.length)
        assertFalse(encrypted.startsWith("Salted__"))
        Base64.getDecoder().decode(encrypted) // 必须可解码
    }

    @Test
    fun `each call produces different ciphertext`() {
        val a = RsaEncryptor.encryptPassword("same-password", salt)
        val b = RsaEncryptor.encryptPassword("same-password", salt)
        assertFalse("random prefix/iv must differ between calls", a == b)
    }

    @Test
    fun `blank salt falls back to plaintext password like deployed js`() {
        assertEquals("abc123", RsaEncryptor.encryptPassword("abc123", ""))
    }
}
