package com.lito.planify.dashboard.data

import com.lito.planify.dashboard.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/** Planify (solo lectura: autenticación y listados del dashboard) y Open-Meteo. */
interface Api {
    @POST("user/auth")
    suspend fun auth(@Body request: AuthRequest): Response<UserResponse>

    /**
     * `start_date`/`end_date`: hora local expresada como segundos epoch en UTC, igual que la app. Sin `end_date`
     * (null) no hay límite: todos los eventos desde `start_date`.
     */
    @GET("event")
    suspend fun events(
        @Header("Authorization") auth: String,
        @Query("start_date") start: Long,
        @Query("end_date") end: Long?,
        @Query("routine") routine: Boolean,
        @Query("timezone") timezone: String,
        @Query("dashboard") dashboard: Boolean = true
    ): Response<List<Event>>

    @GET("task")
    suspend fun tasks(@Header("Authorization") auth: String, @Query("dashboard") dashboard: Boolean = true): Response<List<Task>>

    @GET
    suspend fun forecast(@Url url: String): Response<Forecast>

    @GET
    suspend fun geocode(@Url url: String): Response<GeoResponse>

    companion object {
        val client: Api by lazy {
            val http = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Api::class.java)
        }

        fun forecastUrl(location: Location) = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}&longitude=${location.longitude}&timezone=auto&past_days=1&forecast_days=14" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,is_day" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,sunrise,sunset"

        fun geocodeUrl(query: String) = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=es&format=json&name=" +
            java.net.URLEncoder.encode(query, "UTF-8")
    }
}
