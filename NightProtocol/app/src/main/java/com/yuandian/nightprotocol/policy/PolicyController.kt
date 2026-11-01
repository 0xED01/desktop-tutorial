package com.yuandian.nightprotocol.policy

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.yuandian.nightprotocol.admin.SleepAdminReceiver

/** 对 DevicePolicyManager 的薄封装；接口化以便单元测试中替换。 */
interface PolicyController {
    fun isDeviceOwner(): Boolean
    /** @return 系统拒绝处理的包（真实返回值，不做任何美化）。 */
    fun setPackagesSuspended(packages: List<String>, suspended: Boolean): List<String>
    /** null 表示包不存在或查询失败。 */
    fun isSuspendedByAdmin(pkg: String): Boolean?
    fun isInstalled(pkg: String): Boolean
    fun setLockTaskPackages(packages: Collection<String>)
    fun getLockTaskPackages(): List<String>
    fun setLockTaskFeatures(flags: Int)
    fun getLockTaskFeatures(): Int
    /** 将 Kiosk 设为/取消持久化默认 HOME。返回是否成功。 */
    fun setKioskAsHome(enable: Boolean): Boolean
    fun currentHomePackage(): String?
    fun grantOwnRuntimePermissions(): List<String>
}

class DpmPolicyController(context: Context) : PolicyController {
    private val ctx = context.applicationContext
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val pm = ctx.packageManager
    private val admin = SleepAdminReceiver.component(ctx)
    private val homeAlias = ComponentName(ctx.packageName, KIOSK_HOME_ALIAS)

    override fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(ctx.packageName)

    override fun setPackagesSuspended(packages: List<String>, suspended: Boolean): List<String> {
        if (packages.isEmpty()) return emptyList()
        // 分批，避免单次 Binder 事务过大。
        return packages.chunked(64).flatMap { dpm.setPackagesSuspended(admin, it.toTypedArray(), suspended).toList() }
    }

    override fun isSuspendedByAdmin(pkg: String): Boolean? = try {
        dpm.isPackageSuspended(admin, pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    override fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun setLockTaskPackages(packages: Collection<String>) =
        dpm.setLockTaskPackages(admin, packages.distinct().sorted().toTypedArray())

    override fun getLockTaskPackages(): List<String> = dpm.getLockTaskPackages(admin).toList()

    override fun setLockTaskFeatures(flags: Int) = dpm.setLockTaskFeatures(admin, flags)

    override fun getLockTaskFeatures(): Int = dpm.getLockTaskFeatures(admin)

    override fun setKioskAsHome(enable: Boolean): Boolean = runCatching {
        if (enable) {
            pm.setComponentEnabledSetting(homeAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, filter, homeAlias)
        } else {
            // 只清除本包设置的持久化首选项；用户原有的默认桌面选择随之重新生效。
            dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
            pm.setComponentEnabledSetting(homeAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        true
    }.getOrElse { false }

    override fun currentHomePackage(): String? =
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    override fun grantOwnRuntimePermissions(): List<String> {
        val perms = buildList { if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
        return perms.filter {
            runCatching {
                dpm.setPermissionGrantState(admin, ctx.packageName, it, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }.getOrDefault(false)
        }
    }

    companion object {
        const val KIOSK_HOME_ALIAS = "com.yuandian.nightprotocol.kiosk.KioskHomeAlias"
    }
}
