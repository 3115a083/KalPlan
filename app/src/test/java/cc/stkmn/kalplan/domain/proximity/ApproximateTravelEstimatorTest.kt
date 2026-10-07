package cc.stkmn.kalplan.domain.proximity

import cc.stkmn.kalplan.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproximateTravelEstimatorTest {
    private val estimator = ApproximateTravelEstimator()

    private val dortmund = GeoPoint(51.5136, 7.4653)
    private val bochum = GeoPoint(51.4818, 7.2162)
    private val euskirchen = GeoPoint(50.6606, 6.7872)

    @Test
    fun nearbyCityProducesShorterEstimateThanDistantCity() {
        val bochumEstimate = estimator.estimate(dortmund, bochum)
        val euskirchenEstimate = estimator.estimate(dortmund, euskirchen)

        assertTrue(bochumEstimate.straightLineKm < euskirchenEstimate.straightLineKm)
        assertTrue(bochumEstimate.estimatedMinutesMax < euskirchenEstimate.estimatedMinutesMax)
    }

    @Test
    fun ampleGapIsLikelyFeasible() {
        val estimate = estimator.estimate(dortmund, bochum)
        val assessment = estimator.assess(
            availableMinutes = estimate.estimatedMinutesMax + 20,
            estimate = estimate,
            bufferMinutes = 15
        )

        assertEquals(PreliminaryTravelAssessment.LIKELY_FEASIBLE, assessment)
    }

    @Test
    fun tooSmallGapIsLikelyConflict() {
        val estimate = estimator.estimate(dortmund, euskirchen)
        val assessment = estimator.assess(
            availableMinutes = 30,
            estimate = estimate,
            bufferMinutes = 15
        )

        assertEquals(PreliminaryTravelAssessment.LIKELY_CONFLICT, assessment)
    }
}
