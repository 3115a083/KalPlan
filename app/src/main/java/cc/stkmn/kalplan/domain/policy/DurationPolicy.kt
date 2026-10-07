package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.core.KalPlanDefaults
import cc.stkmn.kalplan.domain.model.RequestLabel

enum class DurationSource {
    EXTRACTED,
    PROFILE_OVERRIDE,
    LABEL_OVERRIDE,
    GLOBAL_DEFAULT
}

data class DurationResolution(
    val minutes: Int,
    val source: DurationSource,
    val assumed: Boolean,
    val labelId: String? = null
)

class DurationPolicy(
    private val globalDefaultMinutes: Int = KalPlanDefaults.GLOBAL_DEFAULT_DURATION_MINUTES
) {
    fun resolve(
        extractedDurationMinutes: Int?,
        profileOverrideMinutes: Int?,
        labels: List<RequestLabel>
    ): DurationResolution {
        extractedDurationMinutes
            ?.takeIf { it > 0 }
            ?.let {
                return DurationResolution(
                    minutes = it,
                    source = DurationSource.EXTRACTED,
                    assumed = false
                )
            }

        profileOverrideMinutes
            ?.takeIf { it > 0 }
            ?.let {
                return DurationResolution(
                    minutes = it,
                    source = DurationSource.PROFILE_OVERRIDE,
                    assumed = true
                )
            }

        val label = labels
            .filter { (it.defaultDurationMinutes ?: 0) > 0 }
            .maxWithOrNull(
                compareBy<RequestLabel> { it.priority }
                    .thenBy { it.defaultDurationMinutes ?: 0 }
            )

        if (label != null) {
            return DurationResolution(
                minutes = requireNotNull(label.defaultDurationMinutes),
                source = DurationSource.LABEL_OVERRIDE,
                assumed = true,
                labelId = label.id
            )
        }

        return DurationResolution(
            minutes = globalDefaultMinutes,
            source = DurationSource.GLOBAL_DEFAULT,
            assumed = true
        )
    }
}
