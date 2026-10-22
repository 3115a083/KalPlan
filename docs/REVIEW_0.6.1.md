# KalPlan 0.6.1-dev

- Main request view uses a swipe icon beside refresh; history lives in the menu. Swipe cards retain only their rounded card shadow.
- Automatic synchronization has seven selectable active weekdays. New settings start with all seven active. Missing fields in legacy JSON derive active days from paused weekdays and the weekend flag; explicit new selections take precedence.
- Daily quiet hours have an explicit optional switch. Existing complete legacy time ranges remain enabled on migration. Disabling the switch preserves the saved times and stops their pause effect. Time controls appear only while enabled.
- Enabled label price overrides treat blank or legacy-null monetary fields as zero and never inherit global flat fees, components or distance bands. Switching the entire override off uses global pricing. Explicit label flat fees remain combinable with hourly work.
- The existing profile editor now copies the original profile when saving, preserving unedited parse direction, multiple-date mode and reply templates. No new profile architecture or matcher semantics are introduced.
- Version code 8, version name 0.6.1-dev. Validation results belong in PR #8 after CI completes; no extra documentation-only commit is required.
