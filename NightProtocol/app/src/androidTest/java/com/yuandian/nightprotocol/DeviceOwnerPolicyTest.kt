package com.yuandian.nightprotocol

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuandian.nightprotocol.policy.DpmPolicyController
import com.yuandian.nightprotocol.policy.EssentialPackageResolver
import com.yuandian.nightprotocol.policy.LockTaskFeaturePolicy
import com.yuandian.nightprotocol.policy.SuspensionPlanner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 需要：已通过 `adb shell dpm set-device-owner com.yuandian.nightprotocol/.admin.SleepAdminReceiver`
 * 设为 Device Owner 的模拟器或测试机。未满足时测试被跳过（assume），而不是伪装通过。
 *
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DeviceOwnerPolicyTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val policy = DpmPolicyController(ctx)
    private val resolver = EssentialPackageResolver(ctx)

    @Before fun requireDeviceOwner() = assumeTrue("not device owner", policy.isDeviceOwner())

    @After fun cleanup() {
        val suspended = resolver.launcherPackages().filter { policy.isSuspendedByAdmin(it) == true }
        policy.setPackagesSuspended(suspended, false)
        policy.setLockTaskPackages(emptyList())
        policy.setKioskAsHome(false)
    }

    @Test fun suspendAndRestore_nonEssentialApps() {
        val ess = resolver.resolve(emptySet())
        val targets = SuspensionPlanner.targets(ess.launcherPackages, ess.suspendExempt)
        assumeTrue("no suspendable launcher apps on this image", targets.isNotEmpty())
        val failed = policy.setPackagesSuspended(targets, true).toSet()
        val ok = targets - failed
        assertTrue(ok.isNotEmpty())
        ok.forEach { assertEquals(it, true, policy.isSuspendedByAdmin(it)) }
        // 受保护应用绝不被暂停
        ess.suspendExempt.forEach { assertNotEquals(it, true, policy.isSuspendedByAdmin(it)) }
        assertTrue(policy.setPackagesSuspended(ok, false).isEmpty())
        ok.forEach { assertEquals(it, false, policy.isSuspendedByAdmin(it)) }
    }

    @Test fun defaultDialer_isInLockTaskAllowlist() {
        val ess = resolver.resolve(emptySet())
        val dialer = ess.defaultDialer
        assumeTrue("no default dialer", dialer != null)
        assertTrue(dialer in ess.lockTaskAllowed)
        assertFalse(dialer in SuspensionPlanner.targets(ess.launcherPackages, ess.suspendExempt))
    }

    @Test fun lockTaskPolicy_setAndClear() {
        val ess = resolver.resolve(emptySet())
        policy.setLockTaskPackages(ess.lockTaskAllowed)
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        assertTrue(dpm.isLockTaskPermitted(ctx.packageName))
        assertTrue(policy.setKioskAsHome(true))
        assertEquals(ctx.packageName, policy.currentHomePackage())
        val f = LockTaskFeaturePolicy.features(true)
        policy.setLockTaskFeatures(f)
        assertEquals(f, policy.getLockTaskFeatures())

        policy.setLockTaskPackages(emptyList())
        assertTrue(policy.setKioskAsHome(false))
        assertFalse(dpm.isLockTaskPermitted(ctx.packageName))
        assertNotEquals(ctx.packageName, policy.currentHomePackage())
        assertEquals(ActivityManager.LOCK_TASK_MODE_NONE, ctx.getSystemService(ActivityManager::class.java).lockTaskModeState)
    }
}
