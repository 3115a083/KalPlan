package cc.stkmn.kalplan.infrastructure.place

import cc.stkmn.kalplan.domain.model.GeoPoint
import cc.stkmn.kalplan.domain.model.PlaceQuery
import cc.stkmn.kalplan.domain.model.PlaceResolution
import cc.stkmn.kalplan.domain.model.PlaceResolutionSource
import cc.stkmn.kalplan.domain.proximity.OfflinePlaceIndex
import java.text.Normalizer
import java.util.Locale

data class LocalPlaceRecord(
    val countryCode: String,
    val postalCode: String,
    val placeName: String,
    val region: String?,
    val latitude: Double,
    val longitude: Double,
    val sourceAccuracy: Int?
)

class TsvPlaceIndex private constructor(
    records: List<LocalPlaceRecord>
) : OfflinePlaceIndex {
    private val byPostalCode = records.groupBy { key(it.countryCode, it.postalCode) }
    private val byPlaceName = records.groupBy { cityKey(it.countryCode, it.placeName) }

    override fun resolve(query: PlaceQuery): PlaceResolution? {
        val country = query.countryCode.uppercase(Locale.ROOT)
        val postal = query.postalCode?.trim()?.takeIf { it.isNotEmpty() }
        val city = query.placeName?.trim()?.takeIf { it.isNotEmpty() }
        val region = query.region?.trim()?.takeIf { it.isNotEmpty() }

        if (postal != null) {
            val postalMatches = byPostalCode[key(country, postal)].orEmpty()
            choose(postalMatches, city, region)?.let {
                return it.toResolution(PlaceResolutionSource.POSTAL_CODE_CENTROID)
            }
        }

        if (city != null) {
            val cityMatches = byPlaceName[cityKey(country, city)].orEmpty()
            val narrowed = if (region == null) {
                cityMatches
            } else {
                cityMatches.filter { normalize(it.region.orEmpty()) == normalize(region) }
            }

            if (narrowed.size == 1) {
                return narrowed.single().toResolution(PlaceResolutionSource.CITY_CENTROID)
            }

            val uniquePoints = narrowed.distinctBy {
                "%.4f,%.4f".format(Locale.ROOT, it.latitude, it.longitude)
            }
            if (uniquePoints.size == 1) {
                return uniquePoints.single().toResolution(PlaceResolutionSource.CITY_CENTROID)
            }
        }

        return null
    }

    private fun choose(
        matches: List<LocalPlaceRecord>,
        city: String?,
        region: String?
    ): LocalPlaceRecord? {
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches.single()

        var narrowed = matches
        if (city != null) {
            narrowed = narrowed.filter { normalize(it.placeName) == normalize(city) }
        }
        if (region != null) {
            narrowed = narrowed.filter { normalize(it.region.orEmpty()) == normalize(region) }
        }

        return when (narrowed.size) {
            1 -> narrowed.single()
            else -> narrowed.maxByOrNull { it.sourceAccuracy ?: 0 }
                ?.takeIf { best ->
                    narrowed.count { (it.sourceAccuracy ?: 0) == (best.sourceAccuracy ?: 0) } == 1
                }
        }
    }

    private fun LocalPlaceRecord.toResolution(source: PlaceResolutionSource) =
        PlaceResolution(
            point = GeoPoint(latitude, longitude),
            displayName = listOf(postalCode, placeName).filter { it.isNotBlank() }.joinToString(" "),
            source = source,
            sourceAccuracy = sourceAccuracy
        )

    companion object {
        /**
         * Expected columns:
         * countryCode<TAB>postalCode<TAB>placeName<TAB>region<TAB>latitude<TAB>longitude<TAB>accuracy
         */
        fun parse(lines: Sequence<String>): TsvPlaceIndex {
            val records = lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull(::parseLine)
                .toList()
            return TsvPlaceIndex(records)
        }

        private fun parseLine(line: String): LocalPlaceRecord? {
            val p = line.split('\t')
            if (p.size < 6) return null

            val lat = p[4].toDoubleOrNull() ?: return null
            val lon = p[5].toDoubleOrNull() ?: return null

            return LocalPlaceRecord(
                countryCode = p[0].trim(),
                postalCode = p[1].trim(),
                placeName = p[2].trim(),
                region = p.getOrNull(3)?.trim()?.ifBlank { null },
                latitude = lat,
                longitude = lon,
                sourceAccuracy = p.getOrNull(6)?.toIntOrNull()
            )
        }

        private fun key(country: String, postal: String) =
            country.uppercase(Locale.ROOT) + "|" + postal.trim()

        private fun cityKey(country: String, city: String) =
            country.uppercase(Locale.ROOT) + "|" + normalize(city)

        private fun normalize(value: String): String =
            Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.GERMANY)
                .replace("ß", "ss")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
    }
}

private fun normalize(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.GERMANY)
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
