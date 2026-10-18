# NerdFM M1 build and match-engine calibration report

Date: 2026-10-16

## What changed

- Added a reproducible calibration entry point that runs the actual `MatchEngine` against newly generated fictional teams, with a default of 5,000 matches and deterministic seed range.
- Extended batch output to report home possession, yellow/red cards, maximum goals scored by one team, goals, shots, xG, result rates, and audit warnings/errors.
- Added a 5,000-match regression test for the requested scoreline guardrails and a smaller determinism test.
- Tuned the match chance-generation/home-advantage constants based on a before/after 5,000-match run.
- Updated GitHub Actions to run on pull requests as well as pushes/manual runs, execute `gradle testDebugUnitTest assembleDebug`, summarize JUnit results in the job summary, and upload XML/HTML reports and the debug APK.

## Calibration results (real engine, seeded synthetic teams)

| Metric | Before | After | Target | Result |
|---|---:|---:|---:|---|
| Matches | 5,000 | 5,000 | 5,000+ | PASS |
| Goals per match | 3.20 | 2.56 | 2.50–2.90 | PASS |
| Home wins | 40.3% | 45.2% | 43–47% | PASS |
| Draws | 21.4% | 24.5% | 23–27% | PASS |
| Away wins | 38.3% | 30.3% | informational | — |
| Maximum goals by one team | 8 | 9 | no team above 9 | PASS |
| Shots per match | 97.74 | 88.85 | diagnostic | INVESTIGATE |
| Total xG per match | 9.85 | 8.91 | diagnostic | INVESTIGATE |
| Home possession | not reported | 51.1% mean | 0–100% | PASS |
| Yellow cards per match | not reported | 6.22 | diagnostic | — |
| Red cards per match | not reported | 0.683 | diagnostic | INVESTIGATE |
| Audit errors | 0 | 0 | 0 | PASS |
| Audit warnings | 0 | 0 | 0 | PASS |

The before/after numbers come from actual runs of `MatchCalibrationHarness.run()` compiled with `kotlinc` and executed on the sandbox JVM. The final sample's total-goals range was 0–10, while the highest individual team score was 9. If the intended requirement is that the *combined match total* must never exceed 9, this sample does not meet that stricter interpretation.

The shots and xG averages are suspiciously high relative to the scoreline and are deliberately not described as calibrated. Red-card frequency also deserves review. The M1 scoreline guardrails pass, but this does not establish full football realism.

## Verification status

### VERIFIED

- Compiled the simulation/persistence core with `kotlinc` successfully. Command:
  `kotlinc app/src/main/java/com/nerdfm/game/{Models.kt,World.kt,WorldGen.kt,Names.kt,Lineups.kt,MatchEngine.kt,MatchTacticalAI.kt,MatchEngineDiagnostics.kt,SelfImprovingEngine.kt,Persistence.kt,Transfers.kt} -d /tmp/nerdfm-core-check.jar`
- Ran the 5,000-match baseline and tuned campaigns using the actual match engine; results are recorded above.
- The 5,000-match tuned campaign reported zero match-audit errors and zero warnings.
- Repeated deterministic calibration smoke test design is added to unit tests; the actual Gradle/JUnit execution is not verified here.

### STATIC-ONLY

- Reviewed the GitHub Actions workflow and Gradle task/report paths.
- Reviewed match engine constants and the diagnostics path.
- Reviewed the test additions and source diff for consistency.

### UNVERIFIED

- Official Android Gradle command `gradle testDebugUnitTest assembleDebug --no-daemon --stacktrace`: Gradle and Android SDK are not installed in this environment.
- GitHub Actions run and uploaded APK.
- Full JUnit suite in Gradle.
- Ten full headless career seasons and all financial/squad/age invariants.
- Weekly advance under 2 seconds and full save under 1 second on a mid-range phone.
- On-device UI, save/restore, lineup drag-and-drop and manual playthrough.

## Manual phone checklist (pending APK)

1. Install debug APK and start a new career offline.
2. Advance a week and verify fixtures/results and league tables update once.
3. Open a match, play to full time, and verify score, shots, cards and possession display.
4. Change tactics during a match and verify the change affects the next phase without changing unrelated saved settings.
5. Save, force-close, relaunch, and load the career.
6. Test missing-primary-save/available-backup restore and verify the backup remains intact.
7. Drag lineup players, save/reload, and verify lineup order persists.
8. Check that no negative balances, empty starting XIs, duplicate lineup players, impossible ages or out-of-range attributes appear during a long playthrough.
9. Advance multiple weeks while checking UI responsiveness and confirm no heavy simulation runs on the main thread.

## Open-source reuse and licence note

This revision borrows architectural/testing ideas from the public [Openfoot Manager](https://github.com/openfootmanager/openfootmanager) documentation (separated simulation/reporting, seeded batch calibration and invariant testing). No Openfoot source files were copied into NerdFM. Openfoot Manager is GPL-3.0; importing or translating its source would require a separate licence-compatibility review. The independent Kotlin implementation preserves NerdFM's existing model and save format.

## Known issues / next step

- Investigate why shot and xG averages are high, and why red cards average 0.683 per fixture. Do not tune these blindly; inspect the underlying event accounting and add focused tests.
- Run the official CI workflow and confirm it is green before moving beyond M1.
- The ten-season, device-performance and on-device checks remain outstanding.
