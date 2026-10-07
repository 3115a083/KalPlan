package cc.stkmn.kalplan.extraction

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ProfileDecodeResult(
    val profile: ExtractionProfile?,
    val validationErrors: List<ProfileValidationError>
) {
    val isValid: Boolean
        get() = profile != null && validationErrors.isEmpty()
}

class ExtractionProfileCodec(
    private val validator: ProfileValidator = ProfileValidator()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    fun encode(profile: ExtractionProfile): String = json.encodeToString(profile)

    fun decode(value: String): ProfileDecodeResult {
        val profile = try {
            json.decodeFromString<ExtractionProfile>(value)
        } catch (_: SerializationException) {
            return ProfileDecodeResult(
                profile = null,
                validationErrors = listOf(
                    ProfileValidationError(
                        code = "invalid_json",
                        message = "Profile JSON could not be decoded."
                    )
                )
            )
        } catch (_: IllegalArgumentException) {
            return ProfileDecodeResult(
                profile = null,
                validationErrors = listOf(
                    ProfileValidationError(
                        code = "invalid_json",
                        message = "Profile JSON could not be decoded."
                    )
                )
            )
        }

        val errors = validator.validate(profile)
        return ProfileDecodeResult(
            profile = profile.takeIf { errors.isEmpty() },
            validationErrors = errors
        )
    }
}
