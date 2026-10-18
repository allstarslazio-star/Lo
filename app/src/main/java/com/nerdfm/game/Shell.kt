package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val NAV = listOf("Home", "Inbox", "Squad", "Players", "People", "Tactics", "Training", "Staff", "League", "Transfers", "Finance", "Business", "Ecosystem", "Club", "Diagnostics")

@Composable
fun GameShell(w: World, tick: Int) {
    val ctx = LocalContext.current
    val scope = App.scope
    var confirm by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var advancing by remember { mutableStateOf(false) }
    val me = w.human()
    val fx = w.humanFixture()
    val unread = w.news.count { !it.read }

    fun runAdvance() {
        if (advancing || App.busy.isNotEmpty()) return
        advancing = true
        scope.launch {
            try {
            if (fx != null) {
                val f = heavy("Simulating the rest of the round...") { Game.startWeek(w) }
                if (f != null) {
                    App.pendingFixture = f
                    App.engine = Game.humanEngine(w, f)
                    App.mode = Mode.MATCH
                } else App.bump()
            } else {
                heavy(if (w.week == 34) "Season rollover..." else "Advancing a week...") {
                    Game.startWeek(w)
                    Game.completeWeek(w)
                }
                withContext(Dispatchers.IO) { Persistence.save(ctx.filesDir, w) }
                App.bump()
            }
            } finally { advancing = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // top bar
        Row(
            Modifier.fillMaxWidth().background(C.surface).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { ClubCrest(me); Spacer(Modifier.width(6.dp)); Column { T(me.name, 16, C.accent, true); T(w.leagueOf(me.id).name + "  |  " + w.dateLabel(), 11, C.muted) } }
            }
            Column(Modifier.weight(1f)) {
                T(if (w.week == 0) "Pre-season" else if (w.week > 34) "Season over" else "Round ${w.week} / 34", 13)
                T(w.windowText(), 11, if (w.isWindowOpen()) C.green else C.muted)
            }
            Column(Modifier.weight(1f)) {
                T("Balance " + money(me.balance), 13, if (me.balance < 0) C.red else C.text)
                T("Board " + me.boardConfidence.roundToInt() + "%", 11,
                    if (me.boardConfidence < 30f) C.red else if (me.boardConfidence < 50f) C.amber else C.green)
            }
            Btn("Menu") { menu = true }
            Spacer(Modifier.width(8.dp))
            val label = if (fx != null) {
                val home = fx.home == me.id
                (if (home) "Play (H) vs " else "Play (A) vs ") + w.clubs[if (home) fx.away else fx.home].name
            } else if (w.week == 0) "Begin season" else "Advance"
            Btn(if (advancing) "Advancing…" else label, enabled = !advancing && App.busy.isEmpty()) { if (fx != null) confirm = true else runAdvance() }
        }
        TutorialBanner()
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(104.dp).fillMaxHeight().background(C.surface).verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
                for (n in NAV) {
                    val sel = App.screen == n
                    Box(
                        Modifier.fillMaxWidth().background(if (sel) C.accent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { App.screen = n }.padding(horizontal = 10.dp, vertical = 11.dp)
                    ) {
                        val extra = if (n == "Inbox" && unread > 0) " ($unread)" else if (n == "Transfers" && w.offers.isNotEmpty()) " (${w.offers.size})" else ""
                        T(n + extra, 13, if (sel) C.accent else C.text, sel)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                when (App.screen) {
                    "Home" -> HomeScreen(w)
                    "Inbox" -> InboxScreen(w)
                    "Players" -> PlayersScreen(w)
                    "Squad" -> SquadScreen(w)
                    "People" -> PeopleScreen(w)
                    "Tactics" -> TacticsScreen(w)
                    "Training" -> TrainingScreen(w)
                    "Staff" -> StaffScreen(w)
                    "League" -> LeagueScreen(w)
                    "Transfers" -> TransferScreen(w)
                    "Finance" -> FinanceScreen(w)
                    "Business" -> BusinessScreen(w)
                    "Ecosystem" -> EcosystemScreen(w)
                    "Club" -> ClubScreen(w)
                    "Diagnostics" -> DiagnosticsScreen(w)
                }
            }
        }
    }

    if (confirm && fx != null) {
        val home = fx.home == me.id
        val opp = w.clubs[if (home) fx.away else fx.home]
        val squad = w.squad(me)
        val out = squad.filter { it.injWeeks > 0 || it.suspWeeks > 0 }
        val tired = Lineups.available(me.let { w.players[me.lineup[0]] ?: squad.first() })
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Match day: ${if (home) me.name else opp.name} v ${if (home) opp.name else me.name}") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Opponent squad ", 13); Stars(strengthStars(Lineups.strength(w, opp)), 13)
                        T("   Yours ", 13); Stars(strengthStars(Lineups.strength(w, me)), 13)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Opponent reputation ", 12, C.muted); Stars(repStars(opp.rep), 12)
                        T("   League position ${w.leagueOf(opp.id).sortedTable().indexOfFirst { it.clubId == opp.id } + 1}", 12, C.muted)
                    }
                    if (out.isNotEmpty()) T("Unavailable: " + out.joinToString { it.name }, 12, C.amber, lines = 3)
                    val lowCond = me.lineup.toList().mapNotNull { w.players[it] }.filter { it.condition < 70f }
                    if (lowCond.isNotEmpty()) T("Tired starters: " + lowCond.joinToString { "${it.name} ${it.condition.roundToInt()}%" }, 12, C.amber, lines = 3)
                    if (!tired) T("Check your goalkeeper!", 12, C.red)
                }
            },
            confirmButton = { Btn("Kick off") { confirm = false; runAdvance() } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Review team") } }
        )
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Menu") },
            text = { T("Progress is saved automatically after every week.", 13, lines = 2) },
            confirmButton = {
                Btn("Save & quit to menu") {
                    menu = false
                    scope.launch {
                        withContext(Dispatchers.IO) { Persistence.save(ctx.filesDir, w) }
                        App.mode = Mode.MENU
                    }
                }
            },
            dismissButton = { TextButton(onClick = { menu = false }) { Text("Close") } }
        )
    }
}

