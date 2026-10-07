# Extraction profiles

KalPlan profiles are versioned data. They contain matchers, extractor rules and semantic field mappings. They do not contain executable code.

The implementation is inspired by the proven profile/rule approach in ShareParser, with KalPlan-specific changes for uncertainty and multiple appointment candidates.

## Processing order

1. normalize mail text.
2. apply profile matchers.
3. run structured extractor rules.
4. parse structured date/time/duration values.
5. use free-text heuristics for fields not covered by the profile.
6. classify online/onsite.
7. apply all matching label rules.
8. create one or more appointment candidates.
9. attach confidence, evidence and review issues.

Structured fields outrank free-text heuristics.

## Sources

Rules can read:
- combined subject + body.
- body.
- subject.
- sender.
- a previously extracted variable.

Extractor order therefore matters when one rule depends on another.

## Semantic fields

Current semantic mappings:
- DATE
- TIME
- END_TIME
- DURATION
- LOCATION
- ONLINE_OR_LOCATION
- ONLINE
- TITLE
- POSTAL_CODE
- CITY
- CUSTOM

## Multiple dates

KalPlan deliberately does not assume recurrence.

A single field such as:

~~~text
Datum: 14.12.2026 oder 16.12.2026
~~~

creates two candidates with relation ALTERNATIVE.

Separate profile fields are supported as well:

~~~text
Datum 1: 14.12.2026
Datum 2: 16.12.2026
~~~

The profile can set:
- AUTO
- ALTERNATIVE
- MULTIPLE_OPTIONS
- UNSPECIFIED

AUTO stays conservative when separate fields do not express a clear relation.

## Year handling

Missing year:
- may be inferred from the received date.
- may roll into the next year near year boundaries.
- remains marked as inferred.

Explicit year:
- is never silently changed.
- implausibly old/far-future years lower confidence.
- suspicious explicit years create a review issue.

## Guided rule creation

The guided-rule factory can turn a selected line or marked substring into an extractor.

Whitespace learned from an example is flexible. Unicode separator spaces commonly introduced by HTML mail clients are treated like normal whitespace.

## Normalization

Before parsing:
- CRLF/CR become LF.
- NBSP and common Unicode separator spaces become ordinary spaces.
- zero-width characters are removed.
- U+FFFC object replacement characters are removed.

This is important for structured HTML-derived mail text.

## Labels

Any number of label rules may match one request.

Rules may inspect:
- sender.
- subject.
- body.
- combined text.

Labels are returned together and sorted by priority. No single-category assumption exists.

## Free-text fallback

Current local heuristics cover:
- numeric dates.
- German and English named dates.
- relative terms such as heute/morgen/übermorgen and today/tomorrow.
- weekdays.
- time ranges.
- duration expressions.
- online meeting terms and common meeting links.
- street addresses.
- German postcode + city patterns.

No network request is needed for extraction.

The offline postcode/city index can later validate or enrich place-name candidates before routing.


## JSON import and export

Profiles can be encoded as human-readable JSON with `ExtractionProfileCodec`.

Before a decoded profile can be used, `ProfileValidator` checks:
- supported schema version.
- non-empty id and name.
- duplicate matcher ids.
- duplicate extractor ids.
- duplicate extractor keys.
- invalid matcher/extractor regex.
- invalid capture-group numbers.
- confidence range.
- references to variables that do not exist yet.

Unknown JSON fields are ignored for forward-compatible imports, while unsupported schema versions are rejected.

Transform objects use a `type` discriminator such as:
- `trim`
- `prefix`
- `suffix`
- `change_case`
- `regex_replace`

Import errors are returned as generic validation errors. Raw customer/profile JSON must not be copied into production logs.
