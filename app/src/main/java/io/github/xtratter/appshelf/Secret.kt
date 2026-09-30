package io.github.xtratter.appshelf

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование пароля WebDAV ключом из Android Keystore: ключ не покидает телефон,
 * поэтому пароль из резервной копии настроек на другом устройстве не расшифруется — его просто введут заново.
 */
object Secret {
    private const val ALIAS = "appshelf_webdav"
    private const val CIPHER = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return g.generateKey()
    }

    fun encrypt(text: String): String {
        val c = Cipher.getInstance(CIPHER)
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.getEncoder().encodeToString(c.iv + c.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    /** Пустая строка — если пароля нет или его не расшифровать (например, настройки с другого телефона). */
    fun decrypt(data: String): String = if (data.isEmpty()) "" else try {
        val raw = Base64.getDecoder().decode(data)
        val c = Cipher.getInstance(CIPHER)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }
}
