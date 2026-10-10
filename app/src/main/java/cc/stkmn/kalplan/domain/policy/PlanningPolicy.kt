package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

data class PriorityResult(val score: Int, val rank: Int, val reasons: List<String>, val stale: Boolean)
data class ValueResult(val totalCents: Long, val workCents: Long, val travelCents: Long, val distanceCents: Long, val flatCents: Long, val billedMinutes: Int)

object PlanningPolicy {
    fun labels(sender: String, subject: String, body: String, policies: List<LabelPolicy>): List<String> {
        return policies.filter { rule ->
            val text = when (rule.searchIn) {
                "SUBJECT" -> subject
                "BODY" -> body
                else -> "$subject\n$body"
            }.lowercase()
            val terms = rule.keywords.map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val termsMatch = when {
                terms.isEmpty() -> true
                rule.keywordMode == "ALL" -> terms.all(text::contains)
                else -> terms.any(text::contains)
            }
            termsMatch &&
                (rule.senderContains.isBlank() || sender.contains(rule.senderContains, true)) &&
                (terms.isNotEmpty() || rule.senderContains.isNotBlank())
        }.map { it.name }.distinct()
    }
    fun priority(request: StoredRequest, settings: Settings, now: Long = System.currentTimeMillis()): PriorityResult {
        val duration = request.candidate?.durationMinutes ?: request.candidates.firstOrNull()?.durationMinutes ?: 60
        val ageHours = ((now - request.receivedMillis).coerceAtLeast(0) / 3_600_000.0)
        val stale = ageHours >= settings.staleHours
        val reasons = mutableListOf("base:50", "duration:+${(duration / 30).coerceAtMost(20)}")
        var score = 50 + (duration / 30).coerceAtMost(20)
        settings.labels.filter { it.name in request.labels && request.labels.containsAll(it.requiredLabels) && it.excludedLabels.none { label -> label in request.labels } }.forEach { rule ->
            val delta = if (rule.shortThresholdMinutes != null && duration < rule.shortThresholdMinutes) rule.shortScore else rule.score
            score += delta; reasons += "${rule.name}:${if (delta >= 0) "+" else ""}$delta"
        }
        val agePenalty = (ageHours / settings.staleHours.coerceAtLeast(1) * 20).toInt().coerceAtMost(40)
        score -= agePenalty; reasons += "age:-$agePenalty"
        score = score.coerceIn(0, 100)
        return PriorityResult(score, when { score >= 80 -> 1; score >= 65 -> 2; score >= 45 -> 3; else -> 4 }, reasons, stale)
    }
    fun value(candidate: StoredCandidate, travelMinutes: Int?, distanceKm: Double?, settings: ValueSettings): ValueResult {
        require(candidate.durationMinutes in 1..10080)
        require(settings.billingStepMinutes in 1..1440)
        require(listOf(settings.workCentsPerHour, settings.travelCentsPerHour, settings.centsPerKm, settings.flatCents).all { it in 0..100_000_000 })
        val step = settings.billingStepMinutes
        val roundingMode = when (settings.roundingMode) {
            "DOWN" -> RoundingMode.FLOOR
            "NEAREST" -> RoundingMode.HALF_UP
            else -> if (settings.roundUp) RoundingMode.CEILING else RoundingMode.HALF_UP
        }
        val units = BigDecimal(candidate.durationMinutes).divide(BigDecimal(step), 0, roundingMode).toInt().coerceAtLeast(1)
        val billed = units * step
        val factor = if (settings.roundTrip) 2 else 1
        fun timeCents(minutes: Int, rate: Long) = BigDecimal(minutes).multiply(BigDecimal(rate)).divide(BigDecimal(60), 0, RoundingMode.HALF_UP).longValueExact()
        val work = timeCents(billed, settings.workCentsPerHour)
        val travel = timeCents((travelMinutes ?: 0).coerceIn(0, 10080) * factor, settings.travelCentsPerHour)
        val km = (distanceKm ?: 0.0).also { require(it.isFinite() && it >= 0 && it <= 100_000) } * factor
        val distance = BigDecimal.valueOf(km).multiply(BigDecimal(settings.centsPerKm)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        val band = if (distanceKm == null) 0L else settings.distanceBands.sortedBy { it.upToKm }.firstOrNull { km <= it.upToKm }?.cents ?: 0
        val flat = settings.flatCents + band
        return ValueResult(work + travel + distance + flat, work, travel, distance, flat, billed)
    }
    fun valueSettings(request: StoredRequest, settings: Settings): ValueSettings {
        val override = settings.labels.filter { it.name in request.labels && it.valueOverride != null }.maxByOrNull { it.score }?.valueOverride
        val base = settings.value
        val merged = if (override == null) base else base.copy(
            workCentsPerHour = override.workCentsPerHour ?: settings.value.workCentsPerHour,
            travelCentsPerHour = override.travelCentsPerHour ?: settings.value.travelCentsPerHour,
            centsPerKm = override.centsPerKm ?: settings.value.centsPerKm,
            flatCents = override.flatCents ?: settings.value.flatCents,
            flatFeePresetId = override.flatFeePresetId ?: settings.value.flatFeePresetId,
            billingStepMinutes = override.billingStepMinutes ?: settings.value.billingStepMinutes,
            roundUp = override.roundUp ?: settings.value.roundUp,
            roundingMode = override.roundingMode ?: settings.value.roundingMode,
            roundTrip = override.roundTrip ?: settings.value.roundTrip
        )
        val preset = settings.flatFeePresets.firstOrNull { it.id == merged.flatFeePresetId }
        return if (preset == null) merged else merged.copy(flatCents = preset.flatCents, distanceBands = preset.distanceBands)
    }
    fun syncPaused(settings: Settings, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val day = now.dayOfWeek.value
        if (day in settings.pausedWeekdays || settings.pauseWeekends && day >= 6) return true
        val from = runCatching { LocalDate.parse(settings.pauseFrom) }.getOrNull()
        val until = runCatching { LocalDate.parse(settings.pauseUntil) }.getOrNull()
        if (from != null && until != null && now.toLocalDate() in from..until) return true
        val quietFrom = runCatching { LocalTime.parse(settings.quietFrom) }.getOrNull()
        val quietUntil = runCatching { LocalTime.parse(settings.quietUntil) }.getOrNull()
        if (quietFrom != null && quietUntil != null) {
            val time = now.toLocalTime()
            if (quietFrom == quietUntil || quietFrom < quietUntil && time >= quietFrom && time < quietUntil ||
                quietFrom > quietUntil && (time >= quietFrom || time < quietUntil)) return true
        }
        return false
    }
}
