package com.yuandian.nightprotocol

import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
import com.yuandian.nightprotocol.policy.LockTaskFeaturePolicy
import com.yuandian.nightprotocol.policy.SuspensionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPlanningTest {
    @Test fun targets_excludeProtected_sortedDistinct() {
        val t = SuspensionPlanner.targets(listOf("z.game", "com.dialer", "a.video", "z.game", ""), setOf("com.dialer"))
        assertEquals(listOf("a.video", "z.game"), t)
    }

    @Test fun restoreSet_unionsRecordAndActualState() {
        assertEquals(listOf("a", "b", "c"), SuspensionPlanner.restoreSet(setOf("a", "b"), listOf("b", "c")))
    }

    @Test fun features_withoutKioskHome_noHomeNoNotificationsNoOverview() {
        val f = LockTaskFeaturePolicy.features(kioskIsHome = false)
        assertEquals(LOCK_TASK_FEATURE_SYSTEM_INFO or LOCK_TASK_FEATURE_GLOBAL_ACTIONS or LOCK_TASK_FEATURE_KEYGUARD, f)
    }

    @Test fun features_withKioskHome_notificationsAlwaysPairedWithHome_neverOverview() {
        val f = LockTaskFeaturePolicy.features(kioskIsHome = true)
        assertTrue(f and LOCK_TASK_FEATURE_NOTIFICATIONS != 0)
        assertTrue(f and LOCK_TASK_FEATURE_HOME != 0)
        assertEquals(0, f and LOCK_TASK_FEATURE_OVERVIEW)
        assertTrue(f and LOCK_TASK_FEATURE_GLOBAL_ACTIONS != 0)
        assertTrue(f and LOCK_TASK_FEATURE_KEYGUARD != 0)
    }
}
