package com.yuandian.nightprotocol

import com.yuandian.nightprotocol.data.ProtocolState
import com.yuandian.nightprotocol.schedule.DecisionEngine
import com.yuandian.nightprotocol.schedule.LockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DecisionEngineTest {
    private val z = ZoneId.of("Asia/Shanghai")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(z).toInstant()
    private val strict = ProtocolState(strictEnabled = true)

    @Test fun notDeviceOwner_neverLocks() {
        val d = DecisionEngine.decide(strict, at("2026-11-02T01:00:00"), z, deviceOwner = false)
        assertFalse(d.lock); assertEquals(LockReason.NOT_DEVICE_OWNER, d.reason); assertNull(d.nextCheck)
    }

    @Test fun strictDisabled_noLockNoSchedule() {
        val d = DecisionEngine.decide(ProtocolState(), at("2026-11-02T01:00:00"), z, true)
        assertFalse(d.lock); assertNull(d.nextCheck)
    }

    @Test fun inWindow_locksUntilMorning_andSchedulesEnd() {
        val d = DecisionEngine.decide(strict, at("2026-11-02T01:00:00"), z, true)
        assertTrue(d.lock); assertEquals(LockReason.SLEEP_WINDOW, d.reason)
        assertEquals(at("2026-11-02T07:30:00"), d.lockUntil); assertEquals(d.lockUntil, d.nextCheck)
    }

    @Test fun daytime_schedulesTonightStart() {
        val d = DecisionEngine.decide(strict, at("2026-11-02T12:00:00"), z, true)
        assertFalse(d.lock); assertEquals(at("2026-11-02T23:30:00"), d.nextCheck)
    }

    @Test fun idempotent_sameInputSameOutput() {
        val now = at("2026-11-02T02:00:00")
        assertEquals(DecisionEngine.decide(strict, now, z, true), DecisionEngine.decide(strict, now, z, true))
    }

    @Test fun overrideActive_unlocksAndSchedulesNextNight() {
        val st = strict.copy(overrideUntil = at("2026-11-02T07:30:00").toEpochMilli())
        val d = DecisionEngine.decide(st, at("2026-11-02T02:00:00"), z, true)
        assertFalse(d.lock); assertEquals(LockReason.OVERRIDDEN, d.reason)
        assertEquals(at("2026-11-02T23:30:00"), d.nextCheck)
    }

    @Test fun overrideExpired_nextNightLocksAgain() {
        val st = strict.copy(overrideUntil = at("2026-11-02T07:30:00").toEpochMilli())
        assertTrue(DecisionEngine.decide(st, at("2026-11-02T23:31:00"), z, true).lock)
    }

    @Test fun testSession_locksEvenWithoutStrict() {
        val st = ProtocolState(testSessionEnd = at("2026-11-02T12:05:00").toEpochMilli())
        val d = DecisionEngine.decide(st, at("2026-11-02T12:00:00"), z, true)
        assertTrue(d.lock); assertTrue(d.isTest); assertEquals(LockReason.TEST_SESSION, d.reason)
        assertFalse(DecisionEngine.decide(st, at("2026-11-02T12:05:00"), z, true).lock)
    }

    @Test fun testSessionOverlappingWindow_usesLaterEnd_andIsNotTest() {
        val st = strict.copy(testSessionEnd = at("2026-11-01T23:35:00").toEpochMilli())
        val d = DecisionEngine.decide(st, at("2026-11-01T23:31:00"), z, true)
        assertTrue(d.lock); assertFalse(d.isTest); assertEquals(at("2026-11-02T07:30:00"), d.lockUntil)
    }

    @Test fun clockMovedForwardPastEnd_unlocks() {
        assertFalse(DecisionEngine.decide(strict, at("2026-11-02T08:00:00"), z, true).lock)
    }
}
