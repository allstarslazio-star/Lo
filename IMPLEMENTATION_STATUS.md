# NerdFM M1 implementation status

**Scope:** M1 only. Do not begin M2 until CI is confirmed green.

- [x] Add a deterministic 5,000-match calibration harness using the production match engine.
- [x] Report goals, shots, xG, home/draw/away percentages, possession, cards and score maximum.
- [x] Tune scoreline/home-advantage parameters to the specified initial guardrails in the measured sample.
- [x] Add seeded regression tests for repeatability and 5,000-match target ranges.
- [x] Configure CI for pull requests, JUnit report summary, report artifacts and debug APK artifact.
- [ ] Official `gradle testDebugUnitTest assembleDebug` green in GitHub Actions.
- [ ] Ten headless seasons with world/economy invariants.
- [ ] Mid-range Android phone performance and manual UI/save/restore checklist.
- [ ] Investigate high shot/xG and red-card averages before calling the engine fully calibrated.

See `BUILD_AND_TEST_REPORT.md` for exact measured results and evidence labels.


## M2 status (2026-10-16)

- [x] Add deterministic weather and pitch match conditions.
- [x] Apply conditions to shot quality, match circulation duration, and injury risk.
- [x] Show conditions in the live match UI.
- [x] Add deterministic/bounds regression tests.
- [x] Compile the simulation and Game integration with `kotlinc`; run 500-seed smoke test.
- [ ] Official Gradle unit tests and debug APK build.
- [ ] Android device verification and condition-specific balance calibration.

See `M2_IMPLEMENTATION_REPORT.md` for verification details and limitations.
