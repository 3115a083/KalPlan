package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.application.RequestFactory
import cc.stkmn.kalplan.data.AppData
import cc.stkmn.kalplan.data.LabelPolicy
import cc.stkmn.kalplan.data.Settings
import cc.stkmn.kalplan.domain.model.AppointmentMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ProfileWorkbenchTest {
    private val reference = Instant.parse("2026-10-19T08:00:00Z")
    private fun input(body: String) = ExtractionInput("dispatch@example.invalid", "Anfrage", body, reference, ZoneId.of("Europe/Berlin"))
    private fun field(key: String, anchor: String, semantic: SemanticField = SemanticField.CUSTOM) =
        ExtractorRule(key, key, semantic, "(.*)", selector = FieldSelector(anchor = anchor), displayName = key)
    private fun profile(vararg rules: ExtractorRule) = ExtractionProfile(id = "p", name = "Flexible mails", extractors = rules.toList())

    @Test fun emptyValueDoesNotConsumeNextFieldAndMissingRemainsDistinct() {
        val result = ProfileExtractionEngine().extract(input("PLZ:\nLeistungscode: Arbeit"), profile(field("postcode", "PLZ"), field("code", "Leistungscode"), field("absent", "Kennung")))
        assertEquals("", result.values["postcode"]?.value)
        assertEquals("Arbeit", result.values["code"]?.value)
        assertFalse(result.values.containsKey("absent"))
    }

    @Test fun fieldSelectionLearnsContextInsteadOfExampleAnswer() {
        val sample = "Bitte am Einsatzort Köln eintreffen."
        val a = sample.indexOf("Köln")
        val selector = FieldRuleBuilder.fromSelection(sample, a, a + 4, InputSource.BODY, "city")
        val result = ProfileExtractionEngine().extract(input("Bitte am Einsatzort Berlin eintreffen."), profile(field("city", "unused").copy(selector = selector)))
        assertEquals("Berlin", result.values["city"]?.value)
    }

    @Test fun anchorlessLineSelectionRequiresExplicitContext() {
        assertThrows(IllegalArgumentException::class.java) { FieldRuleBuilder.fromSelection("Köln", 0, 4, InputSource.BODY, "city") }
    }

    @Test fun boundedMultilineRegionGeneralizesAndStopsAtEndAnchor() {
        val sample = "Hinweise:\nErste Zeile\nZweite Zeile\nEnde\nDatum: morgen"
        val a = sample.indexOf("Erste")
        val b = sample.indexOf("\nEnde")
        val selector = FieldRuleBuilder.fromSelection(sample, a, b, InputSource.BODY, "notes")
        val result = ProfileExtractionEngine().extract(input("Hinweise:\nNeuer Text\nNoch mehr\nEnde\nDatum: heute"), profile(field("notes", "unused").copy(selector = selector)))
        assertEquals("Neuer Text\nNoch mehr", result.values["notes"]?.value)
    }

    @Test fun refinementKeepsPriorExamplesAndVerifiesCorrectedExpectedValues() {
        val p = profile(field("code", "Leistungscode")).copy(examples = listOf(
            ProfileExample("one", "Original", body = "Leistungscode: Arbeit", receivedMillis = reference.toEpochMilli(), expected = mapOf("code" to "Arbeit")),
            ProfileExample("two", "Other layout", body = "Kategorie = Amt", receivedMillis = reference.toEpochMilli(), expected = mapOf("code" to "Amt"))))
        // Only field checks matter here, so include fixed appointment fields in both local examples.
        val withAppointments = p.copy(examples = p.examples.map { it.copy(body = it.body + "\n19.10.2026 13:00-16:00") })
        assertFalse(ProfilePreview.checkExamples(withAppointments).all { it.passed })
        val refined = withAppointments.copy(extractors = listOf(p.extractors.single().copy(variants = listOf(FieldSelector(anchor = "Kategorie", separator = "=")))))
        assertTrue(ProfilePreview.checkExamples(refined).all { it.passed })
    }

    @Test fun contradictoryVariantsRequireReview() {
        val rule = field("code", "Leistungscode").copy(variants = listOf(FieldSelector(anchor = "Kategorie")))
        val result = ProfileExtractionEngine().extract(input("Leistungscode: Arbeit\nKategorie: Amt"), profile(rule))
        assertTrue(result.issues.any { it.code == "conflicting_field_matches" && it.severity == IssueSeverity.NEEDS_REVIEW })
    }

    @Test fun arbitraryFieldCanFeedLabelDescriptionAndTemplateTogether() {
        val rule = field("service", "Leistungscode").copy(displayName = "Tätigkeit", includeInDescription = true,
            labelMappings = listOf(FieldLabelMapping("Arbeit", "Arbeit"), FieldLabelMapping("Amt", "Amt")))
        val result = KalPlanExtractionPipeline().extract(input("Leistungscode: Arbeit\n19.10.2026 13:00-16:00\nKommentar: Amt"), profile(rule))
        assertEquals(listOf("Arbeit"), result.labels.map { it.name })
        assertEquals("Tätigkeit: Arbeit", result.description)
        assertEquals("Tätigkeit Arbeit", ProfileTemplate.render("Tätigkeit {feld.service}", result.fields.mapValues { it.value.value }))
    }

    @Test fun unknownLabelValueRequiresReviewWithoutCreatingLabel() {
        val result = KalPlanExtractionPipeline().extract(input("Leistungscode: Neu\n19.10.2026 13:00"), profile(field("service", "Leistungscode").copy(labelMappings = listOf(FieldLabelMapping("Arbeit", "Arbeit")))))
        assertTrue(result.labels.isEmpty())
        assertTrue(result.issues.any { it.code == "unmapped_label_value" })
    }

    @Test fun structuredModeValueWinsOverHeaderAndComment() {
        val p = profile(field("date", "Datum", SemanticField.DATE), field("start", "Startzeit", SemanticField.TIME),
            field("end", "Endzeit", SemanticField.END_TIME), field("mode", "Ort oder Online", SemanticField.ONLINE).copy(modeMappings = mapOf("Ort" to "ONSITE", "Online" to "ONLINE")),
            field("where", "Ort", SemanticField.LOCATION), field("postcode", "PLZ", SemanticField.POSTAL_CODE), field("street", "Straße", SemanticField.STREET))
        val result = KalPlanExtractionPipeline().extract(input("Datum: HEUTE! 19.10.26\nStartzeit: 13:00\nEndzeit: 16:00\nOrt oder Online: Ort\nOrt: Mönchengladbach\nPLZ:\nStraße:\nKommentar: Online morgen um 09:00"), p)
        val candidate = result.candidates.single()
        assertEquals(AppointmentMode.ONSITE, candidate.mode)
        assertEquals("Mönchengladbach", candidate.locationText)
        assertEquals(LocalTime.of(13, 0), candidate.localStartTime)
        assertEquals(LocalTime.of(16, 0), candidate.localEndTime)
        assertEquals(180L, java.time.Duration.between(candidate.start, candidate.end).toMinutes())
    }

    @Test fun missingProfileDateFallsBackButExplicitEmptyDateDoesNot() {
        val p = profile(field("date", "Datum", SemanticField.DATE))
        val missing = KalPlanExtractionPipeline().extract(input("Wir benötigen Sie am 19.10.2026 von 13:00 bis 16:00."), p)
        assertEquals(19, missing.candidates.single().localDate?.dayOfMonth)
        assertTrue(missing.fields.values.any { it.semantic == SemanticField.DATE && it.evidence.kind == EvidenceKind.HEURISTIC })
        val empty = KalPlanExtractionPipeline().extract(input("Datum:\nKommentar: morgen 13:00"), p)
        assertTrue(empty.candidates.isEmpty())
        assertEquals("", empty.fields["date"]?.value)
    }

    @Test fun fallbackCanBeExplicitlyDisabledForDependentField() {
        val p = profile(field("date", "Datum", SemanticField.DATE).copy(allowHeuristicFallback = false))
        val result = KalPlanExtractionPipeline().extract(input("Morgen 13:00"), p)
        assertTrue(result.candidates.isEmpty())
    }

    @Test fun tableColumnReadsSameNormalizedHtmlSourceAsPreview() {
        val normalizer = MailTextNormalizer()
        val body = normalizer.htmlToText("<table><tr><th>Code</th><th>Stadt</th></tr><tr><td>A</td><td>Köln</td></tr></table>")
        val p = profile(field("city", "unused").copy(selector = FieldSelector(FieldSelectorKind.TABLE_COLUMN, anchor = "Stadt", separator = "\t", column = 2)))
        assertEquals("Köln", KalPlanExtractionPipeline().extract(input(body), p).fields["city"]?.value)
    }

    @Test fun codecPreservesLegacyAndNewFieldConfiguration() {
        val codec = ExtractionProfileCodec()
        assertTrue(codec.decode("""{"schemaVersion":1,"id":"old","name":"Old","extractors":[{"id":"a","key":"x","regex":"(ok)"}]}""").isValid)
        val p = profile(field("service", "Code").copy(labelMappings = listOf(FieldLabelMapping("A", "Work")),
            variants = listOf(FieldSelector(anchor = "Category")), additionalSemantics = setOf(SemanticField.TITLE), includeInDescription = true))
        assertEquals(p, codec.decode(codec.encode(p)).profile)
        assertFalse(codec.decode(codec.encode(p.copy(schemaVersion = 99))).isValid)
    }

    @Test fun runtimeAutoSelectionUsesMatcherAndDoesNotChooseFirstOnConflict() {
        val p = profile(field("code", "Leistungscode")).copy(matchers = listOf(MatcherRule("sender", "dispatch@", InputSource.SENDER)))
        val state = AppData(settings = Settings(labels = listOf(LabelPolicy("Work"))), profiles = listOf(p))
        val request = RequestFactory.create("a", "dispatch@example.invalid", "Anfrage", "Leistungscode: A\n19.10.2026 13:00", reference.toEpochMilli(), state)
        assertEquals(p.id, request.extractionProfileId)
        assertEquals("A", request.extractedFields["code"])
        val conflict = RequestFactory.create("b", "dispatch@example.invalid", "Anfrage", "19.10.2026 13:00", reference.toEpochMilli(), state.copy(profiles = listOf(p, p.copy(id = "other"))))
        assertTrue(conflict.unclear)
        assertTrue("multiple_matching_profiles" in conflict.issues)
        assertNull(conflict.extractionProfileId)
    }

    @Test fun templateDoesNotRecursivelySubstituteMailContents() {
        assertEquals("{feld.b} / safe", ProfileTemplate.render("{feld.a} / {feld.b}", mapOf("a" to "{feld.b}", "b" to "safe")))
    }

    @Test fun fallbackTimeFromDifferentDateCannotCompleteStructuredDate() {
        val result = KalPlanExtractionPipeline().extract(input("Zieldatum: 19.10.2026\n\nAnderer Termin 20.10.2026 um 10:00"), profile(field("date", "Zieldatum", SemanticField.DATE)))
        assertTrue(result.issues.any { it.code == "fallback_context_conflict" })
        assertTrue(result.candidates.all { it.localStartTime == null })
    }

    @Test fun fallbackDateFromDifferentTimeCannotCompleteStructuredTime() {
        val result = KalPlanExtractionPipeline().extract(input("Beginn: 13:00\n\nAnderer Termin 20.10.2026 um 10:00"), profile(field("start", "Beginn", SemanticField.TIME)))
        assertTrue(result.issues.any { it.code == "fallback_context_conflict" })
        assertTrue(result.candidates.isEmpty())
    }

    @Test fun localReextractPreservesIdentityAndNeverOverridesManualOrActedOnRequest() {
        val p = profile(field("date", "Datum", SemanticField.DATE))
        val original = cc.stkmn.kalplan.data.StoredRequest("id", accountId = "account", folder = "INBOX", sender = "s@example.invalid", subject = "S",
            body = "Datum: 19.10.2026\n13:00", receivedMillis = reference.toEpochMilli(), travelMinutes = 99, routeCheckedMillis = 100L)
        val updated = RequestFactory.reextract(original, AppData(), p)
        assertEquals("account", updated.accountId)
        assertEquals("INBOX", updated.folder)
        assertEquals(original.body, updated.body)
        assertEquals("p", updated.extractionProfileId)
        assertNull(updated.routeCheckedMillis)
        assertThrows(IllegalArgumentException::class.java) { RequestFactory.reextract(original.copy(manual = true), AppData(), p) }
        assertThrows(IllegalArgumentException::class.java) { RequestFactory.reextract(original.copy(status = "WAITING", replyId = "mail"), AppData(), p) }
    }

    @Test fun meetingModeWordInCombinedFieldDoesNotBecomePhysicalAddress() {
        val result = KalPlanExtractionPipeline().extract(input("Art: Ort\nDatum: 19.10.2026\nZeit: 13:00"),
            profile(field("mode", "Art", SemanticField.ONLINE_OR_LOCATION), field("date", "Datum", SemanticField.DATE), field("start", "Zeit", SemanticField.TIME)))
        assertEquals(AppointmentMode.ONSITE, result.candidates.single().mode)
        assertNull(result.candidates.single().locationText)
    }

    @Test fun knownEndTimeCannotBeGuessedAsMissingStart() {
        val p = profile(field("date", "Datum", SemanticField.DATE), field("end", "Endzeit", SemanticField.END_TIME))
        val missingStart = KalPlanExtractionPipeline().extract(input("Datum: 19.10.2026\nEndzeit: 16:00"), p)
        assertTrue(missingStart.candidates.all { it.localStartTime == null })
        val foundStart = KalPlanExtractionPipeline().extract(input("Datum: 19.10.2026\nBeginn wäre um 13:00.\nEndzeit: 16:00"), p)
        assertEquals(LocalTime.of(13, 0), foundStart.candidates.single().localStartTime)
        assertEquals(LocalTime.of(16, 0), foundStart.candidates.single().localEndTime)
    }

    @Test fun onlineModeWordIsNotAHeuristicCity() {
        val result = KalPlanExtractionPipeline().extract(input("Datum: 19.10.2026\nUhrzeit: 13:00-16:00\nOrt: Online"), profile())
        assertEquals(AppointmentMode.ONLINE, result.candidates.single().mode)
        assertNull(result.candidates.single().locationText)
        assertTrue(result.issues.none { it.code == "uncertain_heuristic_location" })
    }

    @Test fun selectionFromRawDraftUsesNormalizedOffsetsWithoutChangingTheDraft() {
        val draft = "\u200BBeschriftung:\u00a0\u00a0Köln\r\nNächste Zeile"
        val a = draft.indexOf("Köln")
        val selector = FieldRuleBuilder.fromSelection(draft, a, a + 4, InputSource.BODY, "city")
        val result = KalPlanExtractionPipeline().extract(input("Beschriftung: Berlin\nNächste Zeile"), profile(field("city", "unused").copy(selector = selector)))
        assertEquals("Berlin", result.fields["city"]?.value)
        assertTrue(draft.startsWith("\u200B"))
    }
}
