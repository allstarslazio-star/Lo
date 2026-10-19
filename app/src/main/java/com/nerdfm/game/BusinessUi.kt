package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val BUSINESS_TABS = listOf("Overview", "Commercial", "Contracts", "Loans", "Scouting")
private val GROUP_LABELS = listOf("Any", "GK", "DEF", "MID", "ATT")

private fun groupMatches(p: Player, index: Int): Boolean = when (index) {
    0 -> true
    1 -> p.pos.group == 0
    2 -> p.pos.group == 1
    3 -> p.pos.group == 2
    else -> p.pos.group >= 3
}

@Composable
fun BusinessScreen(w: World) {
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    remember(w.humanClubId) { Business.ensureStarted(w); true }
    val refresh: () -> Unit = { tick++; App.bump() }
    Column(Modifier.fillMaxSize()) {
        Seg(BUSINESS_TABS, tab, { tab = it })
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> OverviewTab(w, refresh)
                1 -> CommercialTab(w, refresh)
                2 -> ContractsTab(w, refresh)
                3 -> LoansTab(w, refresh)
                else -> ScoutingTab(w, refresh)
            }
        }
    }
}

// ---------------------------------------------------------------- overview

@Composable
private fun OverviewTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val b = w.biz()
    val report = Business.ffpReport(w, me)
    val statusColor = when (report.status) { 0 -> C.green; 1 -> C.amber; else -> C.red }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Commercial summary", 15, C.accent, true)
            Spacer(Modifier.height(4.dp))
            T("Balance " + money(me.balance), 14, if (me.balance < 0) C.red else C.text, true)
            T("Sponsor income " + money(Business.sponsorIncome(w)) + " / week", 12, lines = 2)
            T("Gate multiplier x" + "%.2f".format(Business.gateMultiplier(w, me)), 12)
            T("Stadium " + me.capacity() + " seats, expected crowd " + Business.expectedAttendance(w, me), 12, lines = 2)
            Spacer(Modifier.height(6.dp))
            T("This season so far", 13, C.accent, true)
            T("Sponsor income " + money(b.seasonSponsorIncome), 12, C.muted)
            T("Agent fees " + money(b.seasonAgentFees), 12, C.muted)
            T("Scouting wages " + money(b.seasonScoutCosts), 12, C.muted)
            T("Player bonuses " + money(b.seasonBonuses), 12, C.muted)
            T("Loan wage support " + money(b.seasonLoanSubsidy), 12, C.muted)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Fair-play compliance", 15, C.accent, true)
            Spacer(Modifier.height(4.dp))
            T(report.note, 12, statusColor, lines = 4)
            Spacer(Modifier.height(4.dp))
            Bar((report.wageRatio / 1.2).toFloat(), statusColor, Modifier.fillMaxWidth())
            T("Wage bill is " + (report.wageRatio * 100).roundToInt() + "% of annual revenue", 11, C.muted, lines = 2)
            T("Strikes: " + b.ffpStrikes, 12)
            Spacer(Modifier.height(6.dp))
            T("Rules", 13, C.accent, true)
            T("Above 85% of revenue is a warning. Above 100%, or a deficit with wages over 85%, blocks fee-paying signings. Each failed season review adds a strike, a fine and lost board confidence.", 11, C.muted, lines = 8)
            Spacer(Modifier.height(6.dp))
            T("Active loans: " + Loans.active(w).size, 12)
            T("Scouts employed: " + b.scouts.size + " / " + ScoutingNetwork.maxScouts(me), 12)
        }
    }
}

// ---------------------------------------------------------------- commercial

