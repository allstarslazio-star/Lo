package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Search, filter, sort and compare players. Uses the live model objects; nothing is copied into UI state. */
@Composable
fun PlayersScreen(w: World) {
    val me = w.human()
    var filter by remember { mutableStateOf(PlayerFilter()) }
    var sort by remember { mutableStateOf(PlayerSort(SortKey.OVERALL)) }
    var scope by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<Player?>(null) }
    var full by remember { mutableStateOf<Player?>(null) }
    var compare by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showCompare by remember { mutableStateOf(false) }

    val focus = App.focusPlayerId
    LaunchedEffect(focus) {
        if (focus != null) { profile = w.players[focus]; App.focusPlayerId = null }
    }

    val result = remember(w, tick, App.tick, filter, sort, scope) {
        val source: List<Player> = when (scope) {
            0 -> w.squad(me)
            1 -> w.shortlist.mapNotNull { w.players[it] }
            else -> w.players.values.toList()
        }
        PlayerQuery.run(w, source, filter, sort, overallOf = { shownOvr(w, it) }, potentialOf = { it.pa })
    }
    val shown = if (scope == 2) result.players.take(300) else result.players

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filter.text, onValueChange = { filter = filter.copy(text = it.take(30)) }, singleLine = true,
                label = { Text("Search name") }, modifier = Modifier.weight(1f).describe("Search players by name")
            )
            Seg(listOf("My squad", "Shortlist", "Everyone"), scope, { scope = it })
            Btn("Compare (${compare.size}/2)", enabled = compare.size == 2, modifier = Modifier.minTouch()) { showCompare = true }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (pos in Pos.values()) {
                ToggleChip(pos.label, pos in filter.positions, "Position ${pos.label}") {
                    filter = filter.copy(positions = if (pos in filter.positions) filter.positions - pos else filter.positions + pos)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            val fit = listOf(0, 70, 90)
            LabeledSeg("Fitness", listOf("Any", "70%+", "90%+"), fit.indexOf(filter.minFitness).coerceAtLeast(0)) { filter = filter.copy(minFitness = fit[it]) }
            val mor = listOf(0, 50, 70)
            LabeledSeg("Morale", listOf("Any", "50+", "70+"), mor.indexOf(filter.minMorale).coerceAtLeast(0)) { filter = filter.copy(minMorale = mor[it]) }
            LabeledSeg("Contract", ContractFilter.values().map { it.label }, filter.contract.ordinal) { filter = filter.copy(contract = ContractFilter.values()[it]) }
            LabeledSeg("Availability", AvailabilityFilter.values().map { it.label }, filter.availability.ordinal) { filter = filter.copy(availability = AvailabilityFilter.values()[it]) }
            Btn("Clear filters", enabled = !filter.isDefault, modifier = Modifier.minTouch()) { filter = PlayerFilter() }
        }
        T("${shown.size} of ${result.totalBeforeFilter} shown" + if (scope == 2 && result.players.size > 300) " (top 300; refine the search)" else "", 11, C.muted)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(820.dp).fillMaxHeight()) {
                Row(Modifier.background(C.surface).padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Cell("", 34)
                    BrowserHead("Pos", 44, SortKey.POSITION, sort) { sort = sort.toggled(it) }
                    BrowserHead("Name", 170, SortKey.NAME, sort) { sort = sort.toggled(it) }
                    BrowserHead("Age", 40, SortKey.AGE, sort) { sort = sort.toggled(it) }
                    BrowserHead("Ovr", 42, SortKey.OVERALL, sort) { sort = sort.toggled(it) }
                    BrowserHead("Pot", 42, SortKey.POTENTIAL, sort) { sort = sort.toggled(it) }
                    BrowserHead("Fit", 44, SortKey.FITNESS, sort) { sort = sort.toggled(it) }
                    BrowserHead("Mor", 44, SortKey.MORALE, sort) { sort = sort.toggled(it) }
                    BrowserHead("Value", 76, SortKey.VALUE, sort) { sort = sort.toggled(it) }
                    BrowserHead("Wage", 64, SortKey.WAGE, sort) { sort = sort.toggled(it) }
                    Cell("Ends", 44, C.muted); Cell("Status", 70, C.muted)
                }
                val message = result.emptyMessage
                if (message != null) {
                    EmptyState(message, if (!filter.isDefault) "Clear filters" else null, { filter = PlayerFilter() })
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { it.id }) { p ->
                            val checked = p.id in compare
                            Row(
                                Modifier.fillMaxWidth().minTouch().clickable(onClickLabel = "Open ${p.name}") { profile = p }
                                    .describe("${p.name}, ${p.pos.label}, age ${p.age}, overall ${shownOvr(w, p)}, fitness ${p.condition.roundToInt()} percent. Double tap for profile.")
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { on ->
                                    compare = if (on) (compare + p.id).takeLast(2) else compare - p.id
                                }, modifier = Modifier.width(34.dp))
                                Cell(p.pos.label + (p.pos2?.let { "/" + it.label.take(2) } ?: ""), 44, C.muted)
                                Cell(p.name, 170, C.text, p.listed)
                                Cell("${p.age}", 40)
                                Cell("${shownOvr(w, p)}", 42, ovrColor(shownOvr(w, p)), true)
                                Cell(potText(w, p), 42, C.muted)
                                Cell("${p.condition.roundToInt()}%", 44, if (p.condition < 70f) C.amber else C.text)
                                Cell("${p.morale.roundToInt()}", 44, if (p.morale < 45f) C.red else C.text)
                                Cell(money(p.value()), 76)
                                Cell(if (p.clubId < 0) "-" else "${p.wage}", 64)
                                Cell(if (p.clubId < 0) "-" else "${p.contractEnd}", 44, if (p.clubId >= 0 && p.contractEnd <= w.year + 1) C.amber else C.text)
                                Cell(statusText(p), 70, C.red)
                            }
                            HorizontalDivider(color = C.line)
                        }
                    }
                }
            }
        }
    }

    profile?.let { p ->
        PlayerProfileDialog(w, p, onClose = { profile = null }, onOpenFull = { full = p; profile = null }) { tick++; App.bump() }
    }
    full?.let { p -> PlayerDialog(w, p, { full = null }) { tick++; App.bump() } }
    if (showCompare && compare.size == 2) {
        val a = w.players[compare[0]]
        val b = w.players[compare[1]]
        if (a != null && b != null) CompareDialog(w, a, b) { showCompare = false } else showCompare = false
    }
}

