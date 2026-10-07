package cc.stkmn.kalplan.domain.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class PlaceQuery(
    val countryCode: String = "DE",
    val postalCode: String? = null,
    val placeName: String? = null,
    val region: String? = null
)

enum class PlaceResolutionSource {
    POSTAL_CODE_CENTROID,
    CITY_CENTROID,
    MANUAL
}

data class PlaceResolution(
    val point: GeoPoint,
    val displayName: String,
    val source: PlaceResolutionSource,
    val sourceAccuracy: Int? = null
)
