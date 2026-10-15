# Phase 4 status (work in progress, branch phase-4)

## Verified locally (Kotlin 2.0.21 + JUnit, not Gradle/Android): all 31 tests pass, incl. realism test
Tuned values (MatchEngine.kt, object K): TEMPO 1.9 (new), LONG 0.02, GOAL 0.60, YEL 0.16, RED 0.0008,
REPEAT 0.25 (also applied to foulAgainst/offBallFouls), MAX_TOTAL_GOALS 8 guardrail.
Test fix: realism test now calls applyToPlayers before checking ratings (raw ratings are unclamped by design).

Seed 3000000..3000499 (tuning range): goals 2.762 (target 2.75), yellow 4.03 (4.05), red 0.140 (0.126),
draw 25.4% (27.4%), max goals 8, Poisson p: 0.23 / 0.73 / 0.08. Baseline was 3.24 / 6.23 / 0.738 / 14 matches with 8+.

Unseen ranges (1000 matches each, GOAL=0.60): goals 2.56-2.71 (avg ~2.63, a bit low), yellow ~3.98, red ~0.124.

## NOT done / next
1. Raise K.GOAL 0.60 -> 0.63 (goals ~2.63 on unseen seeds, target 2.75), re-validate on fresh seed ranges (3100000, 4000000, 5000000...).
2. Push branch phase-4, run Build APK, paste the "Print realism report" step output.
3. Still open: shots ~45/match (engine guardrail 12-34; no sourced target), home/away win rate targets UNVERIFIED,
   penalties convert at ~50% (clamp in penalty(); not touched).
4. Then Phase 5 (day-based transfer window; needs saveVersion plan approval).
