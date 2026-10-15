package com.lito.planify.dashboard.data.cache

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type

/** Copia local de un dato remoto: el valor y cuándo se descargó (epoch ms). */
data class Cached<T>(val value: T, val fetchedAt: Long)

/**
 * Caché persistente: un fichero JSON por clave en [dir].
 *
 * Cada escritura va a un temporal, se sincroniza con el disco y se renombra encima del anterior, así que un corte de
 * luz deja la versión vieja o la nueva, nunca una a medias. Un fichero ilegible (corrupto o de un modelo antiguo)
 * cuenta como vacío: se vuelve a descargar.
 */
class DiskCache(private val dir: File, private val gson: Gson = Gson()) {

    @Synchronized
    fun <T> read(key: String, type: Type): Cached<T>? {
        val file = file(key)
        if (!file.isFile) return null
        return runCatching {
            val entry: Cached<T>? = gson.fromJson(file.readText(), entryType(type))
            // Gson no comprueba la nulabilidad de Kotlin: sin valor, la entrada no sirve.
            entry?.takeIf { it.value != null }
        }.getOrNull()
    }

    @Synchronized
    fun <T> write(key: String, entry: Cached<T>, type: Type) {
        dir.mkdirs()
        val target = file(key)
        val temp = File(dir, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(gson.toJson(entry, entryType(type)).toByteArray())
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("No se pudo guardar $key en la caché")
        }
    }

    @Synchronized
    fun remove(key: String) {
        file(key).delete()
    }

    /** Claves guardadas que empiezan por [prefix]. */
    @Synchronized
    fun keys(prefix: String = ""): List<String> =
        dir.listFiles().orEmpty()
            .map { it.name }
            .filter { it.endsWith(SUFFIX) && it.startsWith(prefix) }
            .map { it.removeSuffix(SUFFIX) }

    private fun file(key: String): File {
        require(KEY.matches(key)) { "Clave de caché no válida: $key" }
        return File(dir, key + SUFFIX)
    }

    private fun entryType(type: Type): Type = TypeToken.getParameterized(Cached::class.java, type).type

    private companion object {
        const val SUFFIX = ".json"
        val KEY = Regex("[a-z0-9][a-z0-9_.-]*")
    }
}
