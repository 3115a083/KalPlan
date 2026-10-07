package cc.stkmn.kalplan.application

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.model.RequestLabel
import cc.stkmn.kalplan.domain.policy.DurationPolicy
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.extraction.*
import java.time.Instant
import java.time.ZoneId

object RequestFactory {
    fun create(id: String, sender: String, subject: String, body: String, received: Long, state: AppData, profile: ExtractionProfile? = null): StoredRequest {
        require(body.length <= 512_000)
        val input = ExtractionInput(sender.take(512), subject.take(2000), body, Instant.ofEpochMilli(received), ZoneId.systemDefault())
        val result = KalPlanExtractionPipeline().extract(input, profile)
        val disposition = ExtractionDispositionPolicy().evaluate(result)
        val labelNames = PlanningPolicy.labels(sender, subject, body, state.settings.labels)
        val labels = state.settings.labels.filter { it.name in labelNames }.map { RequestLabel(it.name, it.name, it.durationPriority, it.durationMinutes) }
        val candidates = result.candidates.map { c ->
            val duration = DurationPolicy(state.settings.defaultDuration).resolve(c.durationMinutes, profile?.defaultDurationMinutes, labels)
            val end = c.end?.toEpochMilli() ?: c.start?.toEpochMilli()?.plus(duration.minutes * 60_000L)
            val minutes = if (c.start != null && c.end != null) java.time.Duration.between(c.start, c.end).toMinutes().toInt() else duration.minutes
            StoredCandidate(c.start?.toEpochMilli(), end, minutes.coerceAtLeast(1), c.end == null && duration.assumed,
                duration.source.name, c.locationText.orEmpty(), c.mode.name, c.confidence, c.warnings, c.dateRelation.name)
        }
        return StoredRequest(id = id, sender = sender, subject = subject, body = body, receivedMillis = received,
            labels = labelNames, candidates = candidates, selectedCandidate = if (candidates.size == 1) 0 else null,
            unclear = disposition.disposition == ExtractionDisposition.UNCLEAR,
            issues = (result.issues.map { it.code } + disposition.reasons).distinct(),
            evidence = result.fields.values.map { "${it.key}: ${it.value} (${it.evidence.kind}, ${(it.evidence.confidence * 100).toInt()}%)" })
    }
}
