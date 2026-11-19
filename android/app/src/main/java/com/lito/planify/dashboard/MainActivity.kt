package com.lito.planify.dashboard

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lito.planify.dashboard.admin.Kiosk
import com.lito.planify.dashboard.admin.PowerMenuService
import com.lito.planify.dashboard.admin.ReturnButton
import com.lito.planify.dashboard.data.MIN_BRIGHTNESS
import com.lito.planify.dashboard.power.ScreenSchedule
import com.lito.planify.dashboard.ui.DashboardActions
import com.lito.planify.dashboard.ui.DashboardApp
import com.lito.planify.dashboard.ui.ProvideIconClock
import com.lito.planify.dashboard.ui.SettingsCallbacks
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La alarma de encendido trae la actividad al frente con la pantalla apagada.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val actions = DashboardActions(
            // La luna: con el reloj nocturno activo pasa a él; si no, apaga la pantalla
            sleep = {
                if (!viewModel.nightNow() && !Kiosk.sleep(this)) {
                    Toast.makeText(this, "Apagar requiere el modo quiosco (Device Owner)", Toast.LENGTH_LONG).show()
                }
            },
            openWifi = { openSystemSettings(Settings.ACTION_WIFI_SETTINGS) },
            openSettings = { openSystemSettings(Settings.ACTION_SETTINGS) },
            powerMenu = { if (!PowerMenuService.open()) Toast.makeText(this, "Falta activar el servicio de apagado (scripts/install.sh)", Toast.LENGTH_LONG).show() }
        )
        val settings = SettingsCallbacks(
            onSearch = viewModel::searchLocation,
            onSelectLocation = viewModel::selectLocation,
            onSchedule = viewModel::setSchedule,
            onBrightness = viewModel::setBrightness,
            onLogout = viewModel::logout
        )

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Se diseña en píxeles nativos del panel (1920×1200): 1 dp = 1 sp = 1 px.
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                ProvideIconClock {
                    DashboardApp(state, actions, viewModel::login, viewModel::loadWeek, viewModel::syncAll, settings, viewModel::peek)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.run() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.state, systemBrightness()) { state, system ->
                    when {
                        state.night -> MIN_BRIGHTNESS
                        state.brightness != null -> state.brightness
                        system < MIN_BRIGHTNESS -> MIN_BRIGHTNESS
                        else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }.distinctUntilChanged().collect { value ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = value
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ReturnButton.hide(this)
        Kiosk.enter(this)
        hideSystemBars()
        // Si se ha encendido con el botón fuera de horario, programa el apagado de cortesía.
        ScreenSchedule.reschedule(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Ajustes de Android, con el botón flotante para volver (el marco no tiene barra de navegación). */
    private fun openSystemSettings(action: String) {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        ReturnButton.show(this)
    }

    private fun systemBrightness() = callbackFlow {
        val setting = Settings.System.SCREEN_BRIGHTNESS
        fun update() { trySend(Settings.System.getInt(contentResolver, setting, 255) / 255f) }
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = update()
        }
        contentResolver.registerContentObserver(Settings.System.getUriFor(setting), false, observer)
        update()
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
