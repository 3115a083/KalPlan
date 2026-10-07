package cc.stkmn.kalplan.domain.proximity

import cc.stkmn.kalplan.domain.model.GeoPoint
import cc.stkmn.kalplan.domain.model.PlaceQuery
import cc.stkmn.kalplan.domain.model.PlaceResolution
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

interface OfflinePlaceIndex {
    fun resolve(query: PlaceQuery): PlaceResolution?
}

data class ApproximateTravelEstimate(
    val straightLineKm: Double,
    val estimatedRoadKmMin: Double,
    val estimatedRoadKmMax: Double,
    val estimatedMinutesMin: Int,
    val estimatedMinutesMax: Int
)

enum class PreliminaryTravelAssessment {
    LIKELY_FEASIBLE,
    POSSIBLY_FEASIBLE,
    LIKELY_CONFLICT
}

class ApproximateTravelEstimator {
    fun estimate(origin: GeoPoint, destination: GeoPoint): ApproximateTravelEstimate {
        val airKm = greatCircleKm(origin, destination)
        val roadMin = airKm * 1.08
        val roadMax = airKm * 1.30 + 3.0

        val minutes = when {
            airKm <= 8.0 -> timeRange(airKm, fastKmh = 38.0, slowKmh = 20.0, minOverhead = 5, maxOverhead = 12)
            airKm <= 35.0 -> timeRange(airKm, fastKmh = 60.0, slowKmh = 35.0, minOverhead = 8, maxOverhead = 15)
            airKm <= 100.0 -> timeRange(airKm, fastKmh = 80.0, slowKmh = 50.0, minOverhead = 10, maxOverhead = 20)
            airKm <= 250.0 -> timeRange(airKm, fastKmh = 95.0, slowKmh = 65.0, minOverhead = 12, maxOverhead = 25)
            else -> timeRange(airKm, fastKmh = 105.0, slowKmh = 75.0, minOverhead = 15, maxOverhead = 35)
        }

        return ApproximateTravelEstimate(
            straightLineKm = airKm,
            estimatedRoadKmMin = roadMin,
            estimatedRoadKmMax = roadMax,
            estimatedMinutesMin = minutes.first,
            estimatedMinutesMax = minutes.last
        )
    }

    fun assess(
        availableMinutes: Int,
        estimate: ApproximateTravelEstimate,
        bufferMinutes: Int
    ): PreliminaryTravelAssessment {
        val safeAvailable = availableMinutes.coerceAtLeast(0)
        val safeBuffer = bufferMinutes.coerceAtLeast(0)

        return when {
            safeAvailable < estimate.estimatedMinutesMin + safeBuffer ->
                PreliminaryTravelAssessment.LIKELY_CONFLICT
            safeAvailable >= estimate.estimatedMinutesMax + safeBuffer ->
                PreliminaryTravelAssessment.LIKELY_FEASIBLE
            else ->
                PreliminaryTravelAssessment.POSSIBLY_FEASIBLE
        }
    }

    private fun timeRange(
        distanceKm: Double,
        fastKmh: Double,
        slowKmh: Double,
        minOverhead: Int,
        maxOverhead: Int
    ): IntRange {
        val min = ceil(distanceKm / fastKmh * 60.0 + minOverhead).toInt().coerceAtLeast(1)
        val max = ceil(distanceKm / slowKmh * 60.0 + maxOverhead).toInt().coerceAtLeast(min)
        return min..max
    }

    private fun greatCircleKm(a: GeoPoint, b: GeoPoint): Double {
        val earthRadiusKm = 6371.0088
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val deltaLat = (b.latitude - a.latitude).toRadians()
        val deltaLon = (b.longitude - a.longitude).toRadians()

        val h = sin(deltaLat / 2.0).pow(2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0).pow(2)

        return 2.0 * earthRadiusKm * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}

class OfflineProximityService(
    private val placeIndex: OfflinePlaceIndex,
    private val estimator: ApproximateTravelEstimator = ApproximateTravelEstimator()
) {
    fun estimate(origin: PlaceQuery, destination: PlaceQuery): ApproximateTravelEstimate? {
        val originPlace = placeIndex.resolve(origin) ?: return null
        val destinationPlace = placeIndex.resolve(destination) ?: return null
        return estimator.estimate(originPlace.point, destinationPlace.point)
    }
}
