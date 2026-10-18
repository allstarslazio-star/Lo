# NerdFM release checklist (M8)

Nothing on this list is checked by CI. Tick each box on a **real mid-range Android phone** and attach the
diagnostics text from *Diagnostics > Copy report* to the release notes. Unticked boxes mean "not verified".

## 0. Automated gates (must be green first)
- [ ] `gradle testDebugUnitTest assembleDebug` is green in the *Build and test NerdFM* workflow.
- [ ] The *Long-season soak* workflow is green for 30 seasons x 3 careers (artifact `NerdFM-soak-reports`).
- [ ] `WorldIntegrity` reports no errors in the soak summary.

## 1. Install and first run
- [ ] Fresh install of the debug/release APK succeeds; app starts without a crash.
- [ ] Device model and Android version recorded: ____________________
- [ ] Update-install over the previous build keeps the existing career (Continue Career works).

## 2. Career flow
- [ ] New Career: pick a club, start; the tutorial appears and can be skipped.
- [ ] Play a match, advance 5 weeks, play another match.
- [ ] Save & quit, force-stop the app, relaunch, Continue Career: same week, same table, same squad.
- [ ] Rotate / split-screen / font scale 1.3x: no lost state, no clipped essentials (see accessibility below).

## 3. Save safety
- [ ] Advance a week, then kill the app while the "Advancing" spinner is shown; relaunch: career loads (previous week).
- [ ] Menu > Diagnostics shows main save *healthy* and a *healthy* backup after two saves.
- [ ] Airplane mode / low storage does not corrupt the save (write failure keeps the previous save).

## 4. Performance (read the numbers from Diagnostics > Timings)
- [ ] Week advance p95 <= 2000 ms (budget).   Measured: ______ ms
- [ ] Career save p95 <= 1000 ms (budget).    Measured: ______ ms
- [ ] Match simulation and career load: measured and reported.  Match: ______ ms   Load: ______ ms
- [ ] "Main-thread violations" is 0 after a full season.

## 5. Accessibility and UX (M7)
- [ ] TalkBack announces every nav item, the Advance button and the notification actions.
- [ ] All tap targets feel >= 48 dp; no double-advance when tapping Advance repeatedly.
- [ ] Empty states: search for "zzzz" in Players, filter to zero results, open an empty inbox.
- [ ] Back button returns through the screens you visited and never quits mid-career without confirmation.

## 6. Sign-off
- [ ] Known issues listed in the release notes.   Tester: ____________   Date: ____________
