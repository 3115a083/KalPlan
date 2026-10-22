# Recognition warning correction, 0.7.1-dev

A valid mail with `Datum: 14.12.2026` and `Uhrzeit: 14:00-15:00` could display an invalid-date warning because the numeric date parser also read `00-15` inside the clock range. Numeric date candidates overlapping a colon-based or explicitly marked clock range are now excluded. Unmarked hyphenated dates remain eligible; genuinely invalid dates still report an error.

Free text without an optional duration no longer generates `duration_unparsed`. An explicit profile duration value is still validated, including malformed values. Recognized free-text durations retain their previous behavior. This does not broaden duration inference or change how competing appointment blocks are handled.

Regression coverage includes colon/dash/marked-dot clock ranges, valid hyphenated date alternatives, a genuine invalid date, absent optional duration, recognized explicit duration and malformed configured duration. The existing profile device flow additionally asserts that the two spurious warnings are absent.

Validation for this new correction is pending. Existing 0.7.0 proof is recorded separately in `REVIEW_0.7.0.md`.
