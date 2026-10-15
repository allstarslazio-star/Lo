# Phase 4 status (work in progress, branch phase-4)

## Tuned values (MatchEngine.kt, object K)
TEMPO 1.9 (new), LONG 0.02, GOAL 0.63, YEL 0.16, RED 0.0008, REPEAT 0.25 (also applied to
foulAgainst/offBallFouls), MAX_TOTAL_GOALS 8 guardrail.
Test fix: realism test calls applyToPlayers before checking ratings (raw ratings are unclamped by design).

## Verified locally (Kotlin 2.0.21 compiled directly, standalone driver mirroring MatchRealismReportTest; NOT Gradle/Android/JUnit)
Engine sources compile; 1000 matches per range, GOAL=0.63:

| Seeds | Goals | Yellow | Red | Home / Draw / Away | Shots | 8+ goals | Max |
|---|---|---|---|---|---|---|---|
| 3000000.. | 2.814 | 4.121 | 0.146 | 36.8 / 26.6 / 36.6 | 45.3 | 10 | 8 |
| 3100000.. | 2.792 | 3.963 | 0.134 | 37.9 / 26.3 / 35.8 | 45.8 | 11 | 8 |
| 4000000.. | 2.741 | 3.924 | 0.124 | 37.2 / 24.9 / 37.9 | 45.4 | 8 | 8 |
| 5000000.. | 2.828 | 4.091 | 0.126 | 35.8 / 27.2 / 37.0 | 45.6 | 11 | 8 |

Targets: goals 2.75, yellow 4.05, red 0.126, draw 27.4%. Baseline before Phase 4 was 3.24 / 6.23 / 0.738.
The JUnit suite was not re-run in this step (no JUnit available offline); run it in CI.

## NOT done / next
1. Push branch phase-4, run Build APK, paste the "Print realism report" step output (CI runs the real tests).
2. Still open: shots ~45/match (engine guardrail 12-34; no sourced target), home/away win rate targets UNVERIFIED
   (sim shows ~37/37, draws ~26%, slightly below 27.4% target), penalties convert at ~50% (clamp in penalty(); not touched),
   8+ goal matches ~1% (capped at 8 by guardrail).
3. Then Phase 5 (day-based transfer window; needs saveVersion plan approval).
