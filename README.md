# Nerd FM (milestone 1)

Offline, landscape football-management sim for Android (Kotlin + Jetpack Compose, no external services).

## Build the APK without installing anything (GitHub cloud build)
1. Make a free account at github.com, then create a **new repository** (any name, private is fine).
2. Click **Add file > Upload files**. Drag in EVERYTHING from this folder (including the hidden `.github` folder).
   - If `.github` doesn't upload: **Add file > Create new file**, name it `.github/workflows/build.yml`
     and paste the contents of that file.
3. Commit. Open the **Actions** tab -> **Build APK** -> wait ~5-8 minutes for the green tick.
   (If it didn't start: choose the workflow, press **Run workflow**.)
4. Open the finished run, scroll to **Artifacts**, download **NerdFM-debug-apk**, unzip, install `app-debug.apk`
   on your phone (allow "install unknown apps" when asked).
5. If the build fails (red cross), open the run, copy the red error lines and send them to Claude to fix.

## Nerd FM improvements in this build
- Club crest/logo in the game header and a custom launcher icon.
- Career date display advances weekly instead of showing only a round number.
- Training screen with individual focus, light/normal/intensive workload, fitness and sharpness tracking, and gradual potential-limited attribute growth.
- Coaching staff screen with a persistent coach market, hiring/releasing, weekly wages, and coaching effects on development.
- Player profile includes fitness, match sharpness, value, wages, contract details, average rating, minutes, shots, xG, key passes, tackles, interceptions and saves.
- Transfer offers support counteroffers with affordability and reputation-aware responses.
- Match conversion is tuned down to reduce arcade-like scorelines; sharpness now influences match performance. A deterministic smoke sample of 28 fixtures averaged 2.57 total goals per match (maximum 6); this is a small calibration sample, not a full balance test.
- Transfer filters remain stored in career state between visits.

## What's in milestone 1
- 7 divisions (Solaris Crown League ... Mossbank Regional League), 126 clubs, ~5,600 generated players, 36 attributes
- Hidden potential, scouting fog, fuzzy attribute ranges for unscouted players
- Minute-by-minute match engine (tactics, fatigue, set pieces, cards, injuries, subs, xG)
- Transfers, free agents, AI market, offers for your players, contracts, wage budget
- Finances, facilities, board confidence (you can be sacked), promotion/relegation (3 up / 3 down)
- Season rollover: ageing, development, retirements, youth intake, contract expiry
- Autosave every week


## Engine design references
Nerd FM remains a native Android/Kotlin game. The open-source [Player12 / FootballManagement project](https://github.com/FlavioMili/FootballManagement) and [FootballEngine](https://github.com/DeltaBitsSystem/FootballEngine) were used as design references for modular simulation, player development, tactical consequences, match analytics, staff effects, and negotiation flows. Their desktop C++23 and F# engines are not drop-in Android libraries, so this build ports selected design ideas into Nerd FM's existing Kotlin systems rather than copying their engine source. Review each upstream project's license before redistributing any upstream code or assets.


## Match-aware AI tactics (upgrade)

`MatchTacticalAI.kt` adds a deterministic, testable policy for AI-controlled teams. It adjusts effective tactics from the score, match minute, fatigue, red-card balance, and relative attacking/defensive lane strengths. Chasing sides can become more direct and raise tempo when fit; teams protecting a late lead can drop deeper and favour shorter passing; fatigue limits pressing; and width can adapt to the stronger matchup. These are transient match settings and do not overwrite saved club tactics.

Eight unit tests cover the policy's main decision branches and output bounds. Run `gradle testDebugUnitTest assembleDebug` in a configured Android/Gradle environment before release.

See `OPENFOOT_RESEARCH_AND_NERDFM_AUDIT.md` for the documented research findings, local static audit, issue-tracker lessons, validation status, licensing notes, and the remaining steps for a full Openfoot source/history audit.

## Match-engine quality toolkit (upgrade)

This revision adds `MatchEngineDiagnostics.kt`, a read-only diagnostics and batch-calibration layer around the existing Kotlin match engine. It intentionally leaves the in-match decision logic untouched, so the diagnostics can be merged and reviewed separately from gameplay tuning.

### New capabilities

- **Post-match invariant audit** checks score, shots-on-target, xG, pass counters, substitutions, cards, event minutes, event log consistency, active players, and condition ranges.
- **Seeded batch runner** runs a caller-provided fresh-match factory across a reproducible set of seeds and collects match reports.
- **Distribution summaries** report mean, standard deviation, min/max, median, and 10th/90th percentiles for goals, xG, and shots.
- **Balance guardrails** flag suspicious goal/shots/home-win-rate averages without forcing a single deterministic score distribution.
- **Regression digest** turns batch output into a stable text digest so a developer can compare results before/after changing simulation logic.
- **Fixture traces** give a compact, seed-labelled record of the opening and closing commentary events for bug reports.
- **Input validation helpers** validate 36-attribute vectors, starting lineups, probabilities, and non-negative numeric inputs.

### How to use the diagnostics

Create a factory that builds new world/player/team objects for each seed, then returns a new `MatchEngine`. Do not reuse mutated player instances across the batch.

```kotlin
val report = MatchBatchRunner.run(
    seeds = MatchSeedPlan.range(first = 1000L, count = 200),
    factory = { seed ->
        // Build fresh clubs, players, tactics and match teams using this seed.
        // Return MatchEngine(homeTeam, awayTeam, Random(seed))
        buildFreshTestFixture(seed)
    },
    failOnAuditError = true
)
println(report.toText())

val findings = MatchBalanceEvaluator.evaluate(report)
findings.forEach { println("${it.severity}: ${it.code} ${it.observed}; ${it.expected}") }
```

`buildFreshTestFixture` above is intentionally an application-specific adapter, not a function supplied by the game. This keeps the tooling independent from world-generation choices and lets test campaigns use balanced synthetic squads or a real generated world.

### Recommended calibration loop

1. Record a baseline batch with a fixed seed plan and fresh worlds.
2. Check for audit errors first. Do not tune probabilities to compensate for broken counters.
3. Review distributions, not one match: goals, xG, shots, cards, possession, and home-win rate.
4. Change one model family at a time (chance creation, shot quality, goalkeeper response, fatigue, or tactical influence).
5. Run the same seeds again and compare the digest, plus a larger unseen seed range to avoid overfitting.
6. Inspect high- and low-outlier fixtures with `FixtureTrace`.
7. Keep the balance targets broad and document any deliberate deviation for the game's intended difficulty.

The default balance ranges are development guardrails, not claims about a perfect football model. They should be recalibrated against the design target and a large sample before release.

## External open-source research and licensing

The project remains a native Kotlin/Android application. These projects were examined as architectural references; their code has **not** been copied into Nerd FM by this upgrade:

- [OpenEngine](https://github.com/atas76/openengine) — a long-running, pluggable football match-engine experiment under MIT. Useful as a reference for keeping a host game separate from the simulator.
- [FootballManagement](https://github.com/FlavioMili/FootballManagement) — a C++23 management simulator emphasizing data-driven configuration and separation between storage, simulation, and UI. Check its repository's current license and contribution guidance before reusing code.
- [FootballEngine](https://github.com/DeltaBitsSystem/FootballEngine) — an F# management/simulation project with a spatial simulation, deterministic processing, and mod-oriented data. Its repository states GPL-3.0; directly incorporating its code into a distributed app has license implications.
- [RPS Football Sim Engine](https://github.com/Alliance-Sky/football-sim-engine) — a Go simulation project advertising seeded matches, tournament automation, and match-state reporting. Check its current license and maturity before adopting components.
- [Openfoot Manager](https://github.com/openfootmanager/openfootmanager) — a GPL-3.0 football-management game with a moddable, offline-first approach and text-based match simulation.

These are not interchangeable drop-in libraries: they use different languages, data models, licenses, and maturity levels. The safer engineering approach is to compare public architecture and tests, then implement compatible ideas in Kotlin rather than paste large amounts of third-party code. Preserve each upstream project's license and attribution obligations if any code is ever adopted.

### Important status note

This is an upgrade to the supplied project, not a claim of Football Manager parity or statistically validated realism. The diagnostics make engine behavior easier to test and calibrate; the match model itself still needs repeated batch testing and gameplay review. The Android project should be built and tested in the target Gradle/Android environment before release.

## Self-improving simulation layer

NerdFM now includes a local, persistent hybrid learning layer (no server or ML framework required):

- **Player micro-learning:** compact tabular Q-learning profiles are stored on players. In build-up, learned preferences can influence the choice between short pass, long pass, dribble and hold. Rewards are updated from immediate possession/progression outcomes. Exploration decays with career appearances, while staying above a floor so the policy can continue adapting.
- **AI manager evolution:** each club has a bounded tactical genome. At season end, each league ranks genomes by points, goal difference and finish; elite policies are retained with small mutations while other AI clubs copy and mutate elite genes. The human club's tactics are never overwritten by the evolution step.
- **Aggregate calibration:** a career-persistent controller observes batches of 64 completed matches and makes small bounded adjustments to chance creation and shot conversion. It does not react to one freak scoreline; multipliers are constrained to safe ranges. The current late-match fatigue signal is intentionally held at its neutral baseline until the engine exposes a measured second-half output statistic.
- **Reproducibility and safety:** all policies are local, state is quantized, mutation and calibration are bounded, and the existing match engine remains the physical simulator rather than being replaced by an opaque model.

These are pragmatic game-scale learning systems, not a claim that a tabular agent has mastered football. Calibration targets are initial priors and should be tuned against a large, reproducible batch of NerdFM simulations. See `OPENFOOT_RESEARCH_AND_NERDFM_AUDIT.md` for limitations and validation notes.
