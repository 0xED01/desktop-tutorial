package com.yuandian.nightprotocol

import com.yuandian.nightprotocol.data.CheckKey
import com.yuandian.nightprotocol.data.CheckRecord
import com.yuandian.nightprotocol.data.CheckStatus
import com.yuandian.nightprotocol.data.Readiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessTest {
    private val pass = CheckRecord(CheckStatus.PASSED, 1, "")
    private val fail = CheckRecord(CheckStatus.FAILED, 1, "")

    @Test fun emptyIsNotReady_allUntestedAreBlockers() {
        val m = Readiness.merge(emptyMap(), emptyMap())
        assertFalse(Readiness.isReady(m)); assertEquals(CheckKey.entries.size, Readiness.blockers(m).size)
    }

    @Test fun allPassedIsReady() {
        assertTrue(Readiness.isReady(Readiness.merge(CheckKey.entries.associateWith { pass }, emptyMap())))
    }

    @Test fun anySingleFailureBlocks() {
        CheckKey.entries.forEach { k ->
            val m = Readiness.merge(CheckKey.entries.associateWith { if (it == k) fail else pass }, emptyMap())
            assertEquals(listOf(k), Readiness.blockers(m))
        }
    }

    @Test fun liveValueOverridesStaleStoredPass() {
        val stored = CheckKey.entries.associateWith { pass }
        val m = Readiness.merge(stored, mapOf(CheckKey.DEVICE_OWNER to fail))
        assertEquals(listOf(CheckKey.DEVICE_OWNER), Readiness.blockers(m))
    }
}
