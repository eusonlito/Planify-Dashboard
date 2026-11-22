package com.lito.planify.dashboard.updates

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lito.planify.dashboard.ui.PillButton
import com.lito.planify.dashboard.ui.theme.Type

@Composable
internal fun SystemUpdatePanel(model: SystemUpdateModel = viewModel()) {
    val context = LocalContext.current
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

    LaunchedEffect(model) {
        if (model.verified == null && model.percent == null) model.check()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Versión instalada · ${model.installed}", style = Type.Body)
        model.release?.let { Text("Versión disponible · ${it.version}", style = Type.Body) }
        Text(model.message, style = Type.Body)
        model.percent?.let { Text("Descarga · $it %", style = Type.Body) }
        when {
            model.percent != null -> UpdateButton("Cancelar descarga", onClick = model::cancel)
            model.verified != null -> UpdateButton("Instalar actualización") {
                if (context.packageManager.canRequestPackageInstalls()) install()
                else try {
                    permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
                } catch (_: RuntimeException) {
                    model.feedback("Abre Ajustes de Android y permite instalar aplicaciones desde esta fuente.")
                }
            }
            model.release != null -> UpdateButton("Descargar actualización", onClick = model::prepare)
        }
        if (model.percent == null) UpdateButton("Buscar actualizaciones", !model.checking, model::check)
    }
}

@Composable
private fun UpdateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    PillButton(text, modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = onClick)
}
