# Third-party notes (Club Ecosystem work)

Rule followed: MIT / Apache-2.0 / BSD code may be adapted with attribution; GPL / AGPL / unlicensed code is studied for
ideas only and re-implemented independently. **No third-party code has been copied into NerdFM.** No Football Manager
data, assets, names or text were used. Every rule and number in the ecosystem layer is original and tunable.

Research date: 2026-10-16, via web search/fetch from the sandbox (GitHub pages reachable; the Gradle/Maven hosts are not).
Only facts that were read on the cited page are recorded as facts. Everything else is marked as my own knowledge.

## Open-source football management projects

| Source | Licence (as read on the page) | What was learned | Code reused |
|---|---|---|---|
| [Openfoot Manager](https://github.com/openfootmanager/openfootmanager) (Rust + TypeScript/React, Tauri, SQLite) | GPL-3.0 (`LICENSE.md`) | Architecture idea: separate engine / game state / UI layers; training and staff systems exist as a feature area; transfers and contracts as workflows. No finance features described on the page. | None. GPL-3.0: studied for ideas only. |
| [Open Football](https://github.com/ZOXEXIVO/open-football) (Rust) | Apache-2.0 (README "License" section) | Idea: clubs with financial context, reputation influencing outcomes, a connected league/club ecosystem running headless. Staff, injuries, morale and contracts are not described on the page. | None. Licence would permit adaptation with attribution, but the code is Rust and nothing was needed. |
| [Zone Blitz](https://github.com/Tiernebre/zone-blitz) (American-football owner/GM simulator) | No licence stated on the page | Idea only: hiring coaches and scouts under a staff cap. | None. Unlicensed: ideas only. |
| Bygfoot (GTK football manager, listed by distribution package pages) | **Not verified** | Not read; nothing taken. | None. |

## Open data and published research

| Source | Terms / provenance | Use |
|---|---|---|
| [StatsBomb open data](https://github.com/statsbomb/open-data) | The page asks that published work based on the data cite StatsBomb as the source and use their logo; a `LICENSE.pdf` is linked but was not read, so commercial terms are **unverified**. | **Not used and not bundled.** Listed as a possible future source for sanity-checking xG/pressing distributions. |
| Ekstrand J, Hägglund M, Waldén M. UEFA injury study ([BJSM 45(7):553](https://bjsm.bmj.com/content/45/7/553), data 2001-2008) | Journal abstract, read on the page. | Reported there: match injury incidence 27.5 vs training 4.1 per 1000 h; re-injuries 12% of injuries and longer absences (24 vs 18 days); thigh strain the most common subtype at 17%. Used qualitatively: recurrence is a minority of injuries but longer; training injuries are much rarer than match injuries. Game numbers are tuned to the NerdFM engine (25 injured per 1000 player-weeks, measured), not copied from the study. |
| Romeijn N, van Sterkenburg J / FIFPro, [toolkit for guiding foreign players](https://www.eur.nl/en/eshcc/news/toolkit-recommendations-guidance-and-communication-towards-foreign-players-professional-football) | Overview page only (26 interviews, 150-expat survey). Findings and numbers are in a FIFPro PDF that was **not read**. | Used qualitatively: adapting to a new culture and language, building a social life, and communication/playing-style differences are obstacles, harder for young players who leave friends and family. No numbers taken. |
| Home advantage | Several papers surfaced in search; **none was opened**. | No external number used. The home-advantage level is whatever the frozen, calibrated engine produces (about 43% home wins in the real-path baseline); ecosystem effects on it are capped by `ModifierCaps`. |

## My own knowledge (not verified against a source in this session)

- Ideas such as rehab phases, load management, tactical familiarity and fatigue accumulation are common football-science
  concepts. The implementations and all constants are NerdFM's own and are validated only by NerdFM's own harness tests.
