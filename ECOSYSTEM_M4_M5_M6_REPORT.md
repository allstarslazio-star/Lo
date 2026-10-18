# Ecosystem milestones M4 (People), M5 (Fans/Stadium/Pitch/Facilities/Media), M6 (Calendar/Cups/Continental/Internationals/Travel/Referees)

Built on top of the M2 (medical) zip, **without** M3 (analytics). Save formats 8, 9 and 10 are used so that **format 7 stays reserved for M3**;
a v6 save migrates straight to the current format.

## STATUS: NOT COMPILED, NOT RUN
The environment that produced this had no Kotlin compiler, Gradle or network. Nothing here has been compiled or tested.
Only a bracket-balance check was done. Expect a first round of compile errors and some failing tests; run
`tools/jvm-check.sh` and then `gradle testDebugUnitTest assembleDebug` (UI files are only checked by CI).

## What was added
| Milestone | Main file | Tests | Save | Hooks |
|---|---|---|---|---|
| M4 People | People.kt | PeopleTest.kt | 8 | PeopleFactors (MORALE/SHARPNESS), People.weekly, training boost, renewal-wage multiplier, StaffRole.LANGUAGE_COACH |
| M5 Environment | ClubEnv.kt | EnvTest.kt | 9 | EnvFactors (home crowd MORALE, pitch SHARPNESS/CONDITION/RECOVERY, medical centre RECOVERY), gate factor in Game.record, academy shift in Identity.effectiveYouth |
| M6 Calendar | FixtureCalendar.kt | CalendarTest.kt | 10 | FixtureCalendar.midweek in Game.startWeek, CalendarFactors, RefereeFactors |
UI: EcoWorldUi.kt (People, Fans, Facilities, Media, Calendar tabs in the Ecosystem hub) and a referee line on the match screen.

## Departures from the written plan (please review)
- **Staff languages**: only players have mother tongue + learned languages. Staff mother tongue is their nation; no learned languages stored.
- **Agent fees unchanged**: agent personality (greedy/loyal/aggressive, derived from the player id) changes renewal wage demands only (x1.10 / x0.95 / x1.06). Existing tests pin the agent fee, so it was left alone.
- **Languages and M7**: no tactical-familiarity effect (M7 does not exist yet).
- **Relationships** are computed for the human club only. Mentor pairs give up to +4% progression; friendships/rivalries nudge cohesion by at most 0.25 a week.
- **Internationals**: call-ups cost 8 condition, give +2 morale and a 1.5% injury risk. Players are NOT removed from the squad for the window.
- **Referees**: the engine's card rates are frozen, so referees only nudge MORALE by at most 0.4 (strict ref vs hard-pressing side, lenient ref, inconsistent ref favouring the home side). **Card counts are not changed and nothing was measured against the 6.22 yellows / 0.683 reds harness.**
- **Cups are played in weeks the league leaves free**, on top of the league week, with the real match engine through the usual hooks, but results go in no league table. Draw: seeded shuffle, odd rounds give the biggest club a bye (126 clubs -> 125 ties, 7 rounds, weeks 2/7/11/15/20/27/31). Draws are decided by extra time/penalties from a seeded roll (no replays).
- **Continental**: 8 clubs, two groups of four (double round robin, weeks 4/9/13/18/23/25), semi-finals week 29, final week 33. Qualifiers are the top 8 of league tier 0 from the previous season (by reputation in season 1). There is only one tier-0 league in this world, so "tier-0 leagues" is that one league.
- **Travel** uses fixed pseudo-coordinates per club on a 1000 km map (the world has no geography). Away trips over 350 km cost up to 1.5 condition; a second match in a week costs 1.0; midweek trips cost 0.02 recovery.
- **Data suite** level is stored and upgradable but nothing reads it until M3.
- Loading a mid-season save catches the cup and continental brackets up with quick (non-engine) results.

## Risks to check first
1. GoldenBaselineTest bands (injuries 25.02 +/- 2.5) with cups and Europe on: cup matches add match exposure for every club. If injuries drift, reduce cup rounds or raise the band deliberately.
2. `safeModeReproducesTheOriginalGameExactly`: every new hook is behind `Ecosystem.enabled`; the gate factor is exactly 1.0 when disabled.
3. Existing tests that pin provider lists, wage demands (`ContractDealsTest`), gate receipts or the save version number.
4. MatchEngine.kt and MatchConditions.kt were not edited (frozen-hash test).
5. Weekly advance time with 63 extra engine matches in cup week 2 (test asserts < 2 s).

## Name generator (NameForge.kt) - added afterwards, also NOT compiled or run
- Opt-in: `World.richNames` (saved; old saves read false). The original `Names.fullName` is byte-for-byte unchanged and stays the default, because its random draws are pinned by the exact-replay tests. The new-career screen (Menu.kt) now passes `richNames = true`; `WorldGen.generate(..., richNames = false)` is the default for tests.
- Per nation: given names by generation (older players get more classic names), female names, curated family names plus cleaned-up syllable names, customs (double surnames, particles such as di/von/Le, patronymics, Mc/O'/Mac, al-/el-, hyphenated names, hyphenated given names, feminine surname endings), mixed heritage, occasional nicknames, a blocklist and quality checks.
- `NameForge.make(nat, rnd, NameOptions(gender, age, foreignChance, nicknames))` returns given/surname/nickname/short/full. `fixDuplicates(w)` runs at world creation and each season rollover and renames exact duplicate names deterministically.
- Only players use it so far. Staff, owners, chairmen and directors still use the old generator (their code has no World at hand); switching them is a small follow-up, and would also allow women in those roles via `randomGender`.
- Tests: NameForgeTest.kt.

## Faces and flags (AvatarSpec.kt, FlagSpec.kt, PortraitUi.kt) - NOT compiled or run
- **Faces are generated, never stored.** `AvatarSpec.of(id, nation, age, kind, female)` is a pure function, so a player keeps his face for life, there is no save change, and old saves get faces automatically. Skin tone, hair colour and style, beard, eyes, face shape and proportions, glasses and age lines come from it. Nation shifts the skin and hair distribution but with wide spreads (about one person in eight takes looks from another nation); age brings grey hair, hair loss, beards and lines. `kind` (0 player, 1 staff, 2 board, 3 referee) stops different groups sharing faces. Women get their own hair styles and no beards (used when a gender is passed; players are all men for now).
- **Flags** are invented designs for the 12 fictional nations (stripes, crosses, saltire, circles, diamonds), drawn from small layer lists. They are not real flags.
- **Where they appear (FM26-style):** squad table rows (face + flag + name), player profile header (large portrait with the flag in the corner), transfer search rows and incoming-offer cards, both starting XIs on the match screen, staff lists in the Ecosystem hub, owner and chairman on the Board tab (face and flag derived from their names), and the People tab.
- Shirts use the club's two colours; free agents get a neutral shirt.
- Tests: PortraitSpecTest.kt (determinism, ranges, variety, nation centres, ageing, women, one distinct well-formed flag per nation). The drawing itself needs checking on a phone: expect to tune proportions.
- Not done: club badges, referee faces on the match screen, faces in the inbox and on the pitch tokens, and a staff/board `kind` for women (the generator supports it).
