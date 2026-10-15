package com.lito.planify.dashboard.admin

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import com.lito.planify.dashboard.MainActivity

class AdminReceiver : DeviceAdminReceiver()

/**
 * Modo quiosco con Device Owner (`dpm set-device-owner com.lito.planify.dashboard/.admin.AdminReceiver`).
 * Sin Device Owner (por ejemplo en el emulador) todo esto no hace nada y la app funciona igual.
 * Para quitarlo (y poder desinstalar) se hace por ADB root: ver docs/SETUP.md, "Mantenimiento y vuelta atrás".
 */
object Kiosk {
    /** Ajustes de Android se permite dentro del lock task para configurar el Wi-Fi. */
    private const val SETTINGS = "com.android.settings"

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)
    private fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isOwner(context: Context) = dpm(context).isDeviceOwnerApp(context.packageName)

    /** Se llama en cada arranque de la actividad: es idempotente. */
    fun enter(activity: Activity) {
        if (!isOwner(activity)) return
        val dpm = dpm(activity)
        val admin = admin(activity)
        dpm.setLockTaskPackages(admin, arrayOf(activity.packageName, SETTINGS))
        // Solo el menú de apagado (pulsación larga del botón de encendido): sin él no hay forma limpia de apagar
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
        dpm.setKeyguardDisabled(admin, true)
        dpm.setStatusBarDisabled(admin, true)
        dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        dpm.addPersistentPreferredActivity(
            admin,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            ComponentName(activity, MainActivity::class.java)
        )
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) activity.startLockTask()
    }

    /** Apaga la pantalla. Devuelve false si no somos Device Owner. */
    fun sleep(context: Context): Boolean {
        if (!isOwner(context)) return false
        dpm(context).lockNow()
        return true
    }
}
