# Changelog

## Phase 2c — persistence safety

- Added a non-destructive `ReadError` result for unexpected read failures; only incompatible saves and definite EOF/GZIP/object-stream corruption are moved aside. Menu copy for a transient read failure is “Couldn't read the save, try again.” **Implementation: VERIFIED by source inspection; tests NOT VERIFIED (Gradle not run).**
- Added detection and rename-based restoration for `career1.bak` when `career1.dat` is missing. **Implementation: VERIFIED by source inspection; UI test NOT VERIFIED.**
- Career retirement now archives by rename without deserializing, and its call site dispatches deletion to `Dispatchers.IO`. **Implementation: VERIFIED by source inspection; runtime behavior NOT VERIFIED.**
- Added whole-save timing separately from temporary-file read-back verification and tracked already-verified saves to avoid deserializing them again during overwrite. **Implementation: VERIFIED by source inspection; timing numbers NOT VERIFIED.**
- Added 4 persistence tests. **Execution/count in CI: NOT VERIFIED.** The 12 existing `PersistenceTest` test bodies were not intentionally edited; one existing timing test gained an assertion for the new whole-save timer.
