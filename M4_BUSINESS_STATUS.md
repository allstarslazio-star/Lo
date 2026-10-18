# M4 business layer: status

Written without a compiler. This sandbox has no Kotlin, Gradle, Android SDK or network, so
NOTHING here has been compiled or run. Run the GitHub Actions build first and fix any red errors.

## Added (new files)
- BusinessState.kt   serializable state: sponsors, clauses, negotiations, loans, scouts, ledger
- ClubFinance.kt     ticket pricing, sponsors, stadium expansion, fair-play rules, AI budget planning, weekly hooks
- ContractNegotiation.kt  3-round talks, agent fees, release clauses, appearance bonuses, sell-on clauses
- Loans.kt           loan in/out, wage sharing, recall, option to buy, automatic return
- ScoutingNetwork.kt hire/fire/assign scouts, per-player knowledge, reports
- BusinessUi.kt      "Business" screen: Overview, Commercial, Contracts, Loans, Scouting
- Tests: BusinessFinanceTest, ContractDealsTest, LoansTest, ScoutingNetworkTest, BusinessSoakTest (+ support)

## Changed (existing files)
- Models.kt      Club.stadiumBase is now var (stadium expansion)
- World.kt       business state field, biz() accessor, scout wages in wageBill
- Persistence.kt save format 3 (older saves load and get business state lazily)
- Game.kt        hooks: appearance bonuses, sponsor income, loan wage support, gate multiplier, win bonus, season review
- Transfers.kt   fair-play block, release-clause bids, agent fees, sell-on payments, loan guards for AI and sales
- Shell.kt       "Business" navigation entry
- Squad.kt       player dialog shows release clause and loan status

## Rules worth knowing
- Human club: 18% of base weekly income now comes from sponsor deals (starting deals keep income neutral).
- Ticket price level changes gate income; capacity only matters when demand exceeds it.
- Fair play: wages above 85% of revenue = warning; above 100% (or deficit and above 85%) blocks fee-paying signings.
- Generated squads start at 42 players; the existing 40-man cap blocks signings and loans until you release a few.

## Not done / not verified
- Not compiled, not run, no device testing. Button handlers exist for every control but are untested.
- Loans are human <-> AI only (no AI-to-AI loans).
- Scout knowledge is a progress bar; the fuzzy attribute display still uses the existing known/unknown flag.
- About 2,240 net new lines (7,364 to 9,601), not 20,000.
