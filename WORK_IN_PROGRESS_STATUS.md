# Work in progress snapshot (18 Oct 2026)

Base: NerdFM-Final-Consolidated. Fixed so far (logic layer compiles offline; 368 of 371 tests pass offline):
- WorldIntegrity rebuilt from its tests (audit/Report/Severity/inspectAll, per-code caps).
- Persistence: snapshot()/saveSnapshot(), readFile/SAVE_NAME/BACKUP_NAME internal; migrations now run through SaveMigrations (one step per format 1..10).
- EcosystemState.analytics added; M3 Analytics hooks restored (providers, prune, repair, weekly cost, scouting reliability, Analytics tab, data-model line).
- NameForge: female double/hyphen surnames now gendered; wider Valdoria surname list.
- StaffTest: excludes the M4 language coach from the slot-sum check. SaveMigrationMatrixTest: chain length = CURRENT_SAVE_VERSION - 1.

STILL BROKEN / NOT DONE (CI will still fail until these are fixed):
1. (GLUE ADDED, UNCOMPILED) UI compile errors in HomeUi.kt, DiagnosticsUi.kt, PlayerBrowserUi.kt: the App object (MainActivity.kt) lacks members these screens use
   (focusPlayerId, navigate, tutorial, tutorialTick, notices, deviceLabel, diagnosticsText). NavigationModel/Notifications/UiSupport/Diagnostics hold the logic; App needs the glue.
2. NameForgeTest.namesAreVariedInEveryNation: 8 nations are below 540 distinct of 600 (519-537). I raised the syllable-surname share in NameForge.baseSurname from 0.55 to 0.40; this IS in the zip but NOT yet re-measured or re-tested.
3. GoldenBaselineTest (2 tests): engine gives ~83 shots/match vs the frozen 77.3 band. Decide: re-baseline the golden bands, or adopt the Phase 11 engine retune (about 26 shots) and re-baseline.
4. Not yet re-applied: Phase 11 work (day-by-day calendar, profile long-press, wage premium, trend arrow, per-90 stats, CI runner pin, R8). These live on branch next-profile-calendar.
5. Nothing here has been built with Gradle or run on a phone.

## Update (static edit, still never compiled)
- MainActivity.kt: App now has focusPlayerId, tutorial/tutorialTick, notices, diagnosticsText, deviceLabel, initSettings(), navigate(String) and navigate(NavTarget).
- Shell.kt: NAV and the screen switch now include Home, Players and Diagnostics; TutorialBanner is shown under the top bar.
- Items 2 (NameForge re-measure), 3 (GoldenBaseline decision) and 4 (Phase 11 port) are untouched. Expect further compile errors in CI.
