package com.yuandian.nightprotocol

import com.yuandian.nightprotocol.data.CheckRecord
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.recovery.RecoveryCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {
    @Test fun format() {
        val c = RecoveryCode.generate()
        assertTrue(c, Regex("^[2-9A-HJKMNP-Z]{5}(-[2-9A-HJKMNP-Z]{5}){3}$").matches(c))
    }

    @Test fun verify_roundTrip_normalisesInput() {
        val c = RecoveryCode.generate()
        val h = RecoveryCode.hash(c, iterations = 1000)
        assertTrue(RecoveryCode.verify(c, h))
        assertTrue(RecoveryCode.verify(" " + c.lowercase().replace("-", " ") + " ", h))
        assertFalse(RecoveryCode.verify(RecoveryCode.generate(), h))
        assertFalse(RecoveryCode.verify(c, "garbage"))
    }

    @Test fun hash_isSalted() {
        assertNotEquals(RecoveryCode.hash("ABCDE", iterations = 1000), RecoveryCode.hash("ABCDE", iterations = 1000))
    }

    @Test fun lockout_backoff() {
        assertEquals(0L, RecoveryCode.lockoutMillis(4))
        assertEquals(15 * 60_000L, RecoveryCode.lockoutMillis(5))
        assertEquals(30 * 60_000L, RecoveryCode.lockoutMillis(6))
        assertEquals(4 * 3_600_000L, RecoveryCode.lockoutMillis(50))
    }

    @Test fun checkRecord_codec() {
        val r = CheckRecord(CheckStatus.FAILED, 123L, "a|b\nc")
        val d = CheckRecord.decode(r.encode())
        assertEquals(CheckStatus.FAILED, d.status); assertEquals(123L, d.atMillis); assertEquals("a/b c", d.note)
        assertEquals(CheckStatus.UNTESTED, CheckRecord.decode("nonsense").status)
        assertEquals(CheckStatus.UNTESTED, CheckRecord.decode(null).status)
    }
}
