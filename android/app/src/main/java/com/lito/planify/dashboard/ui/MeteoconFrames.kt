package com.lito.planify.dashboard.ui

import android.content.Context
import android.graphics.Canvas
import android.util.LruCache
import androidx.annotation.RawRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fotogramas pre-renderizados de los iconos animados.
 *
 * Dibujar un Lottie en cada fotograma es caro en este marco: con 7–8 iconos a 30 fps se iba a 1–2 núcleos y 70 °C.
 * Así que cada icono se dibuja una sola vez, en segundo plano y al tamaño exacto en el que se pinta: [FRAMES]
 * fotogramas de su bucle de 6 s. Después, animarlo es pintar un bitmap. Los bitmaps van en memoria nativa, en una caché
 * limitada a [MAX_BYTES] que descarta los menos usados.
 */
object MeteoconFrames {
    /** 15 fps durante los 6 s del bucle de Meteocons. */
    const val FRAMES = 90

    private const val MAX_BYTES = 48 * 1024 * 1024

    private data class Key(@RawRes val icon: Int, val px: Int, val frames: Int)

    private val cache = object : LruCache<Key, List<ImageBitmap>>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: List<ImageBitmap>) = key.px * key.px * 4 * value.size
    }

    /** Uno a la vez: renderizar es lo caro, y así los iconos repetidos se reutilizan en vez de calcularse dos veces. */
    private val mutex = Mutex()

    /** Fotogramas de [icon] a [px]×[px]. [frames] = 1 para un icono estático (su primer fotograma). */
    suspend fun get(context: Context, @RawRes icon: Int, px: Int, frames: Int): List<ImageBitmap> {
        val key = Key(icon, px, frames)
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key) ?: withContext(Dispatchers.Default) { render(context, icon, px, frames) }.also { cache.put(key, it) }
        }
    }

    private fun render(context: Context, @RawRes icon: Int, px: Int, frames: Int): List<ImageBitmap> {
        val composition = LottieCompositionFactory.fromRawResSync(context.applicationContext, icon).value ?: return emptyList()
        val drawable = LottieDrawable().apply {
            setComposition(composition)
            setBounds(0, 0, px, px)
        }
        return List(frames) { index ->
            drawable.progress = index / frames.toFloat()
            createBitmap(px, px).also { drawable.draw(Canvas(it)) }.asImageBitmap()
        }
    }
}
