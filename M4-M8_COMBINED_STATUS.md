# NerdFM M4–M8 combined pass — status

This package adds a reusable, Android-free world-integrity audit and unit tests as release-hardening groundwork. It does **not** claim that the full M4–M8 roadmap is complete.

## Roadmap coverage
- M4 Transfers and finance: existing transfer flows retained; advanced scouting knowledge, loans, clauses, agent fees, negotiation rounds, sell-on/release clauses, FFP, sponsors, stadium/ticket pricing and AI budget balance still require implementation and integration.
- M5 Competitions: existing league/fixture model retained; cups, continental/international competitions, congestion handling and player calendar still require implementation.
- M6 Development: existing training/coaches/injury systems retained; academy intake, expanded medical system, retraining and season-level development balance still require implementation.
- M7 UX: existing Compose screens retained; navigation/search/comparison/tutorial/accessibility and empty-data audit still require a dedicated pass.
- M8 Release hardening: added `WorldIntegrity` diagnostics and regression tests. 30-season soak, old-save migration matrix, device performance and release checklist remain unverified.

## New code
`WorldIntegrity.inspect(World)` reports structural problems without mutating the career. `requireValid(World)` provides a fail-fast assertion for headless/soak tests. It validates player identity and attributes, club ownership and membership, league references, and fixture teams/rounds/scores.

## Verification
- Static source review only unless the build workflow is run successfully.
- Required CI command: `gradle testDebugUnitTest assembleDebug`.
- No claim is made that M4–M8 acceptance criteria have passed.