// ---------------------------------------------------------------- inbox

@Composable
fun InboxScreen(w: World) {
    val me = w.human()
    val lg = w.leagueOf(me.id)
    var openedNews by remember { mutableStateOf<News?>(null) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1.6f).fillMaxHeight()) {
            T("Inbox", 15, C.accent, true)
            if (w.news.isEmpty()) T("No messages.", 12, C.muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(w.news.take(60)) { n ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(C.card)
                        .clickable { openedNews = n }.padding(8.dp)) {
                        Row {
                            T(n.title, 13, if (n.read) C.text else C.accent, true, Modifier.weight(1f))
                            T("${n.year}/${(n.year + 1) % 100} wk ${n.week}", 11, C.muted)
                        }
                        T(n.body, 12, C.muted, lines = 4)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                T("Board objective", 13, C.accent, true)
                T(expectation(Game.leagueRank(w, me) + 1, 18), 12, lines = 2)
                Spacer(Modifier.height(4.dp))
                Bar(me.boardConfidence / 100f, if (me.boardConfidence < 30f) C.red else C.green, Modifier.fillMaxWidth())
                T("Confidence ${me.boardConfidence.roundToInt()}%", 11, C.muted)
                if (w.lastResult.isNotEmpty()) { Spacer(Modifier.height(4.dp)); T("Last: " + w.lastResult, 12) }
            }
            Panel(Modifier.fillMaxWidth().weight(1f)) {
                T("League snapshot", 13, C.accent, true)
                val tbl = lg.sortedTable()
                val idx = tbl.indexOfFirst { it.clubId == me.id }
                val from = (idx - 2).coerceAtLeast(0)
                val to = (from + 5).coerceAtMost(tbl.size)
                for (i in from until to) {
                    val r = tbl[i]
                    Row {
                        Cell("${i + 1}", 24, C.muted)
                        Cell(w.clubs[r.clubId].name, 150, if (r.clubId == me.id) C.accent else C.text, r.clubId == me.id)
                        Cell("${r.p}", 24); Cell("${r.pts}", 30, bold = true)
                    }
                }
            }
        }
    }
    openedNews?.let { n ->
        AlertDialog(
            onDismissRequest = { openedNews = null },
            title = { Text(n.title) },
            text = { Column {
                T("${n.year}/${(n.year + 1) % 100} · week ${n.week}", 11, C.muted)
                Spacer(Modifier.height(8.dp))
                T(n.body, 14, lines = 12)
            } },
            confirmButton = { Btn(if (n.read) "Close message" else "Mark read & close") { n.read = true; openedNews = null; App.bump() } },
            dismissButton = { TextButton(onClick = { openedNews = null }) { Text("Close unread") } }
        )
    }
}

// ---------------------------------------------------------------- finance

fun upgradeCost(c: Club, level: Int): Long = (c.revenue * 0.05 * (1 + level / 8.0)).toLong()

