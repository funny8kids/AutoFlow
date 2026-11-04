package com.autoflow.app.core

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 全量备份文件格式。 */
@Serializable
data class BackupFile(
    val format: String = "autoflow-backup",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val macros: List<Macro> = emptyList(),
    val globalVars: List<Variable> = emptyList(),
)

/**
 * 备份编解码：明文 JSON，或 AES-GCM 加密（带认证标签）。
 * 加密格式：Base64( "AFB1" + salt(16) + iv(12) + ciphertext )
 */
object Backup {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun export(macros: List<Macro>, globals: List<Variable>, password: String): String {
        val payload = json.encodeToString(
            BackupFile.serializer(),
            BackupFile(macros = macros, globalVars = globals),
        )
        return if (password.isBlank()) payload else BackupCrypto.encrypt(payload, password)
    }

    fun import(raw: String, password: String): BackupFile {
        val trimmed = raw.trim()
        val payload = if (trimmed.startsWith("{")) trimmed else BackupCrypto.decrypt(trimmed, password)
        val file = json.decodeFromString(BackupFile.serializer(), payload)
        require(file.format == "autoflow-backup") { "不是 AutoFlow 备份文件" }
        return file
    }

    fun isEncrypted(raw: String): Boolean = !raw.trim().startsWith("{")
}

object BackupCrypto {

    private const val MAGIC = "AFB1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(deriveKey(password, salt), "AES"),
            GCMParameterSpec(128, iv),
        )
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(salt)
        out.write(iv)
        out.write(cipherText)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decrypt(data: String, password: String): String {
        val all = Base64.decode(data, Base64.NO_WRAP)
        require(all.size > 32) { "加密数据不完整" }
        val magic = String(all, 0, 4, Charsets.US_ASCII)
        require(magic == MAGIC) { "不是 AutoFlow 加密备份" }
        val salt = all.copyOfRange(4, 20)
        val iv = all.copyOfRange(20, 32)
        val cipherText = all.copyOfRange(32, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(deriveKey(password, salt), "AES"),
            GCMParameterSpec(128, iv),
        )
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }
}

fun backupToJson(file: BackupFile): String =
    Json { prettyPrint = true; encodeDefaults = true }.encodeToString(BackupFile.serializer(), file)

fun backupFromJson(text: String): BackupFile =
    Json { ignoreUnknownKeys = true }.decodeFromString(BackupFile.serializer(), text)

fun macroListToJson(macros: List<Macro>): String =
    Json { prettyPrint = true; encodeDefaults = true }
        .encodeToString(ListSerializer(Macro.serializer()), macros)