@Composable
private fun CommercialTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val b = w.biz()
    var msg by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Ticket pricing", 15, C.accent, true)
            Seg(Business.PRICE_LABELS, b.ticketLevel, { i ->
                if (Business.setTicketLevel(w, i)) msg = "Ticket prices set to " + Business.PRICE_LABELS[i] + "."
                refresh()
            })
            Spacer(Modifier.height(4.dp))
            T("Demand " + Business.expectedDemand(w, me) + " seats, capacity " + me.capacity(), 12, lines = 2)
            T("Expected crowd " + Business.expectedAttendance(w, me), 12)
            T("Gate income x" + "%.2f".format(Business.gateMultiplier(w, me)) + " compared with standard prices", 12, C.muted, lines = 2)
            T("Higher prices raise income per fan but cut demand. Big clubs that sell out can charge more.", 11, C.muted, lines = 3)
            Spacer(Modifier.height(10.dp))
            T("Stadium", 15, C.accent, true)
            if (b.expansionSeats > 0) {
                T("Building " + b.expansionSeats + " seats, " + b.expansionWeeksLeft + " weeks left.", 12, C.amber, lines = 2)
            } else {
                for (seats in listOf(2000, 5000, 10000)) {
                    val cost = Business.expansionCost(me, seats)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("+" + seats + " seats  " + money(cost), 12, modifier = Modifier.weight(1f))
                        Btn("Build", enabled = me.balance >= cost * 3 / 2) {
                            msg = Business.startExpansion(w, seats).second
                            refresh()
                        }
                    }
                }
                T("You need 1.5 times the cost in the bank. Extra seats only pay off if demand exceeds capacity.", 11, C.muted, lines = 3)
            }
            if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); T(msg, 12, C.green, lines = 4) }
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Your sponsors", 15, C.accent, true)
            val deals = Business.activeSponsors(w)
            if (deals.isEmpty()) T("No active deals. Sign one below.", 12, C.amber)
            for (d in deals) {
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    T(d.brand + "  (" + Business.slotName(d.slot) + ")", 13, bold = true, lines = 2)
                    T(money(d.weekly) + " / week until " + d.endYear + ", win bonus " + money(d.winBonus), 11, C.muted, lines = 2)
                    Btn("Buy out " + money(Business.terminationPenalty(d)), enabled = me.balance >= Business.terminationPenalty(d)) {
                        msg = Business.terminateSponsor(w, d.id).second
                        refresh()
                    }
                }
                HorizontalDivider(color = C.line)
            }
            Spacer(Modifier.height(8.dp))
            T("Offers", 15, C.accent, true)
            if (b.sponsorOffers.isEmpty()) T("No offers right now. New ones arrive every 13 weeks.", 12, C.muted, lines = 2)
            for (o in b.sponsorOffers.toList()) {
                val taken = Business.slotTaken(w, o.slot)
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    T(o.brand + "  (" + Business.slotName(o.slot) + ")", 13, bold = true, lines = 2)
                    T(money(o.weekly) + " / week until " + o.endYear + ", signing bonus " + money(o.signingBonus), 11, C.muted, lines = 2)
                    Btn(if (taken) "Slot already filled" else "Accept", enabled = !taken) {
                        msg = Business.acceptSponsor(w, o.id).second
                        refresh()
                    }
                }
                HorizontalDivider(color = C.line)
            }
        }
    }
}

// ---------------------------------------------------------------- contracts

@Composable
private fun ContractsTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val b = w.biz()
    var selectedId by remember { mutableIntStateOf(-1) }
    val selected = if (selectedId >= 0) w.players[selectedId] else null
    val squad = w.squad(me).filter { Loans.loanFor(w, it.id) == null }
        .sortedWith(compareBy<Player> { it.contractEnd }.thenByDescending { it.ovr })
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Contracts", 15, C.accent, true)
            T("Sell-on policy when you sell a player", 11, C.muted)
            Seg(listOf("None", "10% (buyer pays 6% less)", "20% (12% less)"), b.sellOnPolicy, { i ->
                ContractDeals.setSellOnPolicy(w, i)
                refresh()
            })
            Spacer(Modifier.height(6.dp))
            for (p in squad.take(45)) {
                val clauses = b.clauses[p.id]
                val tags = ArrayList<String>()
                if (clauses != null && clauses.releaseClause > 0L) tags.add("release " + money(clauses.releaseClause))
                if (clauses != null && clauses.appearanceBonus > 0) tags.add("app bonus " + clauses.appearanceBonus)
                if (clauses != null && clauses.sellOnPct > 0) tags.add("sell-on " + clauses.sellOnPct + "%")
                val expiring = p.contractEnd <= w.year
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cell(p.pos.label, 34, C.muted)
                        Cell(p.name, 140, bold = selectedId == p.id)
                        Cell("" + p.ovr, 30, ovrColor(p.ovr), true)
                        Cell(money(p.wage.toLong()), 62)
                        Cell("" + p.contractEnd, 42, if (expiring) C.red else C.muted)
                        Btn("Talks", enabled = p.injWeeks >= 0) { selectedId = p.id; refresh() }
                    }
                    if (tags.isNotEmpty()) T(tags.joinToString("  |  "), 10, C.amber, lines = 2)
                }
                HorizontalDivider(color = C.line)
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (selected == null || selected.clubId != me.id) {
                T("Negotiation", 15, C.accent, true)
                T("Pick a player with Talks. Agents negotiate over three rounds; if talks collapse they refuse to speak for eight weeks.", 12, C.muted, lines = 6)
            } else {
                NegotiationForm(w, selected, refresh) { selectedId = -1 }
            }
        }
    }
}

