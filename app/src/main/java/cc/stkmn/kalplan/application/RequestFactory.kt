package cc.stkmn.kalplan.application

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.model.RequestLabel
import cc.stkmn.kalplan.domain.policy.DurationPolicy
import cc.stkmn.kalplan.domain.policy.PlanningPolicy
import cc.stkmn.kalplan.extraction.*
import java.time.Instant
import java.time.ZoneId

object RequestFactory {
    /** Reparse changes local extraction only. Already acted-on or manually corrected requests stay intact. */
    fun reextract(request: StoredRequest, state: AppData, profile: ExtractionProfile): StoredRequest {
        require(request.pending && !request.manual && request.replyId == null && request.reservationEventId == null)
        val parsed = create(request.id, request.sender, request.subject, request.body, request.receivedMillis, state, profile)
        return request.copy(labels = parsed.labels, candidates = parsed.candidates, selectedCandidate = parsed.selectedCandidate,
            unclear = parsed.unclear, issues = parsed.issues, evidence = parsed.evidence,
            extractedFields = parsed.extractedFields, extractionProfileId = parsed.extractionProfileId,
            extractedDescription = parsed.extractedDescription, extractedTitle = parsed.extractedTitle,
            status = if (request.status == "UNCLEAR" && !parsed.unclear) "NEW" else request.status,
            travelMinutes = null, distanceKm = null, routeCheckedMillis = null, travelAfterMinutes = null, travelAfterCheckedMillis = null)
    }

    fun create(id: String, sender: String, subject: String, body: String, received: Long, state: AppData, profile: ExtractionProfile? = null): StoredRequest {
        require(body.length <= 512_000)
        val input = ExtractionInput(sender.take(512), subject.take(2000), body, Instant.ofEpochMilli(received), ZoneId.systemDefault())
        val matching = if (profile == null) ProfileExtractionEngine().matchingProfiles(MailTextNormalizer().normalize(input),
            state.profiles.filter { it.matchers.isNotEmpty() }) else emptyList()
        val selected = profile?.takeIf { it.enabled } ?: matching.singleOrNull()
        var result = KalPlanExtractionPipeline().extract(input, selected)
        val extraIssues = buildList {
            if (matching.size > 1) add(ExtractionIssue("multiple_matching_profiles", "Mehrere Profile passen. Profil gezielt auswählen.", IssueSeverity.NEEDS_REVIEW))
            if (profile != null && !profile.enabled) add(ExtractionIssue("assigned_profile_disabled", "Zugeordnetes Profil ist deaktiviert.", IssueSeverity.NEEDS_REVIEW))
            result.labels.filter { label -> state.settings.labels.none { it.name == label.name } }.forEach {
                add(ExtractionIssue("label_target_missing", "Zugeordnetes Label existiert nicht mehr.", IssueSeverity.NEEDS_REVIEW))
            }
        }
        result = result.copy(issues = result.issues + extraIssues)
        val disposition = ExtractionDispositionPolicy().evaluate(result)
        val labelNames = (PlanningPolicy.labels(sender, subject, body, state.settings.labels) + result.labels.map { it.name }
            .filter { name -> state.settings.labels.any { it.name == name } }).distinct()
        val labels = state.settings.labels.filter { it.name in labelNames }.map { RequestLabel(it.name, it.name, it.durationPriority, it.durationMinutes) }
        val candidates = result.candidates.map { c ->
            val duration = DurationPolicy(state.settings.defaultDuration).resolve(c.durationMinutes, selected?.defaultDurationMinutes, labels)
            val end = c.end?.toEpochMilli() ?: c.start?.toEpochMilli()?.plus(duration.minutes * 60_000L)
            val minutes = if (c.start != null && c.end != null) java.time.Duration.between(c.start, c.end).toMinutes().toInt() else duration.minutes
            StoredCandidate(c.start?.toEpochMilli(), end, minutes.coerceAtLeast(1), c.end == null && duration.assumed,
                duration.source.name, c.locationText.orEmpty(), c.mode.name, c.confidence, c.warnings, c.dateRelation.name)
        }
        return StoredRequest(id = id, sender = sender, subject = subject, body = body, receivedMillis = received,
            labels = labelNames, candidates = candidates, selectedCandidate = if (candidates.size == 1) 0 else null,
            unclear = disposition.disposition == ExtractionDisposition.UNCLEAR,
            issues = (result.issues.map { it.code } + disposition.reasons).distinct(),
            evidence = result.fields.values.map { "${it.key}: ${it.value} (${it.evidence.kind}, ${(it.evidence.confidence * 100).toInt()}%)" },
            extractionProfileId = selected?.id,
            extractedFields = result.fields.mapValues { it.value.value },
            extractedDescription = result.description,
            extractedTitle = result.fields.values.firstOrNull { it.semantic == SemanticField.TITLE || selected?.extractors?.firstOrNull { r -> r.key == it.key }?.additionalSemantics?.contains(SemanticField.TITLE) == true }?.value.orEmpty())
    }
}
