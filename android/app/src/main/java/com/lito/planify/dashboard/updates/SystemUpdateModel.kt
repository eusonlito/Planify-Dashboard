package com.lito.planify.dashboard.updates

import android.text.format.Formatter
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File

class SystemUpdateModel(private val app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    @Suppress("DEPRECATION")
    val installed = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    var message by mutableStateOf("")
        private set
    internal var release by mutableStateOf<UpdateRelease?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var percent by mutableStateOf<Int?>(null)
        private set
    var verified by mutableStateOf<File?>(null)
        private set
    private var download: Job? = null

    init {
        val name = saved.get<String>("apk")
        val version = saved.get<String>("version")
        val size = saved.get<Long>("size")
        if (name != null && File(name).name == name && version != null && size != null) {
            val candidate = File(File(app.cacheDir, "updates"), name)
            val pending = UpdateRelease(version, size)
            if (pending.newerThan(installed) && candidate.isFile && candidate.length() == size) {
                release = pending
                verified = candidate
                message = "Descarga verificada. Puedes continuar con la instalación."
            }
        }
    }

    fun check() {
        if (checking || percent != null) return
        checking = true
        saved.remove<String>("apk")
        release = null
        verified = null
        message = "Consultando la última versión…"
        viewModelScope.launch {
            try {
                val latest = withContext(Dispatchers.IO) {
                    File(app.cacheDir, "updates").listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000 }
                        ?.forEach { it.delete() }
                    latestRelease()
                }
                release = latest?.takeIf { it.newerThan(installed) }
                message = when {
                    latest == null -> "Todavía no hay una versión publicada."
                    release == null -> "Tienes la versión más reciente."
                    else -> "Tamaño de la descarga · ${Formatter.formatShortFileSize(app, latest.size)}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = failure(e)
            } finally {
                checking = false
            }
        }
    }

    fun prepare() {
        val available = release ?: return
        if (percent != null) return
        percent = 0
        message = "Descargando y verificando el APK…"
        download = viewModelScope.launch {
            try {
                verified = withTimeout(240_000) {
                    withContext(Dispatchers.IO) {
                        downloadUpdate(app, available) { percent = it }
                    }
                }
                saved["version"] = available.version
                saved["size"] = available.size
                saved["apk"] = verified!!.name
                message = "Descarga verificada. Android te pedirá confirmar la instalación."
            } catch (_: TimeoutCancellationException) {
                message = "Se agotó el tiempo de descarga. Puedes volver a intentarlo."
            } catch (e: CancellationException) {
                message = "Descarga cancelada."
                throw e
            } catch (e: Exception) {
                message = failure(e)
            } finally {
                percent = null
            }
        }
    }

    fun cancel() = download?.cancel()
    fun feedback(text: String) { message = text }

    private fun failure(error: Exception) = when (error) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "No se pudo conectar con GitHub. Comprueba la conexión y vuelve a intentarlo."
        else -> error.localizedMessage ?: "No se pudo verificar la actualización."
    }
}
