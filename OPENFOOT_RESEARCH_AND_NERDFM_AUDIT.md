# Openfoot Manager research and NerdFM engineering audit

**Prepared:** 2026-10-15  
**Scope:** Public Openfoot Manager repository metadata, architecture/match/game-system documentation and visible issue/PR summaries; static review of the supplied NerdFM Kotlin match engine; first isolated implementation improvement.  
**Important limitation:** This is not yet a complete source-file or Git-history audit of Openfoot Manager. The working environment could not resolve GitHub for a repository clone, and the Openfoot source ZIP/Git bundle was not supplied. No Openfoot source files were copied into NerdFM. This report labels repository facts separately from recommendations and local code findings.

## 1. Sources reviewed

- Repository and current tree: https://github.com/openfootmanager/openfootmanager
- Match simulation design: https://github.com/openfootmanager/openfootmanager/blob/develop/docs/MATCH_SIMULATION.md
- AI manager implementation (`ai.rs`, 641 lines in the rendered page): https://github.com/openfootmanager/openfootmanager/blob/develop/src-tauri/crates/engine/src/ai.rs
- Architecture: https://github.com/openfootmanager/openfootmanager/blob/develop/docs/ARCHITECTURE.md
- Game systems: https://github.com/openfootmanager/openfootmanager/blob/develop/docs/GAME_SYSTEMS.md
- Contributing and test commands: https://github.com/openfootmanager/openfootmanager/blob/develop/CONTRIBUTING.md
- Issues: https://github.com/openfootmanager/openfootmanager/issues
- Pull requests: https://github.com/openfootmanager/openfootmanager/pulls
- Licence: https://github.com/openfootmanager/openfootmanager/blob/develop/LICENSE.md

At the time of web review, GitHub showed the repository as a public Rust/TypeScript football manager under GPL-3.0, with thousands of commits, a modular Rust workspace, public issue/PR trackers and separate engine/domain/core/persistence responsibilities. GitHub's rendered counts can change as the project evolves; the numbers in this document are not intended as a permanent repository snapshot.

## 2. Openfoot architecture findings

### Match simulation

The public match-simulation document describes:

- A live, minute-by-minute engine that can accept substitutions and tactical commands.
- An instant whole-match engine used for developer tools and calibration rather than the normal active-competition path.
- A cheaper scoreline model for competitions outside the active simulation scope.
- A five-zone pitch abstraction and action-based progression, replacing an older, more granular event/transition model.
- A match report separated from raw events, with team and player statistics.
- A live-match state machine for halves, extra time and penalty shootouts.
- Fatigue and condition-adjusted player skills in live matches.
- A manager AI driven by observations, checkpoints, match events, personality and experience.
- Tests covering simulation invariants, score/stat consistency, seeded outcomes, team-strength effects, home advantage, tactics, fatigue, substitutions and match lifecycle.

**Transferable lesson:** use separate, testable layers for match state, action resolution, manager decisions, report generation and persistence. Keep a fast deterministic/seeded harness available for tuning, but do not assume an instant model is equivalent to the live model if the live model includes fatigue, substitutions and AI.

### Engine isolation and integration

The architecture document describes `engine` and `domain` as independent leaves. The core layer translates between domain entities and engine types; the engine does not directly depend on the broader game domain. It also documents backwards-compatible defaults for newer saved-game fields.

**Transferable lesson for NerdFM:** do not make the match simulator depend on Compose UI state. Prefer plain Kotlin simulation inputs and outputs, deterministic random seeds, and a single mapping boundary between the career/world model and match model. Keep save-file compatibility in mind whenever player, tactic or match state is extended.

### Other management systems

The public game-systems documentation covers day/turn processing, training, staff, player traits, league schedules, inbox/news, world generation, finances and transfers.

**Transferable lesson:** management systems need explicit rules and shared calculations. Player development, wage accounting, contract dates and team strength should not silently implement separate versions of the same rule in different screens or services.

## 3. Repository-history and issue-tracker lessons