@Composable
private fun BrowserHead(label: String, width: Int, key: SortKey, sort: PlayerSort, onSort: (SortKey) -> Unit) {
    val active = sort.key == key
    val arrow = if (!active) "" else if (sort.descending) " \u25BC" else " \u25B2"
    Box(
        Modifier.width(width.dp).minTouch().clickable(onClickLabel = "Sort by ${key.label}") { onSort(key) }
            .describe("Sort by ${key.label}" + if (active) (if (sort.descending) ", descending" else ", ascending") else ""),
        contentAlignment = Alignment.CenterStart
    ) { T(label + arrow, 12, if (active) C.accent else C.muted, true) }
}

// ---------------------------------------------------------------- profile with the six planned sections

@Composable
fun PlayerProfileDialog(w: World, p: Player, onClose: () -> Unit, onOpenFull: () -> Unit, onChanged: () -> Unit) {
    val me = w.human()
    val own = p.clubId == me.id
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f), shape = RoundedCornerShape(2.dp), color = C.surface) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(p.name, 18, C.accent, true)
                        T("${Names.nations[p.nation].name} | age ${p.age} | ${p.pos.label}" + (p.pos2?.let { " / " + it.label } ?: "") +
                            " | " + (if (p.clubId >= 0) w.clubs[p.clubId].name else "Free agent"), 12, C.muted)
                    }
                    Btn("Close", modifier = Modifier.minTouch()) { onClose() }
                }
                Spacer(Modifier.height(6.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileSection("Overview") {
                        T("Overall ${shownOvr(w, p)}" + (if (!isKnown(w, p)) " (estimate)" else ""), 14, ovrColor(shownOvr(w, p)), true)
                        val top = (0 until NATTR).filter { ATTRS[it].cat != 3 || p.pos == Pos.GK }.sortedByDescending { shownAttr(w, p, it) }.take(5)
                        T("Strongest: " + top.joinToString { "${ATTRS[it].label} ${attrText(w, p, it)}" }, 12, C.muted, lines = 3)
                    }
                    ProfileSection("Development") {
                        T("Potential ${potText(w, p)} stars", 13)
                        T(if (p.age <= 23) "Still developing; training and match time matter most." else if (p.age <= 29) "In his prime." else "Declining; physical attributes fall first.", 12, C.muted, lines = 2)
                        T("Training: ${p.trainingFocus}, intensity ${listOf("light", "normal", "intensive")[p.trainingIntensity.coerceIn(0, 2)]}", 12, C.muted)
                    }
                    ProfileSection("Physical condition") {
                        T("Fitness ${p.condition.roundToInt()}%   Sharpness ${p.sharpness.roundToInt()}%", 13, if (p.condition < 65f) C.amber else C.text)
                        if (p.injWeeks > 0) T("Injured: ${p.injType.ifEmpty { "injury" }}, ${p.injWeeks} week(s)", 12, C.red)
                        else if (p.suspWeeks > 0) T("Suspended for ${p.suspWeeks} match(es)", 12, C.red)
                        else T("Available", 12, C.green)
                    }
                    ProfileSection("Psychology") {
                        T("${PlayerRelations.personality(p)} | morale ${p.morale.roundToInt()} | form ${p.form.roundToInt()}", 13, lines = 2)
                        val promise = if (p.playingTimePromise == 0) "No playing-time promise" else "${PlayerRelations.promiseName(p.playingTimePromise)} promise, ${p.promiseWeeksRemaining} week(s) left"
                        T(promise, 12, C.muted)
                        if (own) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (level in PlayerRelations.PROMISE_ROTATION..PlayerRelations.PROMISE_STAR) {
                                    Btn(PlayerRelations.promiseName(level), enabled = p.injWeeks == 0, modifier = Modifier.minTouch()) {
                                        if (PlayerRelations.promisePlayingTime(w, p.id, level)) { note = "Promised ${PlayerRelations.promiseName(level).lowercase()} football."; onChanged() }
                                    }
                                }
                            }
                        }
                    }
                    ProfileSection("Contract") {
                        if (p.clubId < 0) T("No contract. Value ${money(p.value())}.", 13)
                        else {
                            T("Wage ${money(p.wage.toLong())}/wk until ${p.contractEnd}", 13)
                            T("Value ${money(p.value())} | wage demand ${money(p.wageDemand().toLong())}/wk", 12, C.muted)
                            if (p.contractEnd <= w.year + 1) T("Contract expires at the end of this season.", 12, C.amber)
                        }
                    }
                    ProfileSection("Performance") {
                        T("${p.apps} apps | ${p.goals} goals | ${p.assists} assists | ${p.minutes} min", 13, lines = 2)
                        T(if (p.apps == 0) "No rating yet" else "Average rating ${"%.2f".format(p.avgRating())} / 10", 12, C.muted)
                    }
                }
                if (note.isNotEmpty()) T(note, 12, C.green)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn(if (own) "Contract & transfer options" else "Bid / sign / shortlist", modifier = Modifier.minTouch()) { onOpenFull() }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(C.card).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(title, 13, C.accent, true)
        content()
    }
}

