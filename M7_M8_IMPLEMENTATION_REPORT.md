# M7 / M8 implementation report

**Verification status: NOT COMPILED, NOT RUN.** This work was written in an environment with no Kotlin compiler,
Gradle or Android SDK and no network. The only checks performed were reading the real model code and a bracket-balance
lint of every .kt file. Run `gradle testDebugUnitTest assembleDebug` (or `tools/jvm-check.sh`) before trusting any of it.
Expect to fix some compile errors, especially in the Compose files.

## M8 (production ~1,020 lines, tests ~850)
- WorldIntegrity.kt: severities (ERROR/WARNING), per-code caps, new checks: duplicate IDs, finance, contract dates,
  duplicate/conflicting fixtures, round/chronology, standings recomputed from results, stats, offers, loans, clauses,
  sponsors. `inspect()` still returns errors only; `requireValid()` ignores warnings.
- SaveMigrations.kt: stepwise, injectable migrations; a failure leaves the file untouched (LoadResult.Unreadable).
- Persistence.kt: refactored into snapshot()/saveSnapshot()/writeVerified(); readFile() is internal.
- SaveRecovery.kt: SaveInspector (read-only file diagnosis), SaveRecovery (auto-restore only for missing/corrupt main),
  SaveCoordinator (world lock + audit + snapshot + write; never refuses a save over an audit finding).
- Diagnostics.kt / PerfMonitor.kt: reproducible failure reports (seed, versions, season/week, operation, findings,
  path/e-mail scrubbing), state fingerprint, 2 s / 1 s budgets, main-thread violation counter.
- DiagnosticsUi.kt + Shell/Menu/MainActivity hooks: on-device timings, audit button, copy report.
- Tests: extended integrity, 7-row save matrix, diagnostics/perf, headless runner, soak/determinism.
- CI: .github/workflows/soak.yml (30 seasons x 3 careers, nightly/manual), RELEASE_CHECKLIST.md.

## M7 (production ~1,180 lines, tests ~670)
- PlayerQuery.kt: accent-insensitive search, combinable filters, stable sorting, empty-state messages, comparison.
- NavigationModel.kt: destinations, bounded duplicate-free back stack, unsaved-change guard, encode/restore.
- Notifications.kt: actionable, read-only notices (offers, injuries, promises, contracts, board, finance, fixtures).
- UiSupport.kt: settings store (outside the save format), tutorial state, duplicate-tap guard, WCAG contrast audit.
- Compose: PlayersScreen (search/filter/sort/compare/profile with six sections), HomeScreen dashboard,
  notification cards, tutorial banner, empty/error/loading states, 48 dp targets, content descriptions, BackHandler.

## Deliberate deviations / not done
- No bottom navigation bar: the app is locked to sensorLandscape, so the existing left rail is regrouped into the six
  primary destinations plus "More". Change this if you want a bottom bar.
- Not verified: Compose rendering, TalkBack, font-scale layouts, on-device timings, a 30-season soak, CI runs.
- Buttons/segments are now 48 dp tall, which makes the landscape top bar taller; check small phones.
- Progress for tutorial/last screen lives in ui-settings.properties, not the career save (no save-version bump).
- Sort-by-"Overall/Potential" uses fogged values for unscouted players; potential sorts by true PA.
