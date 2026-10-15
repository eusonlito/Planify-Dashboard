package com.lito.planify.dashboard.admin

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad que solo sirve para abrir el menú de apagado de Android (Apagar / Reiniciar):
 * ninguna app, ni siendo Device Owner, puede apagar el dispositivo, pero un servicio de accesibilidad sí puede
 * mostrar ese menú. Lo activa install.sh (`settings put secure enabled_accessibility_services …`).
 * El lock task tiene que permitir LOCK_TASK_FEATURE_GLOBAL_ACTIONS (ver [Kiosk]).
 */
class PowerMenuService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        // Se suelta en onUnbind
        @SuppressLint("StaticFieldLeak")
        private var instance: PowerMenuService? = null

        /** Devuelve false si el servicio no está activado. */
        fun open(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false
    }
}