@Composable
private fun NegotiationForm(w: World, p: Player, refresh: () -> Unit, onDone: () -> Unit) {
    val me = w.human()
    val demand = ContractDeals.demandedWage(p)
    var wageText by remember(p.id) { mutableStateOf(demand.toString()) }
    var years by remember(p.id) { mutableIntStateOf(3) }
    var clauseText by remember(p.id) { mutableStateOf("") }
    var bonusText by remember(p.id) { mutableStateOf("") }
    var appText by remember(p.id) { mutableStateOf("") }
    var msg by remember(p.id) { mutableStateOf("") }
    val wait = ContractDeals.weeksUntilTalks(w, p)
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)

    T(p.name, 16, C.accent, true, lines = 2)
    T("Now " + money(p.wage.toLong()) + "/wk until " + p.contractEnd + ".  Asking about " + money(demand.toLong()) + "/wk", 11, C.muted, lines = 3)
    T(PlayerRelations.personality(p) + ", morale " + p.morale.roundToInt(), 11, C.muted)
    if (wait > 0) T("Agent will not talk for $wait more week(s).", 12, C.red, lines = 2)
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = wageText, onValueChange = { wageText = it.filter { c -> c.isDigit() }.take(8) },
        singleLine = true, label = { Text("Weekly wage") }, keyboardOptions = numeric
    )
    T("Years", 11, C.muted)
    Seg(listOf("1", "2", "3", "4", "5"), years - 1, { years = it + 1 })
    OutlinedTextField(
        value = clauseText, onValueChange = { clauseText = it.filter { c -> c.isDigit() }.take(10) },
        singleLine = true, label = { Text("Release clause (0 = none)") }, keyboardOptions = numeric
    )
    OutlinedTextField(
        value = bonusText, onValueChange = { bonusText = it.filter { c -> c.isDigit() }.take(9) },
        singleLine = true, label = { Text("Signing bonus") }, keyboardOptions = numeric
    )
    OutlinedTextField(
        value = appText, onValueChange = { appText = it.filter { c -> c.isDigit() }.take(6) },
        singleLine = true, label = { Text("Appearance bonus") }, keyboardOptions = numeric
    )
    val wage = wageText.toIntOrNull() ?: 0
    val bonus = bonusText.toLongOrNull() ?: 0L
    val fee = ContractDeals.renewalAgentFee(wage, years)
    T("Agent fee " + money(fee) + " plus bonus " + money(bonus) + " paid now. Balance " + money(me.balance), 11, C.muted, lines = 3)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn("Make offer", enabled = wage > 0 && wait == 0) {
            val terms = ContractTerms(wage, years, clauseText.toLongOrNull() ?: 0L, bonus, appText.toIntOrNull() ?: 0)
            val reply = ContractDeals.propose(w, p, terms)
            msg = reply.message
            val counter = reply.counter
            if (counter != null) wageText = counter.wage.toString()
            if (reply.outcome == 0) onDone()
            refresh()
        }
        Btn("Close") { onDone() }
    }
    if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); T(msg, 12, C.amber, lines = 6) }
}

// ---------------------------------------------------------------- loans

@Composable
private fun LoansTab(w: World, refresh: () -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Seg(listOf("Loan in", "Loan out", "My loans"), sub, { sub = it })
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (sub) {
                0 -> LoanInPanel(w, refresh)
                1 -> LoanOutPanel(w, refresh)
                else -> MyLoansPanel(w, refresh)
            }
        }
    }
}