// ---------------------------------------------------------------- comparison

@Composable
fun CompareDialog(w: World, a: Player, b: Player, onClose: () -> Unit) {
    val cmp = remember(a.id, b.id) { PlayerComparison.compare(w, a, b, attrOf = { p, i -> shownAttr(w, p, i) }, overallOf = { shownOvr(w, it) }) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f), shape = RoundedCornerShape(2.dp), color = C.surface) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Compare", 16, C.accent, true)
                        T(cmp.verdict(a.name, b.name), 12, C.muted, lines = 2)
                    }
                    Btn("Close", modifier = Modifier.minTouch()) { onClose() }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Cell("", 130); Cell(a.name, 150, C.accent, true); Cell(b.name, 150, C.accent, true)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(cmp.rows) { r ->
                        Row(Modifier.fillMaxWidth().minTouch().describe("${r.label}: ${a.name} ${r.a}, ${b.name} ${r.b}" + when (r.winner) { -1 -> ", ${a.name} better"; 1 -> ", ${b.name} better"; else -> "" }), verticalAlignment = Alignment.CenterVertically) {
                            Cell(r.label, 130, C.muted)
                            Cell(r.a, 150, if (r.winner < 0) C.green else C.text, r.winner < 0)
                            Cell(r.b, 150, if (r.winner > 0) C.green else C.text, r.winner > 0)
                        }
                        HorizontalDivider(color = C.line)
                    }
                }
            }
        }
    }
}
