# NerdFM consolidated build — merge and verification audit

## What this archive contains

This is a consolidated working tree assembled from the supplied milestone archives:

- M1/M2 core and medical/finance/loan systems from the supplied M2 medical archive.
- M3 analytics module and analytics tests from the supplied M3 analytics archive.
- M6 people, environment, calendar, generated faces and fictional flags from the supplied M6 faces/flags archive.
- M7/M8 navigation model, player query/browser modules, notification and diagnostics modules, performance monitor, save recovery/migration modules, UI support components, and related regression tests from the supplied M7/M8 archive.
- CI workflow and build configuration from the M7/M8 archive.

## Important merge caveat

The source archives are not all successive commits of one repository. They contain divergent versions of shared files such as `Game.kt`, `MainActivity.kt`, `MatchUi.kt`, `Menu.kt`, `Persistence.kt`, `Shell.kt`, `World.kt`, `WorldGen.kt`, `Ecosystem.kt`, and `WorldIntegrity.kt`. A file-by-file overlay cannot safely guarantee every feature is wired together. This archive preserves the M6 faces/flags UI and model integration while adding M7/M8 modules and tests, but some M7/M8 UI modules may require integration into the active navigation/screen files. Please treat this as a merge candidate, not a release-certified APK source.

## Checks actually performed in this environment

- VERIFIED: All supplied ZIPs could be listed and extracted; this archive was assembled from the provided project trees.
- VERIFIED: Kotlin source inventory is 62 production Kotlin files and 41 test Kotlin files.
- VERIFIED: Line count in production Kotlin files is 14,610; test Kotlin files total 5,475 lines. These counts include blank lines and comments.
- VERIFIED: Archive integrity check passed after packaging (see delivery summary).
- UNVERIFIED: Android Gradle build and full JUnit suite. No Gradle executable or Gradle compiler libraries are installed in this environment. `tools/jvm-check.sh` was attempted and exited with status 2 because it could not locate a Gradle library directory containing `kotlin-compiler-embeddable`.
- UNVERIFIED: UI compile/type-check, because the offline UI check depends on project tooling and a successful base compile.
- UNVERIFIED: Full-season soak tests, save migration matrix, phone UI tests, and performance targets.

## Release blockers before calling the app complete

1. Run `gradle testDebugUnitTest assembleDebug` in GitHub Actions with the Android SDK installed.
2. Resolve any duplicate/conflicting API references caused by milestone archive divergence.
3. Confirm M7/M8 navigation, notifications, player browser, save recovery, and diagnostics are reachable from the active Compose screens.
4. Run migration tests against real legacy save fixtures and confirm the version-1/version-2 migration contract against the actual serialization model.
5. Run seeded multi-season soak tests and inspect invariants and performance measurements.
6. Perform an Android device smoke test covering new career, match, week advance, transfer, save, restart, and restore.

## FM26 comparison and licensing

NerdFM is a fictional-data football-management game. Comparisons with Football Manager 26 should be feature-level only (e.g. squad management, tactics, transfers, match presentation, player development and analytics). This archive does not claim parity with FM26's proprietary simulation, data, UI, or licensing. Do not copy proprietary assets, real player/club databases, or source code. Keep generated names, clubs, badges and flags fictional unless appropriately licensed data is deliberately added.
