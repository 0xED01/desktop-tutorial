package com.yuandian.nightprotocol.data

/**
 * 严格模式“可正式启用”的门槛：所有检查项必须为 PASSED。
 * 实时类自动检查（Device Owner、精确闹钟、拨号器白名单、恢复码）以实时值覆盖已存储值。
 */
object Readiness {
    fun merge(stored: Map<CheckKey, CheckRecord>, live: Map<CheckKey, CheckRecord>): Map<CheckKey, CheckRecord> =
        CheckKey.entries.associateWith { live[it] ?: stored[it] ?: CheckRecord.UNTESTED }

    fun blockers(merged: Map<CheckKey, CheckRecord>): List<CheckKey> =
        CheckKey.entries.filter { merged[it]?.status != CheckStatus.PASSED }

    fun isReady(merged: Map<CheckKey, CheckRecord>): Boolean = blockers(merged).isEmpty()
}
