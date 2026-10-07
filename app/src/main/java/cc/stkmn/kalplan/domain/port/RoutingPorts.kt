package cc.stkmn.kalplan.domain.port

import cc.stkmn.kalplan.domain.model.GeoPoint
import java.time.Instant

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val arrivalTime: Instant?
)

data class RouteEstimate(
    val distanceMeters: Long,
    val durationSeconds: Long,
    val providerId: String,
    val calculatedAt: Instant
)

interface RoutingProvider {
    val id: String
    suspend fun route(request: RouteRequest): RouteEstimate
}
