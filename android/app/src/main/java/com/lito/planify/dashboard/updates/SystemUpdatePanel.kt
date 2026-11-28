package com.lito.planify.dashboard.updates

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lito.planify.dashboard.ui.PillButton
import com.lito.planify.dashboard.ui.theme.Type
import com.lito.planify.dashboard.ui.theme.Palette

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
    val onUpdate = {
        updateRequested = true
        if (model.verified == null) model.prepare()
    }
    val onCancel: () -> Unit = {
        updateRequested = false
        model.cancel()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(52.dp)) {
        Column(Modifier.weight(0.9f)) { VersionSheet(model) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            UpdateStatus(model)
            when {
                downloading -> PillButton("Cancelar descarga", Modifier.fillMaxWidth(), background = Palette.Background, onClick = onCancel)
                model.release != null -> PillButton("Actualizar", Modifier.fillMaxWidth(), primary = true, enabled = !updateRequested, onClick = onUpdate)
            }
            if (!downloading && !updateRequested) {
                PillButton("Buscar actualizaciones", Modifier.fillMaxWidth(), primary = model.release == null, enabled = !model.checking, background = Palette.Background, onClick = model::check)
            }
        }
    }
}

@Composable
private fun VersionSheet(model: SystemUpdateModel) {
    val paper = Palette.Highlight
    val band = Palette.Accent
    val ink = Palette.Ink
    val available = model.release?.version
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(paper)) {
        Row(Modifier.fillMaxWidth().background(band).padding(horizontal = 38.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.SystemUpdateAlt, null, Modifier.size(38.dp), tint = Palette.Card)
            Text("Planify Dashboard", style = Type.Body, color = Palette.Card, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(38.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (available == null) "Versión instalada" else "Nueva versión", style = Type.Small, color = ink)
                Text(available ?: model.installed, style = Type.Huge.copy(fontSize = 84.sp, lineHeight = 92.sp, letterSpacing = (-3).sp), color = ink, fontWeight = FontWeight.Bold)
                if (available != null) Text("Instalada · ${model.installed}", style = Type.Small, color = ink)
            }
            Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Spacer(Modifier.matchParentSize().rotate(-12f).background(band.copy(alpha = 0.14f), RoundedCornerShape(27.dp)))
                Icon(if (available == null) Icons.Outlined.Layers else Icons.Outlined.FileDownload, null,
                    Modifier.matchParentSize().background(band, RoundedCornerShape(27.dp)).padding(23.dp),
                    tint = Palette.Card)
            }
        }
    }
}

@Composable
private fun UpdateStatus(model: SystemUpdateModel) {
    val percent = model.percent
    Text(model.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = Type.Large)
    if (percent != null) {
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Palette.Accent, trackColor = Palette.Line)
        Text("$percent %", style = Type.Small)
    } else if (model.checking) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Palette.Accent, trackColor = Palette.Line)
    }
}
