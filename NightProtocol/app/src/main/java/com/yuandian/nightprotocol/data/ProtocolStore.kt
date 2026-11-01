package com.yuandian.nightprotocol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * 基于 DataStore 的协议状态存储。文件位于设备加密 (Device Protected) 存储区，
 * 使直接启动 (Direct Boot) 阶段的 LOCKED_BOOT_COMPLETED 也能读取计划并核对策略。
 */
class ProtocolStore private constructor(private val ds: DataStore<Preferences>) {

    val state: Flow<ProtocolState> = ds.data.map(::toState)

    suspend fun snapshot(): ProtocolState = state.first()

    suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        ds.edit { block(it) }
    }

    suspend fun setSchedule(s: SleepSchedule) = edit {
        it[K.START] = s.startMinute; it[K.END] = s.endMinute
    }

    suspend fun setStrictEnabled(v: Boolean) = edit { it[K.STRICT] = v }
    suspend fun setExtraAllowed(v: Set<String>) = edit { it[K.EXTRA] = v }
    suspend fun setRecoveryHash(v: String) = edit {
        it[K.RECOVERY] = v; it[K.FAILED_ATTEMPTS] = 0; it[K.LOCKOUT] = 0L
    }

    suspend fun setOverrideUntil(v: Long) = edit { it[K.OVERRIDE] = v }
    suspend fun setTestSessionEnd(v: Long) = edit { it[K.TEST_END] = v }

    suspend fun addSuspended(pkgs: Collection<String>) = edit {
        it[K.SUSPENDED] = (it[K.SUSPENDED] ?: emptySet()) + pkgs
    }

    suspend fun setSuspended(pkgs: Set<String>) = edit { it[K.SUSPENDED] = pkgs }

    suspend fun setEnforced(locked: Boolean, until: Long, isTest: Boolean, homeOverride: Boolean) = edit {
        it[K.ENFORCED] = locked
        it[K.LOCKED_UNTIL] = until
        if (locked) it[K.LAST_WAS_TEST] = isTest
        it[K.HOME_OVERRIDE] = homeOverride
    }

    suspend fun recordCheck(key: CheckKey, status: CheckStatus, note: String, now: Long = System.currentTimeMillis()) = edit {
        it[stringPreferencesKey(CHECK_PREFIX + key.name)] = CheckRecord(status, now, note).encode()
    }

    suspend fun resetChecks() = edit { prefs ->
        CheckKey.entries.forEach { prefs.remove(stringPreferencesKey(CHECK_PREFIX + it.name)) }
    }

    suspend fun setRecoveryAttempts(failed: Int, lockoutUntil: Long) = edit {
        it[K.FAILED_ATTEMPTS] = failed; it[K.LOCKOUT] = lockoutUntil
    }

    private fun toState(p: Preferences): ProtocolState {
        val schedule = runCatching {
            SleepSchedule(p[K.START] ?: SleepSchedule.DEFAULT_START, p[K.END] ?: SleepSchedule.DEFAULT_END)
        }.getOrDefault(SleepSchedule())
        val checks = CheckKey.entries.associateWith { CheckRecord.decode(p[stringPreferencesKey(CHECK_PREFIX + it.name)]) }
        return ProtocolState(
            schedule = schedule,
            strictEnabled = p[K.STRICT] ?: false,
            extraAllowed = p[K.EXTRA] ?: emptySet(),
            recoveryHash = p[K.RECOVERY],
            overrideUntil = p[K.OVERRIDE] ?: 0L,
            testSessionEnd = p[K.TEST_END] ?: 0L,
            suspendedByUs = p[K.SUSPENDED] ?: emptySet(),
            enforcedLock = p[K.ENFORCED] ?: false,
            lockedUntil = p[K.LOCKED_UNTIL] ?: 0L,
            lastLockWasTest = p[K.LAST_WAS_TEST] ?: false,
            homeOverrideActive = p[K.HOME_OVERRIDE] ?: false,
            checks = checks,
            failedRecoveryAttempts = p[K.FAILED_ATTEMPTS] ?: 0,
            recoveryLockoutUntil = p[K.LOCKOUT] ?: 0L,
        )
    }

    private object K {
        val START = intPreferencesKey("schedule_start")
        val END = intPreferencesKey("schedule_end")
        val STRICT = booleanPreferencesKey("strict_enabled")
        val EXTRA = stringSetPreferencesKey("extra_allowed")
        val RECOVERY = stringPreferencesKey("recovery_hash")
        val OVERRIDE = longPreferencesKey("override_until")
        val TEST_END = longPreferencesKey("test_session_end")
        val SUSPENDED = stringSetPreferencesKey("suspended_by_us")
        val ENFORCED = booleanPreferencesKey("enforced_lock")
        val LOCKED_UNTIL = longPreferencesKey("locked_until")
        val LAST_WAS_TEST = booleanPreferencesKey("last_lock_was_test")
        val HOME_OVERRIDE = booleanPreferencesKey("home_override_active")
        val FAILED_ATTEMPTS = intPreferencesKey("recovery_failed_attempts")
        val LOCKOUT = longPreferencesKey("recovery_lockout_until")
    }

    companion object {
        private const val CHECK_PREFIX = "check_"
        @Volatile private var instance: ProtocolStore? = null

        fun get(context: Context): ProtocolStore = instance ?: synchronized(this) {
            instance ?: run {
                val de = context.applicationContext.createDeviceProtectedStorageContext()
                ProtocolStore(
                    PreferenceDataStoreFactory.create(
                        produceFile = { File(de.filesDir, "datastore/night_protocol.preferences_pb") }
                    )
                ).also { instance = it }
            }
        }
    }
}