@Composable
fun PeopleScreen(w: World) {
    val club = w.human()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("People & dressing room", 20, C.accent, true)
        T(PlayerRelations.managerProfile(w), 13, C.muted)
        Spacer(Modifier.height(8.dp))
        T("Team cohesion ${club.teamCohesion.roundToInt()}%", 15, C.text, true)
        Bar(club.teamCohesion / 100f, C.green, Modifier.fillMaxWidth())
        val leaders = w.squad(club).sortedWith(compareByDescending<Player> { it.attrs[Attr.Leadership.ordinal] }.thenBy { it.id }).take(3)
        T("Dressing-room leaders", 14, C.text, true)
        if (leaders.isEmpty()) T("No registered players", 12, C.muted)
        leaders.forEach { T("${it.name} · leadership ${it.attrs[Attr.Leadership.ordinal]}", 12, C.muted) }
        T("Board objective", 15, C.text, true)
        val objectives = listOf("Balanced", "Promotion", "Top half", "Avoid relegation", "Financial stability")
        objectives.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = club.boardObjective == index, onClick = {
                    club.boardObjective = index
                    w.addNews("Board objective updated", "The club's current objective is now $label.")
                    App.bump()
                })
                T(label, 13)
            }
        }
        Spacer(Modifier.height(6.dp))
        T("Media briefing", 15, C.text, true)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Btn("Back squad") { PlayerRelations.mediaInteraction(w, 1); App.bump() }
            Btn("Balanced") { PlayerRelations.mediaInteraction(w, 0); App.bump() }
            Btn("Cautious") { PlayerRelations.mediaInteraction(w, -1); App.bump() }
        }
        Spacer(Modifier.height(10.dp))
        T("Player personalities & playing-time promises", 15, C.text, true)
        T("Promises last eight weeks. Repeatedly missing the promised minutes reduces morale.", 11, C.muted)
        for (player in w.squad(club).sortedWith(compareByDescending<Player> { it.ovr }.thenBy { it.id })) {
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                T("${player.name} · ${PlayerRelations.personality(player)} · morale ${player.morale.roundToInt()}%", 12, C.text, true)
                val promise = if (player.playingTimePromise == 0) "No promise" else "${PlayerRelations.promiseName(player.playingTimePromise)} (${player.promiseWeeksRemaining}w)"
                T("$promise · apps ${player.apps}", 11, C.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (level in PlayerRelations.PROMISE_ROTATION..PlayerRelations.PROMISE_STAR) {
                        Btn(listOf("", "Rot.", "Regular", "Important", "Star")[level], enabled = player.injWeeks == 0) {
                            if (PlayerRelations.promisePlayingTime(w, player.id, level)) App.bump()
                        }
                    }
                    Btn("Clear") {
                        player.playingTimePromise = PlayerRelations.PROMISE_NONE
                        player.promiseWeeksRemaining = 0
                        player.promiseBrokenWeeks = 0
                        App.bump()
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun FinanceScreen(w: World) {
    val me = w.human()
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var wageCapText by remember(me.id) { mutableStateOf(me.wageBudget.toString()) }
    val bill = w.wageBill(me)
    tick.let { }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Finances", 15, C.accent, true)
            Spacer(Modifier.height(4.dp))
            T("Balance: " + money(me.balance), 14, if (me.balance < 0) C.red else C.text, true)
            T("Annual revenue: " + money(me.revenue), 13)
            T("Transfer budget: " + money(me.transferBudget), 13)
            Spacer(Modifier.height(6.dp))
            T("Wage bill: ${money(bill)} / wk", 13)
            T("Wage budget: ${money(me.wageBudget)} / wk", 13)
            Bar(bill.toFloat() / me.wageBudget.coerceAtLeast(1L).toFloat(), if (bill > me.wageBudget) C.red else C.green, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = wageCapText,
                    onValueChange = { wageCapText = it.filter { c -> c.isDigit() }.take(10) },
                    singleLine = true,
                    label = { Text("New weekly wage cap") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Btn("Set cap", enabled = wageCapText.toLongOrNull() != null && wageCapText.toLongOrNull()!! >= 1_000L) {
                    val cap = wageCapText.toLongOrNull()!!.coerceAtMost(1_000_000_000L)
                    me.wageBudget = cap
                    msg = "Weekly wage cap set to ${money(cap)}. The board may still react to overspending."
                    tick++; App.bump()
                }
            }
            T("You can raise the cap when the club's finances allow it. This changes the board's wage limit, not the actual wage bill.", 11, C.muted, lines = 3)
            Spacer(Modifier.height(6.dp))
            T("Last week: income ${money(me.weeklyIncome)}, spend ${money(me.weeklyExpense)}", 12, C.muted, lines = 2)
            T("Season start balance: " + money(me.seasonStartBalance), 12, C.muted)
            Spacer(Modifier.height(6.dp))
            T("Hard mode notes:", 12, C.amber, true)
            T("Negative balance and wage overspend erode board confidence every week. Bankruptcy ends your job.", 12, C.muted, lines = 4)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Facilities", 15, C.accent, true)
            val items = listOf(
                Triple("Training & recovery", me.facilities, 0),
                Triple("Youth academy", me.youth, 1),
                Triple("Scouting network", me.scout, 2)
            )
            for ((label, lvl, kind) in items) {
                Spacer(Modifier.height(8.dp))
                T("$label: $lvl / 20", 13, bold = true)
                val cost = upgradeCost(me, lvl)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Upgrade: " + money(cost), 12, C.muted, modifier = Modifier.weight(1f))
                    Btn("Upgrade", enabled = lvl < 20 && me.balance > cost * 2) {
                        me.balance -= cost
                        when (kind) { 0 -> me.facilities++; 1 -> me.youth++; else -> me.scout++ }
                        msg = "$label upgraded."
                        tick++
                        App.bump()
                    }
                }
            }
            if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); T(msg, 12, C.green) }
        }
    }
}

