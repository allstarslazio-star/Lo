# Changelog

## Phase 2c — persistence safety

- Added a non-destructive `ReadError` result for unexpected read failures; only incompatible saves and definite EOF/GZIP/object-stream corruption are moved aside. Menu copy for a transient read failure is “Couldn't read the save, try again.” **Implementation: VERIFIED by source inspection; tests NOT VERIFIED (Gradle not run).**
- Added detection and rename-based restoration for `career1.bak` when `career1.dat` is missing. **Implementation: VERIFIED by source inspection; UI test NOT VERIFIED.**
- Career retirement now archives by rename without deserializing, and its call site dispatches deletion to `Dispatchers.IO`. **Implementation: VERIFIED by source inspection; runtime behavior NOT VERIFIED.**
- Added whole-save timing separately from temporary-file read-back verification and tracked already-verified saves to avoid deserializing them again during overwrite. **Implementation: VERIFIED by source inspection; timing numbers NOT VERIFIED.**
- Added 4 persistence tests. **Execution/count in CI: NOT VERIFIED.** The 12 existing `PersistenceTest` test bodies were not intentionally edited; one existing timing test gained an assertion for the new whole-save timer.

## Phase 3 — measure-only realism report

- Added a deterministic 500-fixture diagnostic test using seeds `3000000..3000499`, freshly generated world/squads per fixture, `MatchBatchRunner`, and `MatchBalanceEvaluator`. **Execution and all measured numbers: NOT VERIFIED (Gradle not run).**
- Report includes mean home/away/total goals, home/draw/away rates, yellow/red cards per match, fitted Poisson chi-square statistics and p-values with sparse-tail pooling, target deltas, and rating/goal-cap guardrails. Hard assertions do not assert realism. **NOT VERIFIED.**
- Added CI log streaming, failure-line extraction, realism-report printing, and build-log/report artifacts. **Workflow behavior: NOT VERIFIED (CI not run here).**
- Targets are user-supplied: goals/match 2.75 (1,045/380), draw rate 27.4% (104/380), decisive matches 72.6%, yellow cards/match 4.05 (single secondary source, koppafootball.com), red cards/match 0.126 (single secondary source, koppafootball.com); home/away win targets UNVERIFIED. Target values were not independently checked.
- No match-engine constants or match outcome logic were changed. **VERIFIED by diff inspection.**

## Phase 5 — calendar transfer windows and save migration

- Save-format bump plan: advance `World.saveVersion` via `Persistence.CURRENT_SAVE_VERSION` from 1 to 2; accept version-1 serialized `World` instances and migrate their version marker to 2 on load. New calendar values are derived from the existing `year`/`week`, so the migration preserves career progress and weekly fixture scheduling. Unknown versions remain incompatible. This plan and implementation are committed together.
- Transfer windows now use the calendar date derived from the existing weekly career clock: summer July–August and winter January; UI text includes days remaining and weekly processing can emit a deadline reminder. Match weeks/fixtures are unchanged.
- Added old-save migration and unsupported-version persistence tests. **NOT VERIFIED:** Gradle/JUnit/Android compilation cannot be run in this environment. Reference informed: handoff `03-phase-plan.md` (day-based dates, deadline warning, retain weekly schedule); no external real-world calendar source fetched.

## Phase 6 — contract negotiation

- Added one-off signing/renewal bonus negotiation alongside the existing weekly wage and 1–5 year contract terms. Bonuses are checked against available balance and deducted only when the deal succeeds; wage-budget enforcement remains intact.
- Player interest now has Interested / Undecided / Not interested states with an explanation in contract talks. Offer rejection and completed sales continue to remove offers immediately.
- Added focused interest-state tests. **NOT VERIFIED:** Gradle/JUnit/Android compilation not run. Reference informed: `05-wishlist.md` (real negotiation and visible interest); no external reference implementation copied.

## Phase 7 — scouting interest leads to bids

- Player records now persist the interested club and a 1–3 week countdown. When the countdown expires during a transfer window, the named club can submit a real bid and news identifies it as following up on scouting. Ordinary random bids are skipped while the player has a pending scouting interest.
- Player profile displays the interested club and expected timing. Added a focused countdown/state test. **NOT VERIFIED:** Gradle/JUnit/Android compilation not run. Reference informed: `05-wishlist.md` and `03-phase-plan.md` (scouting news should lead to bids).

## Phase 8 — squad and player presentation

- Live match substitution bench now displays the match rating (same clamped 3.0–10.0 presentation as the pitch) rather than overall ability. Player profiles show value, wage, stats, form trend (improving/declining/steady), and persisted scouting interest where applicable. Existing squad and transfer player rows already open the detailed player dialog when tapped; those paths are retained.
- Added a focused form-trend presentation test. **NOT VERIFIED:** Gradle/JUnit/Android compilation not run. Reference informed: `05-wishlist.md` (bench ratings, player profiles, value and development indicator).

## Phase 9 — financially bounded wage budget

- The Finance screen now clamps requested weekly wage caps to a revenue/balance-based board range, explains when a requested cap is adjusted, and displays an explicit overspend warning. Weekly processing also emits a deduplicated board-news warning while wages exceed budget; the existing confidence penalty remains.
- Added tests for positive, ordered bounds and tighter limits when the club is in deficit. **NOT VERIFIED:** Gradle/JUnit/Android compilation not run. Reference informed: `05-wishlist.md` (adjust wage cap when finances allow; warn board when over budget).

## Phase 10 — bounded shooting/pressing learning and tactical adaptation

- Q-learning now receives xG-residual rewards for shot outcomes and a bounded reward for pressing decisions during ball recovery; pressing preferences make a small bounded adjustment to transition behavior. Existing build-up learning remains unchanged. AI manager genomes gain a small counter-tactic bias based on aggregate human pressing, line, and tempo usage sampled across weekly advances.
- Added a deterministic 200-season learning/genome stress test (8,000 shooting/pressing updates), plus existing bounds tests remain in place. This is a learning-state stress test, **not** a 200-season full match simulation. **NOT VERIFIED:** Gradle/JUnit/Android compilation and runtime simulation not run. Reference informed: `06-code-review-notes.md` and `05-wishlist.md` (shooting/pressing Q-learning and adaptation to human tactics); no external engine code copied.

## Final review / verification status

- Reviewed the six phase commits and checked the complete diff for whitespace errors. **NOT VERIFIED:** Gradle/JUnit/Android compilation and runtime simulation were not run, as instructed. No claims are made about Kotlin/Compose compilation, unit-test results, APK assembly, match calibration, or full 200-season gameplay.

## Post-review fixes (phases 5-10)

- **Counter-tactic drift (Phase 10):** the bias against the human's most-used tactics was written into stored AI genomes every season, so it compounded (mean AI pressing gene reached ~1.9/2.0 after ~15 seasons against a low-pressing human, ~0.1 against a high-pressing one). It is now applied only to the tactics actually played. Regression test `CounterTacticDriftTest` fails on the old code (1.92) and passes now. **Verified offline (JVM).**
- **Bench display (Phase 8):** substitution bench shows OVR and match rating again; unused substitutes all start at a default 6.0 rating, so rating alone hid the information needed to choose a sub. **Compose UI change: NOT VERIFIED (not compiled).**
- **Signing bonus (Phase 6):** the bonus is now checked against the balance remaining after the transfer fee. **NOT VERIFIED by a dedicated test.**
