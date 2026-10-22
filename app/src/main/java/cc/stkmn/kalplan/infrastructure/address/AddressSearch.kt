package cc.stkmn.kalplan.infrastructure.address

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.infrastructure.routing.BoundedHttps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.LocalDate

/** One shared serialized request stream. No fallbacks, retries, or background geocoding. */
object AddressSearch {
    private val mutex = Mutex()
    private val cache = linkedMapOf<String, List<String>>()
    private var lastCall = 0L
    suspend fun suggest(repository: AppRepository, query: String, language: String): List<String> = mutex.withLock {
        val clean = query.trim().take(200)
        if (clean.length < 3 || !repository.data.value.settings.addressSuggestionsEnabled) return@withLock emptyList()
        val key = "$language:${clean.lowercase()}"
        cache[key]?.let { return@withLock it }
        val wait = 1100 - (android.os.SystemClock.elapsedRealtime() - lastCall)
        if (wait > 0) delay(wait)
        val date = LocalDate.now().toString()
        repository.update { state ->
            val budget = state.routeBudgets["PHOTON"]?.takeIf { it.date == date }?.used ?: 0
            require(state.settings.addressSuggestionsEnabled && budget < state.settings.addressDailyLimit.coerceIn(0, 1000)) { "address_cap_reached" }
            state.copy(routeBudgets = state.routeBudgets + ("PHOTON" to Budget(date, budget + 1)))
        }
        lastCall = android.os.SystemClock.elapsedRealtime()
        val results = withContext(Dispatchers.IO) {
            val response = BoundedHttps.json("https://photon.komoot.io/api/?q=" + URLEncoder.encode(clean, "UTF-8") + "&limit=5&lang=" + if (language == "de") "de" else "en")
            val features = response.optJSONArray("features") ?: return@withContext emptyList()
            (0 until minOf(features.length(), 5)).mapNotNull { i ->
                val properties = features.optJSONObject(i)?.optJSONObject("properties") ?: return@mapNotNull null
                listOf("name", "street", "housenumber", "postcode", "city", "country")
                    .map { properties.optString(it).take(120) }.filter(String::isNotBlank).distinct().joinToString(", ").takeIf(String::isNotBlank)
            }.distinct()
        }
        if (cache.size >= 30) cache.remove(cache.keys.first())
        cache[key] = results
        results
    }
}
