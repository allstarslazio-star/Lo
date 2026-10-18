# NerdFM M2 implementation report

Date: 2026-10-16

## Scope delivered

M2 introduces deterministic match-day weather and pitch conditions in the simulation core and exposes the conditions in the live match screen.

- Weather: clear, rain, windy, hot.
- Pitch: good, wet, heavy, poor.
- Stable fixture-seeded condition selection that does not consume or perturb the match engine's random stream.
- Conditions influence match circulation duration, shot quality, and injury risk.
- Match conditions are transient match state, not persisted career state, so the save schema and save version remain unchanged.
- Added tests for deterministic generation, valid multiplier bounds, coverage of all weather/pitch options across a seed sample, and unchanged neutral-condition multipliers.

## Verification

### VERIFIED

- `kotlinc app/src/main/java/com/nerdfm/game/{Models.kt,World.kt,WorldGen.kt,Names.kt,Lineups.kt,MatchConditions.kt,MatchEngine.kt,MatchTacticalAI.kt,MatchEngineDiagnostics.kt,SelfImprovingEngine.kt,Persistence.kt,Transfers.kt} -d /tmp/nerdfm-m2-core.jar` completed successfully. Kotlin emitted existing warnings about unused parameters and redundant null assertions; no compilation errors.
- `kotlinc app/src/main/java/com/nerdfm/game/{Models.kt,World.kt,WorldGen.kt,Names.kt,Lineups.kt,MatchConditions.kt,MatchEngine.kt,MatchTacticalAI.kt,MatchEngineDiagnostics.kt,SelfImprovingEngine.kt,Persistence.kt,Transfers.kt,Game.kt} -d /tmp/nerdfm-m2-game-core.jar` completed successfully, including fixture integration.
- A standalone Kotlin smoke test checked 500 seeds, deterministic equality, bounded multipliers, all four weather and all four pitch values appearing in the sample, and unchanged normal-condition multipliers. Output: `PASS: 500 deterministic condition seeds; weather=4/4 pitch=4/4; normal baseline unchanged`.

### STATIC-ONLY

- Reviewed UI insertion and the new JUnit tests.
- Confirmed no persisted model fields changed and no save-version bump is required for this feature.
- Researched open-source football-manager implementations for architecture/testing patterns. No third-party source code or assets were copied.

### UNVERIFIED

- `gradle testDebugUnitTest assembleDebug`: not run; Gradle and Android SDK are unavailable in this environment.
- Compose UI compilation, APK build, and GitHub Actions result.
- Device verification of the match-condition label and gameplay balance.
- Large batch analysis to measure condition-specific goal, pass, and injury distributions.

## Known risks / follow-up

- The condition multipliers are deliberately conservative starting coefficients and need batch calibration before being considered realistic.
- The weather/pitch feature is only the first coherent M2 slice. Role/duty-specific instructions, configurable set-piece routines, and spatial heat/pass maps are not claimed as delivered by this package.
- M1's full CI, ten-season, and on-device acceptance checks remain outstanding. This M2 package does not claim those checks passed.

## Open-source note

OpenFoot Manager (GPL-3.0) and other public football-management projects were reviewed for high-level architectural ideas such as keeping match simulation testable and separating deterministic batch checks from presentation. No source files were copied or translated; NerdFM's Kotlin implementation and save format remain independent. Any future direct source reuse must first be reviewed for licence compatibility.