@Composable
private fun LoanInPanel(w: World, refresh: () -> Unit) {
    val me = w.human()
    var group by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    val candidates = remember(group, w.week, w.year, Loans.active(w).size, me.players.size) {
        w.players.values.filter { p ->
            p.clubId >= 0 && p.clubId != me.id && p.age <= 32 && groupMatches(p, group) &&
                Loans.loanInProblem(w, p, 50) == null
        }.let { l -> val ovr = shownOvrFn(w); l.map { it to ovr(it) }.sortedByDescending { it.second }.take(25).map { it.first } }
    }
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Loan players in", 15, C.accent, true)
        T(w.windowText() + ". You pay a loan fee (4% of value) and a share of his wages. Maximum " + Loans.MAX_LOANS_IN + " loans.", 11, C.muted, lines = 3)
        Seg(GROUP_LABELS, group, { group = it })
        if (msg.isNotEmpty()) T(msg, 12, C.green, lines = 3)
        Spacer(Modifier.height(4.dp))
        if (candidates.isEmpty()) T("No loanable players match right now.", 12, C.muted, lines = 2)
        for (p in candidates) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell(p.pos.label, 34, C.muted)
                Cell(p.name, 150)
                Cell("" + p.age, 28)
                Cell(w.clubs[p.clubId].name, 130, C.muted)
                val o = shownOvr(w, p)
                Cell("" + o + (if (isKnown(w, p)) "" else "?"), 38, ovrColor(o), true)
                Btn("Loan") { picked = p.id }
            }
            HorizontalDivider(color = C.line)
        }
    }
    val chosen = if (picked >= 0) w.players[picked] else null
    if (chosen != null) {
        var share by remember(chosen.id) { mutableIntStateOf(1) }
        var half by remember(chosen.id) { mutableStateOf(false) }
        var option by remember(chosen.id) { mutableStateOf(false) }
        var dialogMsg by remember(chosen.id) { mutableStateOf("") }
        val shares = listOf(50, 75, 100)
        AlertDialog(
            onDismissRequest = { picked = -1 },
            title = { Text("Loan " + chosen.name) },
            text = {
                Column {
                    T("From " + (if (chosen.clubId >= 0) w.clubs[chosen.clubId].name else "?") + ", wage " + money(chosen.wage.toLong()) + "/wk", 12, C.muted, lines = 2)
                    T("Share of wages you pay", 11, C.muted)
                    Seg(listOf("50%", "75%", "100%"), share, { share = it })
                    T("Length", 11, C.muted)
                    Seg(listOf("Full season", "Half season"), if (half) 1 else 0, { half = it == 1 })
                    T("Option to buy", 11, C.muted)
                    Seg(listOf("None", "Yes (asking price)"), if (option) 1 else 0, { option = it == 1 })
                    if (dialogMsg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); T(dialogMsg, 12, C.amber, lines = 4) }
                }
            },
            confirmButton = {
                Btn("Confirm loan") {
                    val r = Loans.loanIn(w, chosen, shares[share], half, option)
                    if (r.first) { msg = r.second; picked = -1 } else dialogMsg = r.second
                    refresh()
                }
            },
            dismissButton = { TextButton(onClick = { picked = -1 }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LoanOutPanel(w: World, refresh: () -> Unit) {
    val me = w.human()
    var picked by remember { mutableIntStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    val squad = w.squad(me).filter { Loans.loanFor(w, it.id) == null }.sortedBy { it.ovr }
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Loan players out", 15, C.accent, true)
        T(w.windowText() + ". Other clubs pay most of the wages. Your own wage support continues for the rest.", 11, C.muted, lines = 3)
        if (msg.isNotEmpty()) T(msg, 12, C.green, lines = 3)
        Spacer(Modifier.height(4.dp))
        for (p in squad.take(40)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell(p.pos.label, 34, C.muted)
                Cell(p.name, 150)
                Cell("" + p.age, 28)
                Cell("" + p.ovr, 34, ovrColor(p.ovr), true)
                Cell(money(p.wage.toLong()), 62)
                Btn("Loan out", enabled = w.isWindowOpen() && squad.size > 18) { picked = p.id }
            }
            HorizontalDivider(color = C.line)
        }
    }
    val chosen = if (picked >= 0) w.players[picked] else null
    if (chosen != null) {
        var half by remember(chosen.id) { mutableStateOf(false) }
        val options = Loans.loanOutOptions(w, chosen)
        AlertDialog(
            onDismissRequest = { picked = -1 },
            title = { Text("Loan out " + chosen.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    T("Length", 11, C.muted)
                    Seg(listOf("Full season", "Half season"), if (half) 1 else 0, { half = it == 1 })
                    Spacer(Modifier.height(6.dp))
                    if (options.isEmpty()) T("No club will take him on loan right now.", 12, C.amber, lines = 3)
                    for (o in options) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            T(o.first.name + " (pays " + o.second + "%)", 12, modifier = Modifier.weight(1f), lines = 2)
                            Btn("Send") {
                                val r = Loans.loanOut(w, chosen, o.first, half)
                                msg = r.second
                                picked = -1
                                refresh()
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picked = -1 }) { Text("Close") } }
        )
    }
}

@Composable
private fun MyLoansPanel(w: World, refresh: () -> Unit) {
    val me = w.human()
    var msg by remember { mutableStateOf("") }
    val deals = Loans.active(w).filter { it.parentClubId == me.id || it.borrowerClubId == me.id }
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Active loans", 15, C.accent, true)
        if (msg.isNotEmpty()) T(msg, 12, C.green, lines = 3)
        if (deals.isEmpty()) T("No players are on loan to or from your club.", 12, C.muted, lines = 2)
        for (d in deals) {
            val p = w.players[d.playerId] ?: continue
            val incoming = d.borrowerClubId == me.id
            val other = w.clubs.getOrNull(if (incoming) d.parentClubId else d.borrowerClubId)?.name ?: "?"
            val until = if (d.endWeek >= Loans.SEASON_END_WEEK) "end of season" else "week " + d.endWeek
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                T(p.name + " (" + p.pos.label + ", " + p.ovr + ")", 13, bold = true, lines = 2)
                T((if (incoming) "On loan from " else "On loan at ") + other + " until " + until + ", wage share " + d.borrowerSharePct + "%", 11, C.muted, lines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn(if (incoming) "Send back" else "Recall") {
                        msg = Loans.recall(w, d.id).second
                        refresh()
                    }
                    if (incoming && d.optionFee > 0L) {
                        Btn("Buy " + money(d.optionFee), enabled = w.isWindowOpen()) {
                            msg = Loans.exercisePurchase(w, d.id).second
                            refresh()
                        }
                    }
                }
            }
            HorizontalDivider(color = C.line)
        }
    }
}

