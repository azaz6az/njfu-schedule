package com.schedule.njfu.importer

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 南林统一身份认证（金智教育 CAS）密码加密器。
 *
 * 注意：类名保留为 [RsaEncryptor] 是任务计划的约定（后续任务引用此名），
 * 但侦察实测（见 docs/njfu-cas-notes.md）确认本部署**不使用 RSA**，
 * 实际算法为金智 AES 复合加密，与线上 /authserver/custom/js/encrypt.js 的
 * `encryptAES` / `_gas` / `_rds` 逐字节一致：
 *
 * ```
 * passwordEncrypt = Base64( AES-128-CBC-Pkcs7(
 *     key       = UTF8(pwdDefaultEncryptSalt),   // 登录页隐藏域，16 字符
 *     iv        = UTF8(_rds(16)),                // 每次加密随机 16 字符
 *     plaintext = _rds(64) + password            // 64 字符随机前缀 + 密码
 * ))
 * ```
 *
 * 输出为纯 Base64（直接 key 模式，无 "Salted__" 前缀）。
 * 随机前缀的首个 16 字节用于吸收 CBC 首块对 IV 的依赖，故服务器可用任意 IV 解出密码。
 */
object RsaEncryptor {

    /** 与线上 encrypt.js 的 `$_chars` 完全一致（53 字符，无 I/L/O/U/o/l/u/0/1/9） */
    const val RANDOM_CHARSET = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    private val random = SecureRandom()

    /**
     * 加密密码：salt 作为 AES-128 密钥（UTF-8），明文 = 64 字符随机前缀 + 密码，
     * 随机 IV，AES/CBC/PKCS5Padding，输出 Base64 密文。
     *
     * @param password 明文密码
     * @param salt 登录页隐藏域 `pwdDefaultEncryptSalt` 的值
     * @return Base64 密文；salt 为空时退回明文（与线上 JS `encryptAES` 兜底行为一致）
     */
    fun encryptPassword(password: String, salt: String): String {
        if (salt.isEmpty()) return password
        val plaintext = randomString(64) + password
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(randomString(16).toByteArray(Charsets.UTF_8))
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    private fun randomString(length: Int): String = buildString(length) {
        repeat(length) { append(RANDOM_CHARSET[random.nextInt(RANDOM_CHARSET.length)]) }
    }
}