The visible issue list at the time of review included examples such as:

- A test-infrastructure issue describing duplicated production rules and flaky tests.
- A documentation/tooling issue describing claims that did not match implementation behaviour.
- A schema issue describing the same package schema being maintained in multiple places and fields being silently ignored.
- A transfer issue about loan wages not respecting negotiated responsibility.
- A match-flow issue about a drawn league match incorrectly proceeding to extra time/penalties through one integration path.
- A world-generation issue about country/region fallback assigning African nations to Europe.

These are observations from issue titles/summaries, not proof that every issue remains unfixed in the current source. They are valuable as engineering warnings:

1. Test the real production rules instead of reimplementing them in test-only code.
2. Make documentation claims traceable to code and automated checks.
3. Avoid duplicating schemas and business rules.
4. Test competition rules at every entry point, not just the main UI flow.
5. Use explicit country/region mappings and validate generated data.
6. Treat transfers and finance as accounting systems with invariants, not just UI interactions.

A direct read of the public `ai.rs` source and its comments added more detail beyond the overview docs. The code explicitly rejects a per-minute “lottery” for whether managers notice a situation: it uses checkpoints plus immediate reactions to important events, and puts uncertainty in the close-call choice of substitution rather than whether the manager acts. It also documents a no-oscillation principle: issue a tactical target when the situation calls for it, but do not automatically issue the inverse command as soon as the trigger disappears. The implementation separates substitution decisions, tactical changes, score-response rules, and a shared cap for certain potentially under-priced tactical dials. These comments are valuable because they explain the failure mode the design is intended to prevent, not just what the code does.

A true commit-by-commit conclusion still requires the Git object history. The web-rendered current tree, source pages and issue summaries are not a substitute for checking every commit diff, linked tests, review comments and subsequent fixes.

## 4. Local NerdFM match-engine review

The supplied project uses Kotlin/Android and contains a large `MatchEngine.kt` with:

- Weighted ability vectors derived from 36 attributes.
- Role familiarity and positional contribution weights.
- Team-level attacking/defensive lane strengths.
- Build-up, midfield and final-third phases.
- Chance types, shot quality, expected-goals tracking, goalkeeper actions and rebounds.
- Set pieces, fouls, offsides, cards, injuries and substitutions.
- Fatigue, morale, form, sharpness, day-form and match ratings.
- A seeded `kotlin.random.Random` passed to the match engine.

This is a substantive foundation; a wholesale rewrite would add migration risk without evidence that it improves outcomes.

### Static findings to investigate

1. **A suspicious no-op in aerial chance quality.** `MatchEngine.kt` contains `q *= 1f - 0.05f * (def.eLine - 1) * 0f`. Because the final factor is multiplied by zero, the expression never changes `q`. It may be a deliberately disabled experiment or an accidental leftover. It should be resolved explicitly and tested, not silently assumed to be a bug.
2. **AI tactical adaptation is concentrated in `refresh()`.** Before this update, the AI mainly adjusted mentality for score, line for a lead, pressing for fatigue/red-card state and tempo for low condition. Effective width and passing remained the saved values, even when the scoreline called for a different approach.
3. **Audit tooling already exists in this supplied revision.** `MatchEngineDiagnostics.kt` checks post-match invariants, summarizes seeded batches and reports broad balance warnings. These tools need to be exercised in the Android/Gradle environment before they can be treated as validated.
4. **The project has no checked-in Gradle wrapper.** Its GitHub workflow installs Gradle 8.9. Local build verification requires a compatible Gradle/Android SDK environment or a successful CI run.

These are static observations. They are not a substitute for running seeded match batches and checking the distribution of goals, xG, shots, cards, possession and outcomes.

## 5. Implemented in this revision

### Match-aware AI tactical policy

Added `MatchTacticalAI.kt` and integrated it into `MatchEngine.refresh()`.

