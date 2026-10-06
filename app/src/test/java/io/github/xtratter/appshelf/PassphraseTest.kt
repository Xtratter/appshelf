package io.github.xtratter.appshelf

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PassphraseTest {
    @Test fun roundTrip() {
        val box = Passphrase.encrypt("p@ss wörd — пароль", "correct horse")
        assertEquals("p@ss wörd — пароль", Passphrase.decrypt(box, "correct horse"))
    }

    @Test fun passwordIsNotInTheFile() {
        val text = Passphrase.encrypt("hunter2-secret", "phrase!").toString()
        assertFalse("hunter2-secret" in text)
        assertTrue(JSONObject(text).has("salt") && JSONObject(text).has("iv") && JSONObject(text).has("ct"))
    }

    @Test fun samePasswordGivesDifferentCiphertext() {
        val a = Passphrase.encrypt("x", "phrase!").getString("ct")
        val b = Passphrase.encrypt("x", "phrase!").getString("ct")
        assertNotEquals(a, b)   // своя соль и вектор каждый раз
    }

    @Test fun wrongPhraseOrDamagedDataThrowsWrong() {
        val box = Passphrase.encrypt("secret", "right one")
        expectWrong { Passphrase.decrypt(box, "wrong one") }
        expectWrong { Passphrase.decrypt(box, "") }
        val damaged = JSONObject(box.toString()).put("ct", box.getString("ct").reversed())
        expectWrong { Passphrase.decrypt(damaged, "right one") }
        expectWrong { Passphrase.decrypt(JSONObject("""{"kdf":"other"}"""), "right one") }
        expectWrong { Passphrase.decrypt(JSONObject(box.toString()).put("iter", 999_999_999), "right one") }
        expectWrong { Passphrase.decrypt(JSONObject("{}"), "right one") }
    }

    private fun expectWrong(block: () -> Unit) {
        try { block(); fail("expected Passphrase.Wrong") } catch (e: Passphrase.Wrong) { }
    }
}
