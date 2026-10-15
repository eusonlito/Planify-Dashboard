package com.lito.planify.dashboard.data.repository

import com.lito.planify.dashboard.data.Api
import com.lito.planify.dashboard.data.AuthRequest
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.data.Forecast
import com.lito.planify.dashboard.data.GeoResponse
import com.lito.planify.dashboard.data.Task
import com.lito.planify.dashboard.data.UserResponse
import com.lito.planify.dashboard.data.cache.DiskCache
import com.lito.planify.dashboard.data.isUnauthorized
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

class CalendarRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** API falsa: devuelve un evento por petición, o falla como se le diga. */
    private class FakeApi : Api {
        var failure: Exception? = null
        var status = 200

        override suspend fun events(auth: String, start: Long, end: Long?, routine: Boolean, timezone: String, dashboard: Boolean): Response<List<Event>> {
            failure?.let { throw it }
            if (status != 200) return Response.error(status, "".toResponseBody())
            return Response.success(listOf(Event(1, 1, "Casa", "#000000", if (routine) "Rutina" else "Evento", "2026-10-14 10:00:00")))
        }

        override suspend fun auth(request: AuthRequest): Response<UserResponse> = error("no se usa")
        override suspend fun tasks(auth: String, dashboard: Boolean): Response<List<Task>> = error("no se usa")
        override suspend fun forecast(url: String): Response<Forecast> = error("no se usa")
        override suspend fun geocode(url: String): Response<GeoResponse> = error("no se usa")
    }

    private val api = FakeApi()
    private var clock = 1_000_000L
    private val monday = LocalDate.of(2026, 10, 12)

    private fun repository() = CalendarRepository(api, DiskCache(folder.root), now = { clock })

    @Test
    fun downloadedWeekIsAvailableAfterRestart() = runBlocking {
        repository().refresh("token", monday)
        val week = repository().weeks.value[monday]
        assertEquals("Evento", week?.events?.single()?.title)
        assertEquals("Rutina", week?.sessions?.single()?.title)
    }

    @Test
    fun offlineKeepsTheCachedWeek() = runBlocking {
        val repository = repository()
        repository.refresh("token", monday)
        clock += CalendarRepository.MAX_AGE_MS
        assertTrue(repository.isStale(monday))
        api.failure = IOException("sin red")
        assertTrue(repository.refresh("token", monday).isFailure)
        assertEquals(1, repository.weeks.value[monday]?.events?.size)
    }

    @Test
    fun expiredSessionIsReportedAsUnauthorized() = runBlocking {
        api.status = 401
        val error = repository().refresh("token", monday).exceptionOrNull()
        assertTrue(error?.isUnauthorized == true)
    }

    @Test
    fun keepsOnlyTheMostRecentlyDownloadedWeeks() = runBlocking {
        val repository = repository()
        val mondays = (0L until CalendarRepository.KEEP + 3L).map { monday.plusWeeks(it) }
        mondays.forEach {
            clock += 1_000
            repository.refresh("token", it)
        }
        val kept = mondays.takeLast(CalendarRepository.KEEP).toSet()
        assertEquals(kept, repository.weeks.value.keys)
        assertEquals(kept, repository().weeks.value.keys)
    }

    @Test
    fun clearForgetsEverything() = runBlocking {
        val repository = repository()
        repository.refresh("token", monday)
        repository.clear()
        assertTrue(repository.weeks.value.isEmpty())
        assertTrue(repository().weeks.value.isEmpty())
    }
}
