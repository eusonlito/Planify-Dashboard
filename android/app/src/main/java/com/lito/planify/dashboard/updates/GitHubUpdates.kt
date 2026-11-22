package com.lito.planify.dashboard.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class UpdateFiles : FileProvider()

internal const val RELEASE_REPO = "eusonlito/Planify-Dashboard"
private const val APK = "Planify-Dashboard.apk"
private const val MAX_APK = 80L * 1024 * 1024
private val versionPattern = Regex("""(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})""")

internal data class UpdateRelease(val version: String, val size: Long) {
    init {
        require(versionPattern.matches(version) && size in 1..MAX_APK) { "Versión o tamaño de descarga inválidos." }
    }

    fun newerThan(installed: String): Boolean {
        require(versionPattern.matches(installed)) { "La versión instalada tiene un formato desconocido." }
        val current = installed.split('.').map(String::toInt)
        version.split('.').map(String::toInt).forEachIndexed { index, value ->
            if (value != current[index]) return value > current[index]
        }
        return false
    }
}

internal fun parseRelease(json: String): UpdateRelease {
    val release = JsonParser.parseString(json).asJsonObject
    val tag = release["tag_name"].asString
    require(tag.startsWith("v") && !release["draft"].asBoolean && !release["prerelease"].asBoolean) {
        "La publicación no es una versión estable válida."
    }
    val uploaded = release["assets"].asJsonArray.map { it.asJsonObject }
        .filter { it["state"].asString == "uploaded" && it["size"].asLong > 0 }
    fun size(name: String) = uploaded.singleOrNull { it["name"].asString == name }?.get("size")?.asLong
        ?: throw IOException("Falta el archivo $name en la publicación.")
    require(size("$APK.sha256") <= 1024) { "El archivo de verificación es inválido." }
    return UpdateRelease(tag.drop(1), size(APK))
}

private suspend fun <T> fetch(address: String, allowMissing: Boolean = false, read: suspend (InputStream) -> T): T? {
    var url = URL(address)
    repeat(6) {
        currentCoroutineContext().ensureActive()
        require(url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443) &&
            (url.host in setOf("github.com", "api.github.com") || url.host.endsWith(".githubusercontent.com"))) {
            "Destino de descarga inesperado."
        }
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Planify-Dashboard-Android")
            when (val status = conn.responseCode) {
                200 -> return conn.inputStream.use { read(it) }
                301, 302, 303, 307, 308 -> url = URL(url, conn.getHeaderField("Location") ?: error("Redirección incompleta."))
                404 -> if (allowMissing) return null else throw IOException("Archivo de actualización no disponible.")
                403, 429 -> throw IOException("GitHub ha limitado las consultas. Inténtalo más tarde.")
                else -> throw IOException("GitHub respondió con HTTP $status.")
            }
        } finally {
            conn.disconnect()
        }
    }
    throw IOException("Demasiadas redirecciones en la descarga.")
}

private suspend fun copyLimited(input: InputStream, output: OutputStream, limit: Long, progress: (Long) -> Unit = {}): Long {
    var total = 0L
    val buffer = ByteArray(32 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) return total
        total += count
        require(total <= limit) { "El archivo excede el tamaño previsto." }
        output.write(buffer, 0, count)
        progress(total)
    }
}

private suspend fun InputStream.text(limit: Long) = ByteArrayOutputStream().use {
    copyLimited(this, it, limit)
    it.toString("UTF-8")
}

internal suspend fun latestRelease() =
    fetch("https://api.github.com/repos/$RELEASE_REPO/releases/latest", allowMissing = true) { parseRelease(it.text(256 * 1024)) }

internal suspend fun downloadUpdate(context: Context, release: UpdateRelease, progress: (Int) -> Unit): File {
    val url = "https://github.com/$RELEASE_REPO/releases/download/v${release.version}/$APK"
    val checksum = fetch("$url.sha256") { it.text(1024) }!!.trim()
    val expected = Regex("""([a-fA-F0-9]{64})[ \t]+\*?${Regex.escape(APK)}""").matchEntire(checksum)?.groupValues?.get(1)
        ?: throw IOException("El formato de la suma SHA-256 es inválido.")
    val directory = File(context.cacheDir, "updates")
    directory.mkdirs()
    val file = File.createTempFile("update-", ".apk", directory)
    try {
        fetch(url) { input ->
            file.outputStream().buffered().use { output ->
                copyVerifiedApk(input, output, expected, release.size, progress)
            }
        }
        verifyUpdate(context, file, release.version)
        return file
    } catch (e: Throwable) {
        file.delete()
        throw e
    }
}

internal suspend fun copyVerifiedApk(input: InputStream, output: OutputStream, expected: String, size: Long, progress: (Int) -> Unit) {
    val digest = MessageDigest.getInstance("SHA-256")
    val stream = java.security.DigestOutputStream(output, digest)
    require(copyLimited(input, stream, size) { progress((it * 100 / size).toInt()) } == size) {
        "La descarga está incompleta."
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    require(actual.equals(expected, ignoreCase = true)) { "La suma SHA-256 del APK no coincide." }
}

@Suppress("DEPRECATION")
internal fun verifyUpdate(context: Context, file: File, version: String) {
    val pm = context.packageManager
    val flags = PackageManager.GET_SIGNING_CERTIFICATES
    fun PackageInfo.signers() = signingInfo?.apkContentsSigners
    val installed = pm.getPackageInfo(context.packageName, flags)
    // En Android 9 y 10 el analizador del APK solo recoge certificados si se pide también GET_SIGNATURES.
    val archive = pm.getPackageArchiveInfo(file.path, flags or PackageManager.GET_SIGNATURES) ?: error("El APK es ilegible.")
    require(archive.packageName == installed.packageName && archive.versionName == version &&
        PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed)) {
        "El APK no corresponde a la actualización de Planify Dashboard."
    }
    val signers = installed.signers()?.toSet().orEmpty()
    require(signers.isNotEmpty() && archive.signers()?.toSet() == signers) { "La firma es distinta de la instalación actual." }
}

internal fun installerIntent(context: Context, apk: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").apply {
        clipData = ClipData.newRawUri("Planify Dashboard", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