- A team behind late can use more direct passing and, if sufficiently fit, higher tempo and pressing.
- A team protecting a late lead can drop its line, slow the game and favour shorter passing.
- Fatigue limits the AI's willingness to sustain a press.
- A red-card disadvantage encourages a more conservative shape; a numerical advantage can encourage initiative.
- Width is selected from the relative strengths of both wide channels versus the central channel, rather than always preserving the same AI width. A hysteresis threshold avoids reversing a width choice on a small, noisy change in lane ratings.
- The policy produces transient effective settings and never mutates the club's saved tactics. Its design borrows the documented no-oscillation principle from Openfoot's AI comments, but is an independent Kotlin implementation, not a source-code port.
- Decisions are deterministic given the current match context; randomness stays in match resolution.

Added `MatchTacticalAITest.kt` with nine unit tests covering late chasing, fatigue constraints, protecting a lead, red-card disadvantage, wide/central matchup selection, neutral matchups, width hysteresis and output bounds. Added JUnit 4 as a test dependency.

**Validation performed:** the new standalone `MatchTacticalAI.kt` compiles successfully with the installed Kotlin compiler.  
**Validation not performed:** a full Android Gradle build and JUnit run could not be executed in this environment because Gradle/Android SDK dependencies are not installed and external dependency resolution is unavailable. Run `gradle testDebugUnitTest assembleDebug` in a configured environment before release.

## 6. Recommended next engineering steps

1. Run the new tests and the full Android build in CI; fix any integration failures first.
2. Add an automated, seeded baseline campaign for balanced synthetic teams and generated-world teams.
3. Record scoreline distributions and team/player stats before changing the shot/goal model.
4. Inspect the no-op aerial line and decide whether to restore an intended defensive-line effect or remove it with a comment and regression test.
5. Validate that xG and goals are statistically related across thousands of matches; don't tune on a few memorable fixtures.
6. Add invariants for red-card player counts, duplicate player participation, minutes played, keeper presence, substitutions, penalties, score/event consistency and season-stat deltas.
7. Move additional decision logic into pure functions so tests exercise production code directly.
8. Audit transfer/contract/wage and training progression with accounting invariants and deterministic progression tests.
9. Add a save migration test before extending persistent data models.
10. Use real historical match data or a documented target distribution to calibrate the simulator; broad guardrails alone cannot establish football realism.

## 7. Licence and reuse boundary

Openfoot Manager's repository states GPL-3.0. This NerdFM update does not copy Openfoot source code. It uses documented architectural ideas to independently implement a Kotlin tactical policy. Before importing or translating substantial source code, review the applicable licence obligations and distribution model with care.

## 8. What is needed for a genuinely complete Openfoot audit

To inspect every source file, inline comment, blame annotation and commit diff rather than only public rendered documentation and summaries, provide either:

- a ZIP of the Openfoot Manager repository plus a Git bundle (`git bundle create openfoot-history.bundle --all`), or
- a local clone/archive containing `.git` history.

A source ZIP alone supports a complete current-tree audit, but does not contain the complete commit history. The history bundle permits tracking when behaviours were introduced, which tests changed with them, and whether follow-up commits corrected regressions.

## Follow-up: persistent self-improvement layer

The current upgrade adds `SelfImprovingEngine.kt`:

- player-level, serializable tabular Q-learning with bounded state/action space and bounded memory;
- a match-time hook in build-up that lets learned action preferences influence long-ball selection and updates from possession/progression outcomes;
- bounded manager genomes evolved at season boundaries within each league, then blended into AI tactical setup;
- a persisted global calibration controller that consumes 64-match batches and adjusts chance creation / conversion multipliers within strict bounds.

The calibration loop is deliberately conservative: it uses aggregate goals-per-match and goals-per-shot, not single-match scores. It does not yet tune fatigue because the engine does not expose a trustworthy second-half output metric. A proper release should add batch simulation tests across multiple seeds and calibrate against a documented target league/era before changing these priors. Full Android build and JUnit execution remain unverified in this environment because the Android Gradle toolchain is unavailable.
