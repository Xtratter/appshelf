package io.github.xtratter.appshelf

import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование парольной фразой для файла настроек: ключ из фразы — PBKDF2-HMAC-SHA256 с солью, шифр — AES-GCM.
 * В файле остаются только параметры, соль, вектор и шифртекст. Без Android-зависимостей — проверяется unit-тестами.
 */
object Passphrase {
    const val MIN_LENGTH = 6
    private const val ITERATIONS = 210_000
    private const val MAX_ITERATIONS = 2_000_000   // чужой файл не должен заставить телефон считать часами
    private val AAD = "AppShelf-settings".toByteArray()

    /** Фраза не подошла (или шифртекст испорчен). */
    class Wrong : Exception("wrong passphrase")

    private fun key(phrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(phrase.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    fun encrypt(text: String, phrase: String, random: SecureRandom = SecureRandom()): JSONObject {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(phrase, salt, ITERATIONS), GCMParameterSpec(128, iv))
        c.updateAAD(AAD)
        return JSONObject().apply {
            put("kdf", "pbkdf2-sha256"); put("iter", ITERATIONS)
            put("salt", b64(salt)); put("iv", b64(iv))
            put("ct", b64(c.doFinal(text.toByteArray(Charsets.UTF_8))))
        }
    }

    /** Расшифровать; [Wrong] — неверная фраза или испорченные данные. */
    fun decrypt(box: JSONObject, phrase: String): String {
        try {
            require(box.optString("kdf") == "pbkdf2-sha256") { "unknown key derivation" }
            val iterations = box.getInt("iter").also { require(it in 1..MAX_ITERATIONS) { "bad iterations" } }
            val salt = Base64.getDecoder().decode(box.getString("salt"))
            val iv = Base64.getDecoder().decode(box.getString("iv"))
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(phrase, salt, iterations), GCMParameterSpec(128, iv))
            c.updateAAD(AAD)
            return String(c.doFinal(Base64.getDecoder().decode(box.getString("ct"))), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw Wrong()
        } catch (e: IllegalArgumentException) {
            throw Wrong()
        } catch (e: org.json.JSONException) {
            throw Wrong()
        }
    }
}
