package com.yuandian.nightprotocol.core

import android.content.Context
import com.yuandian.nightprotocol.data.EventLogger
import com.yuandian.nightprotocol.data.LogDatabase
import com.yuandian.nightprotocol.data.ProtocolStore
import com.yuandian.nightprotocol.policy.DpmPolicyController
import com.yuandian.nightprotocol.policy.EssentialPackageResolver
import com.yuandian.nightprotocol.recovery.RecoveryManager
import com.yuandian.nightprotocol.schedule.TransitionScheduler

/** 极简手动依赖注入。 */
object Graph {
    @Volatile private var ctx: Context? = null

    fun init(context: Context) { if (ctx == null) ctx = context.applicationContext }

    private fun c(context: Context? = null): Context {
        context?.let(::init)
        return checkNotNull(ctx) { "Graph not initialised" }
    }

    val store by lazy { ProtocolStore.get(c()) }
    val logDb by lazy { LogDatabase.get(c()) }
    val logger by lazy { EventLogger(logDb.dao()) }
    val policy by lazy { DpmPolicyController(c()) }
    val resolver by lazy { EssentialPackageResolver(c()) }
    val scheduler by lazy { TransitionScheduler(c()) }
    val notifier by lazy { LockNotifier(c()) }
    val enforcer by lazy { Enforcer(c(), store, policy, resolver, scheduler, notifier, logger) }
    val recovery by lazy { RecoveryManager(store, enforcer, logger) }
}
