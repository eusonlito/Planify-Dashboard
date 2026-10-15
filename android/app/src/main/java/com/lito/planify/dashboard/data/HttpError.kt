package com.lito.planify.dashboard.data

import retrofit2.Response
import java.io.IOException

/** Respuesta HTTP sin éxito (o sin cuerpo). 401 = sesión caducada. */
class HttpError(val code: Int) : IOException("HTTP $code") {
    val unauthorized get() = code == 401
}

fun <T> Response<T>.bodyOrThrow(): T = body().takeIf { isSuccessful } ?: throw HttpError(code())

val Throwable.isUnauthorized get() = this is HttpError && unauthorized
