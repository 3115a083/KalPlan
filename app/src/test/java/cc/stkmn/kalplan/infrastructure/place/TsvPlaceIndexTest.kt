package cc.stkmn.kalplan.infrastructure.place

import cc.stkmn.kalplan.domain.model.PlaceQuery
import cc.stkmn.kalplan.domain.model.PlaceResolutionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TsvPlaceIndexTest {
    private val index = TsvPlaceIndex.parse(
        sequenceOf(
            "DE\t44135\tDortmund\tNordrhein-Westfalen\t51.5136\t7.4653\t6",
            "DE\t44787\tBochum\tNordrhein-Westfalen\t51.4818\t7.2162\t6",
            "DE\t53879\tEuskirchen\tNordrhein-Westfalen\t50.6606\t6.7872\t6",
            "DE\t12345\tNeustadt\tRegion A\t50.0000\t8.0000\t4",
            "DE\t54321\tNeustadt\tRegion B\t51.0000\t9.0000\t4"
        )
    )

    @Test
    fun exactPostalCodeResolvesOffline() {
        val result = index.resolve(PlaceQuery(postalCode = "44135"))

        assertNotNull(result)
        assertEquals(PlaceResolutionSource.POSTAL_CODE_CENTROID, result?.source)
        assertEquals("44135 Dortmund", result?.displayName)
    }

    @Test
    fun uniqueCityResolvesOffline() {
        val result = index.resolve(PlaceQuery(placeName = "Bochum"))

        assertNotNull(result)
        assertEquals(PlaceResolutionSource.CITY_CENTROID, result?.source)
    }

    @Test
    fun ambiguousCityWithoutRegionIsNotGuessed() {
        assertNull(index.resolve(PlaceQuery(placeName = "Neustadt")))
    }

    @Test
    fun ambiguousCityCanBeDisambiguatedByRegion() {
        val result = index.resolve(
            PlaceQuery(placeName = "Neustadt", region = "Region B")
        )

        assertNotNull(result)
        assertEquals(51.0, result?.point?.latitude ?: 0.0, 0.0001)
    }
}
