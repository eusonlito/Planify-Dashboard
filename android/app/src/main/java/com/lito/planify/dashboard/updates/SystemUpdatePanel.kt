package com.lito.planify.dashboard.updates

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lito.planify.dashboard.ui.PillButton
import com.lito.planify.dashboard.ui.theme.Type

@Composable
internal fun SystemUpdatePanel(model: SystemUpdateModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var updateRequested by rememberSaveable { mutableStateOf(false) }
    val downloading = model.percent != null
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.feedback("Si la actualización sigue pendiente, puedes abrir de nuevo el instalador.")
    }
    fun install() {
        val apk = model.verified ?: return
        try {
            installer.launch(installerIntent(context, apk))
        } catch (e: RuntimeException) {
            model.feedback("No se pudo abrir el instalador. Vuelve a comprobar la actualización.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else model.feedback("Para continuar, permite a Planify Dashboard instalar aplicaciones desde esta fuente.")
    }

    LaunchedEffect(updateRequested, downloading, model.verified, lifecycle) {
        if (!updateRequested || downloading) return@LaunchedEffect
        if (model.verified == null) {
            updateRequested = false
            return@LaunchedEffect
        }
        lifecycle.withResumed {
            updateRequested = false
            if (context.packageManager.canRequestPackageInstalls()) install()
            else try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            } catch (_: RuntimeException) {
                model.feedback("Abre Ajustes de Android y permite instalar aplicaciones desde esta fuente.")
            }
        }
    }
    LaunchedEffect(model) {
        if (model.verified == null && model.percent == null) model.check()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Versión instalada · ${model.installed}", style = Type.Body)
        model.release?.let { Text("Versión disponible · ${it.version}", style = Type.Body) }
        Text(model.message, style = Type.Body)
        model.percent?.let { Text("Descarga · $it %", style = Type.Body) }
        when {
            downloading -> UpdateButton("Cancelar descarga") {
                updateRequested = false
                model.cancel()
            }
            model.release != null -> UpdateButton("Actualizar", enabled = !updateRequested) {
                updateRequested = true
                if (model.verified == null) model.prepare()
            }
        }
        if (!downloading && !updateRequested) UpdateButton("Buscar actualizaciones", !model.checking, model::check)
    }
}

@Composable
private fun UpdateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    PillButton(text, modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = onClick)
}
