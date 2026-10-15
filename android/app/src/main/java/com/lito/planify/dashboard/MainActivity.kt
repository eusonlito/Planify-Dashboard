package com.lito.planify.dashboard

import android.content.Intent
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
import com.lito.planify.dashboard.power.ScreenSchedule
import com.lito.planify.dashboard.ui.DashboardActions
import com.lito.planify.dashboard.ui.DashboardApp
import com.lito.planify.dashboard.ui.ProvideIconClock
import com.lito.planify.dashboard.ui.SettingsCallbacks
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
            sleep = { if (!Kiosk.sleep(this)) Toast.makeText(this, "Apagar requiere el modo quiosco (Device Owner)", Toast.LENGTH_LONG).show() },
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
                // El reloj nocturno va con el brillo al mínimo; el resto, con el elegido en Ajustes (o el del sistema)
                viewModel.state.map { if (it.night) NIGHT_BRIGHTNESS else it.brightness }.distinctUntilChanged().collect { value ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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

    private companion object {
        /** El paso más bajo de la retroiluminación (1 de 255): lo justo para leer el reloj nocturno a oscuras. */
        const val NIGHT_BRIGHTNESS = 1f / 255f
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
