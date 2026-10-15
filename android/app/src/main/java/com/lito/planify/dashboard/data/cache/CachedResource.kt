package com.lito.planify.dashboard.data.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

/**
 * Un dato remoto con copia en disco ("stale-if-error"):
 * - al crearse publica la última copia guardada, así que se pinta enseguida, con red o sin ella;
 * - [refresh] descarga, guarda y publica; si falla, la copia anterior se queda tal cual y se devuelve el error;
 * - nunca hay dos refrescos a la vez del mismo dato.
 * La lectura inicial es síncrona a propósito: los ficheros son pequeños y así el primer fotograma ya sale completo.
 */
class CachedResource<T>(
    private val cache: DiskCache,
    private val key: String,
    private val type: Type,
    private val maxAgeMs: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val _data = MutableStateFlow<Cached<T>?>(cache.read(key, type))
    val data: StateFlow<Cached<T>?> = _data

    /** No hay copia, o tiene más de [maxAgeMs]. */
    fun isStale(): Boolean = _data.value?.let { now() - it.fetchedAt >= maxAgeMs } ?: true

    suspend fun refresh(fetch: suspend () -> T): Result<T> = mutex.withLock {
        val value = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Result.failure(e)
        }
        val entry = Cached(value, now())
        // Si no se puede guardar, el dato nuevo se muestra igual: solo se pierde la copia para el próximo arranque.
        runCatching { withContext(Dispatchers.IO) { cache.write(key, entry, type) } }
        _data.value = entry
        Result.success(value)
    }

    fun clear() {
        cache.remove(key)
        _data.value = null
    }
}
