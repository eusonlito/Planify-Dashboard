package com.lito.planify.dashboard

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lito.planify.dashboard.data.Api
import com.lito.planify.dashboard.data.AuthRequest
import com.lito.planify.dashboard.data.Content
import com.lito.planify.dashboard.data.Forecast
import com.lito.planify.dashboard.data.GeoResult
import com.lito.planify.dashboard.data.Location
import com.lito.planify.dashboard.data.ReferenceClock
import com.lito.planify.dashboard.data.Schedule
import com.lito.planify.dashboard.data.Store
import com.lito.planify.dashboard.data.Week
import com.lito.planify.dashboard.data.cache.DiskCache
import com.lito.planify.dashboard.data.isUnauthorized
import com.lito.planify.dashboard.data.repository.CalendarRepository
import com.lito.planify.dashboard.data.repository.ContentRepository
import com.lito.planify.dashboard.data.repository.WeatherRepository
import com.lito.planify.dashboard.power.ScreenSchedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.io.File
import java.time.LocalDateTime

data class UiState(
    val token: String? = null,
    val email: String? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    /** false mientras el reloj no se haya sincronizado (el RTC vuelve a la fecha de fábrica). */
    val clockValid: Boolean = false,
    val content: Content? = null,
    val forecast: Forecast? = null,
    val location: Location? = null,
    val schedule: Schedule = Schedule(),
    val brightness: Float? = null,
    /** false si el último intento de sincronizar falló: se muestra lo que hay en caché. */
    val online: Boolean = true,
    /** Hay una red con salida a Internet (sin ella, el login muestra la espera y "Ir a Ajustes"). */
    val connected: Boolean = false,
    /** Sincronización manual en curso (botón de la barra lateral). */
    val syncing: Boolean = false,
    /** Semanas del calendario descargadas bajo demanda, por su lunes. */
    val weeks: Map<LocalDate, Week> = emptyMap(),
    val loginError: String? = null,
    val loggingIn: Boolean = false,
    val geoResults: List<GeoResult> = emptyList(),
    val geoSearching: Boolean = false
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)
    private val api = Api.client
    private val cache = DiskCache(File(application.filesDir, "cache"))
    private val contentRepository = ContentRepository(api, cache)
    private val weatherRepository = WeatherRepository(api, cache)
    private val calendarRepository = CalendarRepository(api, cache)
    private val clock = ReferenceClock(BuildConfig.BUILD_TIME, { store.lastValidTime }, { store.lastValidTime = it })

    private val _state = MutableStateFlow(
        clock.current().let { time ->
            UiState(
                token = store.token,
                email = store.email,
                now = time.now,
                clockValid = time.valid,
                content = contentRepository.content.value?.value,
                forecast = weatherRepository.forecast.value?.value,
                weeks = calendarRepository.weeks.value,
                location = store.location,
                schedule = store.schedule,
                brightness = store.brightness
            )
        }
    )
    val state: StateFlow<UiState> = _state

    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            setConnected(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))

        override fun onLost(network: Network) = setConnected(false)
    }

    init {
        // Los repositorios publican la copia en disco y cada descarga buena: la pantalla siempre tiene lo último.
        viewModelScope.launch { contentRepository.content.collect { c -> _state.update { it.copy(content = c?.value) } } }
        viewModelScope.launch { weatherRepository.forecast.collect { f -> _state.update { it.copy(forecast = f?.value) } } }
        viewModelScope.launch { calendarRepository.weeks.collect { w -> _state.update { it.copy(weeks = w) } } }
        setConnected(connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onCleared() {
        connectivity.unregisterNetworkCallback(networkCallback)
    }

    /** Al recuperar la red se sincroniza enseguida, sin esperar al siguiente ciclo. */
    private fun setConnected(connected: Boolean) {
        val was = _state.value.connected
        _state.update { it.copy(connected = connected) }
        if (connected && !was) refreshNow()
    }

    /** Bucle mientras la actividad está visible: reloj cada minuto; contenido y tiempo cuando caducan (5 y 30 min). */
    suspend fun run() {
        while (true) {
            tick()
            if (_state.value.clockValid) {
                if (contentRepository.isStale()) refreshContent()
                if (weatherRepository.isStale()) refreshWeather()
            }
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }

    private fun tick() {
        val time = clock.current()
        _state.update { it.copy(now = time.now, clockValid = time.valid) }
    }

    /** Fuerza la sincronización (al recuperar la red, tras iniciar sesión...). */
    fun refreshNow() {
        viewModelScope.launch {
            tick()
            if (!_state.value.clockValid) return@launch
            refreshContent()
            refreshWeather()
        }
    }

    /** Sin hora válida no se pide nada: las fechas de la petición serían las de fábrica. */
    private suspend fun refreshContent() {
        val token = _state.value.token ?: return
        report(contentRepository.refresh(token, LocalDate.now()))
    }

    private suspend fun refreshWeather() {
        val location = _state.value.location ?: return
        report(weatherRepository.refresh(location))
    }

    /** Carga la semana del calendario que empieza en [monday] si no está en caché o ha caducado. */
    fun loadWeek(monday: LocalDate) {
        val token = _state.value.token ?: return
        if (!calendarRepository.isStale(monday)) return
        viewModelScope.launch { report(calendarRepository.refresh(token, monday)) }
    }

    /**
     * Botón de sincronizar: vuelve a pedir el contenido de Hoy, las semanas del calendario ya cargadas y el
     * tiempo, aunque no hayan caducado. Dura al menos un segundo para que se vea el giro.
     */
    fun syncAll() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val started = System.currentTimeMillis()
            tick()
            if (_state.value.clockValid) {
                refreshContent()
                _state.value.token?.let { token -> calendarRepository.loaded().forEach { report(calendarRepository.refresh(token, it)) } }
                refreshWeather()
            }
            delay((1000 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.update { it.copy(syncing = false) }
        }
    }

    /** Estado de la conexión según la última petición. Si falla, la pantalla sigue con la copia en caché. */
    private fun report(result: Result<*>) {
        val error = result.exceptionOrNull()
        when {
            error == null -> _state.update { it.copy(online = true) }
            error.isUnauthorized -> logout()
            else -> _state.update { it.copy(online = false) }
        }
    }

    fun login(email: String, password: String) {
        _state.update { it.copy(loggingIn = true, loginError = null) }
        viewModelScope.launch {
            try {
                val response = api.auth(AuthRequest(email.trim(), password))
                val token = response.body()?.token
                if (!response.isSuccessful || token == null) {
                    _state.update { it.copy(loggingIn = false, loginError = "Email o contraseña incorrectos") }
                    return@launch
                }
                store.token = token
                store.email = email.trim()
                _state.update { it.copy(token = token, email = email.trim(), loggingIn = false) }
                refreshNow()
            } catch (e: Exception) {
                _state.update { it.copy(loggingIn = false, loginError = "Sin conexión con el servidor") }
            }
        }
    }

    fun logout() {
        store.logout()
        contentRepository.clear()
        calendarRepository.clear()
        _state.update { it.copy(token = null) }
    }

    fun searchLocation(query: String) {
        if (query.isBlank()) return
        _state.update { it.copy(geoSearching = true) }
        viewModelScope.launch {
            val results = runCatching { api.geocode(Api.geocodeUrl(query.trim())).body()?.results }.getOrNull().orEmpty()
            _state.update { it.copy(geoResults = results, geoSearching = false) }
        }
    }

    fun selectLocation(result: GeoResult) {
        val location = Location(
            name = result.name,
            detail = listOfNotNull(result.admin1, result.country).joinToString(", "),
            latitude = result.latitude,
            longitude = result.longitude
        )
        store.location = location
        weatherRepository.clear()
        _state.update { it.copy(location = location, geoResults = emptyList()) }
        viewModelScope.launch { refreshWeather() }
    }

    fun setSchedule(schedule: Schedule) {
        store.schedule = schedule
        _state.update { it.copy(schedule = schedule) }
        ScreenSchedule.reschedule(getApplication())
    }

    fun setBrightness(value: Float?) {
        store.brightness = value
        _state.update { it.copy(brightness = value) }
    }
}
