package cc.stkmn.kalplan.infrastructure.routing

import cc.stkmn.kalplan.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import java.time.LocalDate
import kotlin.math.ceil

data class RoutePoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
    companion object {
        fun parse(text: String): RoutePoint {
            val parts = text.split(',').map { it.trim().toDouble() }
            require(parts.size == 2) { "Use latitude,longitude" }
            return RoutePoint(parts[0], parts[1])
        }
    }
}
data class ManualRoute(val minutes: Int, val km: Double)

object BoundedHttps {
    fun json(url: String, body: String? = null, headers: Map<String, String> = emptyMap()): JSONObject {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        connection.requestMethod = if (body == null) "GET" else "POST"
        connection.setRequestProperty("User-Agent", "KalPlan")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            require(connection.responseCode in 200..299) { "HTTP_${connection.responseCode}" }
            require(connection.contentLengthLong <= 1_000_000) { "response_limit" }
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1_000_000) { "response_limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
/** No geocoder, background route calls, automatic fallback, redirects or retries. Exactly one budget unit per HTTP request. */
class ManualRouting(private val repository: AppRepository) {
    suspend fun route(provider: String, origin: RoutePoint, destination: RoutePoint, key: String): ManualRoute = withContext(Dispatchers.IO) {
        require(key.isNotBlank() && !key.any { it == '\r' || it == '\n' }) { "Missing API key" }
        require(provider in setOf("GOOGLE", "HERE", "TOMTOM", "ORS", "GRAPHHOPPER"))
        val date = LocalDate.now().toString()
        repository.update { state ->
            val used = state.routeBudgets[provider]?.takeIf { it.date == date }?.used ?: 0
            require(used < state.settings.routingDailyLimit) { "Routing cap reached" }
            state.copy(routeBudgets = state.routeBudgets + (provider to Budget(date, used + 1)))
        }
        fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
        val o = "${origin.latitude},${origin.longitude}"
        val d = "${destination.latitude},${destination.longitude}"
        val response: JSONObject
        val seconds: Double
        val meters: Double
        when (provider) {
            "GOOGLE" -> {
                fun waypoint(point: RoutePoint) = JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", point.latitude).put("longitude", point.longitude)))
                val body = JSONObject().put("origin", waypoint(origin)).put("destination", waypoint(destination)).put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_UNAWARE")
                response = BoundedHttps.json("https://routes.googleapis.com/directions/v2:computeRoutes", body.toString(),
                    mapOf("X-Goog-Api-Key" to key, "X-Goog-FieldMask" to "routes.duration,routes.distanceMeters"))
                val route = response.getJSONArray("routes").getJSONObject(0)
                seconds = route.getString("duration").removeSuffix("s").toDouble(); meters = route.getDouble("distanceMeters")
            }
            "HERE" -> {
                response = BoundedHttps.json("https://router.hereapi.com/v8/routes?transportMode=car&origin=${encoded(o)}&destination=${encoded(d)}&return=summary&apiKey=${encoded(key)}")
                val sections = response.getJSONArray("routes").getJSONObject(0).getJSONArray("sections")
                var duration = 0.0; var distance = 0.0
                for (i in 0 until sections.length()) { val summary = sections.getJSONObject(i).getJSONObject("summary"); duration += summary.getDouble("duration"); distance += summary.getDouble("length") }
                seconds = duration; meters = distance
            }
            "TOMTOM" -> {
                response = BoundedHttps.json("https://api.tomtom.com/routing/1/calculateRoute/$o:$d/json?traffic=false&key=${encoded(key)}")
                val summary = response.getJSONArray("routes").getJSONObject(0).getJSONObject("summary")
                seconds = summary.getDouble("travelTimeInSeconds"); meters = summary.getDouble("lengthInMeters")
            }
            "ORS" -> {
                val body = JSONObject().put("coordinates", JSONArray().put(JSONArray().put(origin.longitude).put(origin.latitude)).put(JSONArray().put(destination.longitude).put(destination.latitude)))
                response = BoundedHttps.json("https://api.heigit.org/openrouteservice/v2/directions/driving-car/geojson", body.toString(), mapOf("Authorization" to key))
                val summary = response.getJSONArray("features").getJSONObject(0).getJSONObject("properties").getJSONObject("summary")
                seconds = summary.getDouble("duration"); meters = summary.getDouble("distance")
            }
            else -> {
                response = BoundedHttps.json("https://graphhopper.com/api/1/route?point=${encoded(o)}&point=${encoded(d)}&profile=car&instructions=false&calc_points=false&key=${encoded(key)}")
                val path = response.getJSONArray("paths").getJSONObject(0)
                seconds = path.getDouble("time") / 1000.0; meters = path.getDouble("distance")
            }
        }
        require(seconds.isFinite() && meters.isFinite() && seconds in 0.0..604800.0 && meters in 0.0..100_000_000.0)
        ManualRoute(ceil(seconds / 60).toInt(), meters / 1000)
    }
}