// ---------------------------------------------------------------- club / scouting

@Composable
fun ClubScreen(w: World) {
    val me = w.human()
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Scouting assignment", 15, C.accent, true)
            T("Your scouts fully assess players each week. Unscouted players show fuzzy attribute ranges.", 12, C.muted, lines = 3)
            Spacer(Modifier.height(6.dp))
            T("Search where", 12, C.muted)
            val opts = listOf("Off", "Free agents") + (1..7).map { "L$it" }
            val cur = when (w.scoutLeague) { -1 -> 0; -2 -> 1; else -> w.scoutLeague + 2 }
            Seg(opts, cur, { i ->
                w.scoutLeague = when (i) { 0 -> -1; 1 -> -2; else -> i - 2 }
                tick++
            })
            Spacer(Modifier.height(6.dp))
            T("Position", 12, C.muted)
            val popts = listOf("Any") + Pos.values().map { it.label }
            Seg(popts, w.scoutPos + 1, { i -> w.scoutPos = i - 1; tick++ })
            Spacer(Modifier.height(6.dp))
            T("Scouted players known: ${w.scouted.size}", 12)
            T("Scouting level: ${me.scout}/20", 12, C.muted)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Club", 15, C.accent, true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Reputation ", 13); Stars(repStars(me.rep), 13)
                T("   |  Stadium ${me.capacity()}", 13)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Squad rating ", 13); Stars(strengthStars(Lineups.strength(w, me)), 13)
            }
            T("Seasons managed: ${w.seasonsManaged}   League titles: ${w.trophies}", 13)
            Spacer(Modifier.height(8.dp))
            T("Roll of honour (latest first)", 12, C.accent, true)
            if (w.history.isEmpty()) T("No completed seasons yet.", 12, C.muted)
            for (h in w.history.take(14)) T(h, 11, C.muted, lines = 2)
        }
    }
}

// ---------------------------------------------------------------- sacked

@Composable
fun SackedScreen(w: World) {
    val ctx = LocalContext.current
    val scope = App.scope
    val old = w.human()
    val jobs = remember {
        val rnd = kotlin.random.Random(w.seed + w.year)
        w.clubs.filter { it.id != old.id && it.rep <= old.rep + 6 && !it.human }.shuffled(rnd).take(8)
            .sortedByDescending { it.rep }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        T("You have been sacked", 26, C.red, true)
        T(w.sackReason, 14, C.muted, lines = 2)
        Spacer(Modifier.height(10.dp))
        T("Clubs willing to hire you:", 14, C.accent, true)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(jobs) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(C.card)
                        .clickable {
                            scope.launch {
                                heavy("Moving to ${c.name}...") {
                                    old.human = false
                                    old.autoPick = true
                                    c.human = true
                                    c.boardConfidence = 60f
                                    c.autoPick = true
                                    w.humanClubId = c.id
                                    w.sacked = false
                                    w.sackReason = ""
                                    w.offers.clear()
                                    w.addNews("New job", "You are the new manager of ${c.name}.")
                                    Lineups.autoPick(w, c)
                                }
                                withContext(Dispatchers.IO) { Persistence.save(ctx.filesDir, w) }
                                App.screen = "Inbox"
                                App.bump()
                            }
                        }.padding(10.dp)
                ) {
                    T(c.name, 14, bold = true, modifier = Modifier.weight(1f))
                    T(w.leagues[c.tier].name, 12, C.muted, modifier = Modifier.weight(1f))
                    Stars(repStars(c.rep), 12)
                }
            }
        }
        Btn("Retire to the main menu") {
            scope.launch {
                val archived = withContext(Dispatchers.IO) { Persistence.delete(ctx.filesDir) }
                if (archived) {
                    App.mode = Mode.MENU
                    App.world = null
                } else {
                    // Keep the current career open if the archive rename fails.
                    App.bump()
                }
            }
        }
    }
}