// ---------------------------------------------------------------- scouting

@Composable
private fun ScoutingTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val b = w.biz()
    var msg by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Scouting network", 15, C.accent, true)
            T("Scouts " + b.scouts.size + " / " + ScoutingNetwork.maxScouts(me) + "  (raise the scouting level to employ more)", 11, C.muted, lines = 2)
            if (msg.isNotEmpty()) T(msg, 12, C.green, lines = 3)
            for (s in b.scouts.toList()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    T(s.name + "  ability " + s.ability + "  judgement " + s.judgement, 13, bold = true, lines = 2)
                    T(money(s.wage.toLong()) + "/wk, " + s.reportsMade + " reports, now: " + ScoutingNetwork.regionLabel(s.region) + ", " + ScoutingNetwork.posGroupLabel(s.posGroup), 11, C.muted, lines = 3)
                    val regionOptions = listOf("Off", "Free") + (1..7).map { "L$it" }
                    val regionIndex = when (s.region) { -1 -> 0; -2 -> 1; else -> s.region + 2 }
                    Seg(regionOptions, regionIndex, { i ->
                        val region = when (i) { 0 -> -1; 1 -> -2; else -> i - 2 }
                        ScoutingNetwork.assign(w, s.id, region, s.posGroup)
                        refresh()
                    })
                    Spacer(Modifier.height(3.dp))
                    Seg(GROUP_LABELS, s.posGroup + 1, { i ->
                        ScoutingNetwork.assign(w, s.id, s.region, i - 1)
                        refresh()
                    })
                    Spacer(Modifier.height(3.dp))
                    Btn("Release scout") {
                        msg = ScoutingNetwork.fire(w, s.id).second
                        refresh()
                    }
                }
                HorizontalDivider(color = C.line)
            }
            Spacer(Modifier.height(8.dp))
            T("Available to hire", 14, C.accent, true)
            for (s in b.scoutMarket.toList()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(s.name + "  ab " + s.ability + "  jd " + s.judgement + "  " + money(s.wage.toLong()) + "/wk", 12, modifier = Modifier.weight(1f), lines = 2)
                    Btn("Hire", enabled = b.scouts.size < ScoutingNetwork.maxScouts(me)) {
                        msg = ScoutingNetwork.hire(w, s.id).second
                        refresh()
                    }
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("In progress", 15, C.accent, true)
            val partial = b.knowledge.entries.filter { it.value in 1..99 && w.players.containsKey(it.key) }
                .sortedByDescending { it.value }.take(8)
            if (partial.isEmpty()) T("No players are being watched yet.", 12, C.muted, lines = 2)
            for (entry in partial) {
                val p = w.players[entry.key] ?: continue
                T(p.name + " (" + p.pos.label + ", " + p.age + ")  " + entry.value + "%", 12, lines = 2)
                Bar(entry.value / 100f, C.accent, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            T("Reports", 15, C.accent, true)
            if (b.reports.isEmpty()) T("Full reports appear when a player reaches 100% knowledge.", 12, C.muted, lines = 3)
            for (r in b.reports.take(10)) {
                T(r.scoutName + ", " + r.year + " wk " + r.week, 10, C.muted, lines = 2)
                T(r.text, 12, lines = 5)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
