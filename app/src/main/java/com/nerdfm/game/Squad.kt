package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun isKnown(w: World, p: Player): Boolean = p.clubId == w.humanClubId || w.scouted.contains(p.id)

fun scoutErr(w: World): Float = (20 - w.human().scout) / 10f

fun shownAttr(w: World, p: Player, i: Int): Int {
    if (isKnown(w, p)) return p.attrs[i]
    val noise = (((p.id * 7919 + i * 104729) % 5) + 5) % 5 - 2
    return clampI(p.attrs[i] + (noise * scoutErr(w)).roundToInt(), 1, 20)
}

fun attrText(w: World, p: Player, i: Int): String {
    val s = shownAttr(w, p, i)
    if (isKnown(w, p)) return "$s"
    val e = ceil(scoutErr(w) * 1.5f).toInt()
    return if (e <= 0) "$s" else "${max(1, s - e)}-${min(20, s + e)}"
}

fun shownOvr(w: World, p: Player): Int {
    if (isKnown(w, p)) return p.ovr
    val a = IntArray(NATTR) { shownAttr(w, p, it) }
    return Roles.overall(a, p.pos).roundToInt()
}

fun potText(w: World, p: Player): String {
    if (isKnown(w, p)) return "%.1f".format(p.pa / 20f)
    val lo = max(0f, (p.pa - 10) / 20f)
    val hi = min(5f, (p.pa + 10) / 20f)
    return "%.1f-%.1f".format(lo, hi)
}

fun statusText(p: Player): String = when {
    p.injWeeks > 0 -> "Inj ${p.injWeeks}w"
    p.suspWeeks > 0 -> "Susp"
    else -> ""
}

// ------------------------------------------------------------------ squad list

@Composable
fun SquadScreen(w: World) {
    val me = w.human()
    var sel by remember { mutableStateOf<Player?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var sortKey by remember { mutableStateOf("pos") }
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    var list = w.squad(me)
    list = when (tab) {
        1 -> list.filter { it.pos.group == 0 }
        2 -> list.filter { it.pos.group == 1 }
        3 -> list.filter { it.pos.group == 2 }
        4 -> list.filter { it.pos.group >= 3 }
        else -> list
    }
    list = when (sortKey) {
        "ovr" -> list.sortedByDescending { it.ovr }
        "age" -> list.sortedBy { it.age }
        "pot" -> list.sortedByDescending { it.pa }
        "cond" -> list.sortedBy { it.condition }
        "wage" -> list.sortedByDescending { it.wage }
        "rtg" -> list.sortedByDescending { it.avgRating() }
        "name" -> list.sortedBy { it.name }
        else -> list.sortedWith(compareBy<Player> { it.pos.ordinal }.thenByDescending { it.ovr })
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seg(listOf("All", "GK", "DEF", "MID", "ATT"), tab, { tab = it })
            Spacer(Modifier.width(10.dp))
            T("${me.players.size} players | wage bill ${money(w.wageBill(me))}/wk (budget ${money(me.wageBudget)})", 12, C.muted)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(860.dp).fillMaxHeight()) {
                Row(Modifier.background(C.surface).padding(vertical = 4.dp, horizontal = 4.dp)) {
                    HeadCell("Pos", 40, "pos", sortKey) { sortKey = it }
                    HeadCell("Name", 170, "name", sortKey) { sortKey = it }
                    HeadCell("Age", 38, "age", sortKey) { sortKey = it }
                    HeadCell("Ovr", 40, "ovr", sortKey) { sortKey = it }
                    HeadCell("Pot", 40, "pot", sortKey) { sortKey = it }
                    HeadCell("Cond", 48, "cond", sortKey) { sortKey = it }
                    Cell("Mor", 40, C.muted); Cell("Frm", 40, C.muted)
                    Cell("Apps", 42, C.muted); Cell("G", 30, C.muted); Cell("A", 30, C.muted)
                    HeadCell("Rtg", 42, "rtg", sortKey) { sortKey = it }
                    HeadCell("Wage", 60, "wage", sortKey) { sortKey = it }
                    Cell("Ends", 44, C.muted); Cell("Status", 70, C.muted)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(list, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { sel = p }.padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cell(p.pos.label + (p.pos2?.let { "/" + it.label.take(2) } ?: ""), 40, C.muted)
                            Cell(p.name, 170, C.text, p.listed)
                            Cell("${p.age}", 38)
                            Cell("${p.ovr}", 40, ovrColor(p.ovr), true)
                            Cell(potText(w, p), 40, C.muted)
                            Cell("${p.condition.roundToInt()}%", 48, if (p.condition < 70f) C.amber else C.text)
                            Cell("${p.morale.roundToInt()}", 40, if (p.morale < 45f) C.red else C.text)
                            Cell("${p.form.roundToInt()}", 40)
                            Cell("${p.apps}", 42); Cell("${p.goals}", 30); Cell("${p.assists}", 30)
                            Cell(if (p.apps == 0) "-" else "%.2f".format(p.avgRating()), 42)
                            Cell("${p.wage}", 60)
                            Cell("${p.contractEnd}", 44, if (p.contractEnd <= w.year + 1) C.amber else C.text)
                            Cell(statusText(p), 70, C.red)
                        }
                        HorizontalDivider(color = C.line)
                    }
                }
            }
        }
    }
    sel?.let { p -> PlayerDialog(w, p, { sel = null }) { tick++; App.bump() } }
}

@Composable
fun HeadCell(label: String, w: Int, key: String, cur: String, onSort: (String) -> Unit) {
    Box(Modifier.width(w.dp).clickable { onSort(key) }) {
        T(label, 12, if (cur == key) C.accent else C.muted, true)
    }
}

// ------------------------------------------------------------------ player dialog

@Composable
fun PlayerDialog(w: World, p: Player, onClose: () -> Unit, onChanged: () -> Unit) {
    val me = w.human()
    val own = p.clubId == me.id
    var step by remember { mutableStateOf("view") }
    var msg by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf(0L) }
    val clubName = if (p.clubId >= 0) w.clubs[p.clubId].name else "Free agent"

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f), shape = RoundedCornerShape(2.dp), color = C.surface
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(p.name, 18, C.accent, true)
                        T("${Names.nations[p.nation].name}  |  age ${p.age}  |  ${p.pos.label}" + (p.pos2?.let { " / " + it.label } ?: "") + "  |  $clubName", 12, C.muted)
                    }
                    Btn("Close") { onClose() }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // attributes
                    Row(Modifier.weight(2.3f).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val cats = listOf("Technical", "Mental", "Physical", "Goalkeeping")
                        for (cat in 0..3) {
                            Column(Modifier.weight(1f)) {
                                T(cats[cat], 12, C.accent, true)
                                for (i in 0 until NATTR) {
                                    if (ATTRS[i].cat != cat) continue
                                    val v = shownAttr(w, p, i)
                                    Row {
                                        T(ATTRS[i].label, 11, C.muted, modifier = Modifier.weight(1f))
                                        T(attrText(w, p, i), 12, attrColor(v), true)
                                    }
                                }
                            }
                        }
                    }
                    // summary + actions
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) {
                        T("Overall ${shownOvr(w, p)}" + (if (!isKnown(w, p)) " (est.)" else ""), 16, ovrColor(shownOvr(w, p)), true)
                        T("Potential ${potText(w, p)} stars", 12)
                        T("Fitness ${p.condition.roundToInt()}%  Sharpness ${p.sharpness.roundToInt()}%", 12, if (p.condition < 65f || p.sharpness < 50f) C.amber else C.muted, lines = 2)
                        T("Morale ${p.morale.roundToInt()}  Form ${p.form.roundToInt()}", 12, C.muted)
                        T("Value ${money(p.value())}  |  Wage demand ${money(p.wageDemand().toLong())}/wk", 12)
                        T("Career: ${p.apps} apps  |  ${p.goals} goals  |  ${p.assists} assists  |  ${p.minutes} minutes", 12, C.muted, lines = 2)
                        if (p.apps > 0) T("Average rating ${"%.2f".format(p.avgRating())} / 10", 12, C.accent)
                        T("Career performance", 13, C.accent, true)
                        T("Minutes ${p.minutes}  |  Shots ${p.seasonShots}  |  On target ${p.seasonShotsOnTarget}", 11, C.muted, lines = 2)
                        T("xG ${"%.2f".format(p.seasonXg)}  |  Key passes ${p.seasonKeyPasses}", 11, C.muted, lines = 2)
                        T("Tackles ${p.seasonTackles}  |  Interceptions ${p.seasonInterceptions}  |  Saves ${p.seasonSaves}", 11, C.muted, lines = 2)
                        if (own) {
                            T("Wage ${money(p.wage.toLong())}/wk  until ${p.contractEnd}", 12)
                            T("Season: ${p.apps} apps, ${p.goals} goals, ${p.assists} assists", 12, C.muted, lines = 2)
                        }
                        if (p.injWeeks > 0) T("Injured: ${p.injType} (${p.injWeeks} wks)", 12, C.red, lines = 2)
                        if (p.suspWeeks > 0) T("Suspended (${p.suspWeeks})", 12, C.red)
                        if (!isKnown(w, p)) T("Not scouted: attributes are estimates.", 11, C.amber, lines = 2)
                        Spacer(Modifier.height(8.dp))
                        if (own) {
                            Btn("Renew contract", modifier = Modifier.fillMaxWidth()) { step = "renew"; msg = "" }
                            Spacer(Modifier.height(4.dp))
                            Btn(if (p.listed) "Remove from transfer list" else "List for transfer", modifier = Modifier.fillMaxWidth()) {
                                p.listed = !p.listed; onChanged()
                            }
                            Spacer(Modifier.height(4.dp))
                            Btn("Release", modifier = Modifier.fillMaxWidth()) { step = "release"; msg = "" }
                        } else {
                            val inList = w.shortlist.contains(p.id)
                            Btn(if (inList) "Remove from shortlist" else "Add to shortlist", modifier = Modifier.fillMaxWidth()) {
                                if (inList) w.shortlist.remove(p.id) else w.shortlist.add(p.id)
                                onChanged()
                            }
                            Spacer(Modifier.height(4.dp))
                            if (p.clubId < 0) {
                                Btn("Sign (free agent)", modifier = Modifier.fillMaxWidth()) { fee = 0L; step = "terms"; msg = "" }
                            } else {
                                Btn("Make a bid", modifier = Modifier.fillMaxWidth()) {
                                    fee = Transfers.askingPrice(w, p, me); step = "bid"; msg = ""
                                }
                            }
                        }
                        if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); T(msg, 12, C.amber, lines = 5) }
                    }
                }
            }
        }
    }

    when (step) {
        "bid" -> {
            var counter by remember { mutableStateOf(0L) }
            var feeText by remember { mutableStateOf(fee.toString()) }
            AlertDialog(
                onDismissRequest = { step = "view" },
                title = { Text("Bid for ${p.name}") },
                text = {
                    Column {
                        T("Value ${money(p.value())}. Budget ${money(me.transferBudget)}.", 12, C.muted, lines = 2)
                        OutlinedTextField(
                            value = feeText, onValueChange = { feeText = it.filter { c -> c.isDigit() }.take(10) },
                            singleLine = true, label = { Text("Fee") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (msg.isNotEmpty()) T(msg, 12, C.amber, lines = 4)
                    }
                },
                confirmButton = {
                    Btn("Submit bid") {
                        val f = feeText.toLongOrNull() ?: 0L
                        val r = Transfers.bid(w, p, f)
                        msg = r.msg
                        if (r.status == 0) { fee = f; step = "terms"; msg = "" }
                        else if (r.status == 2) { feeText = r.counter.toString() }
                    }
                },
                dismissButton = { TextButton(onClick = { step = "view"; msg = "" }) { Text("Cancel") } }
            )
        }
        "terms", "renew" -> {
            val renew = step == "renew"
            val wanted = if (renew) p.wageDemand() else Transfers.wantedWage(me, p)
            var wageText by remember { mutableStateOf(wanted.toString()) }
            var bonusText by remember { mutableStateOf("0") }
            var years by remember { mutableIntStateOf(3) }
            AlertDialog(
                onDismissRequest = { step = "view" },
                title = { Text(if (renew) "New contract: ${p.name}" else "Contract terms: ${p.name}") },
                text = {
                    Column {
                        if (!renew) T("Player interest: ${Transfers.interestLabel(me, p)}. ${Transfers.interestReason(me, p)}", 12, if (Transfers.willJoin(me, p)) C.green else C.red, lines = 3)
                        T("Expected wage about ${money(wanted.toLong())} / week", 12, C.muted)
                        OutlinedTextField(
                            value = wageText, onValueChange = { wageText = it.filter { c -> c.isDigit() }.take(7) },
                            singleLine = true, label = { Text("Weekly wage") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = bonusText, onValueChange = { bonusText = it.filter(Char::isDigit).take(10) },
                            singleLine = true, label = { Text("One-off signing bonus") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.height(6.dp))
                        Seg(listOf("1 yr", "2 yrs", "3 yrs", "4 yrs", "5 yrs"), years - 1, { years = it + 1 })
                        if (msg.isNotEmpty()) T(msg, 12, C.amber, lines = 4)
                    }
                },
                confirmButton = {
                    Btn(if (renew) "Offer deal" else "Sign player") {
                        val wage = wageText.toIntOrNull() ?: 0
                        val bonus = bonusText.toLongOrNull() ?: 0L
                        val r = if (renew) Transfers.renew(w, p, wage, years, bonus) else Transfers.completeSigning(w, p, fee, wage, years, bonus)
                        if (r.first) { step = "view"; onChanged(); if (!renew) onClose() } else msg = r.second
                    }
                },
                dismissButton = { TextButton(onClick = { step = "view"; msg = "" }) { Text("Cancel") } }
            )
        }
        "release" -> AlertDialog(
            onDismissRequest = { step = "view" },
            title = { Text("Release ${p.name}?") },
            text = { T("You must pay a settlement on his remaining contract.", 13, lines = 3) },
            confirmButton = {
                Btn("Release") {
                    if (me.players.size <= 16) { msg = "Squad too small to release anyone."; step = "view" }
                    else { Transfers.release(w, p); step = "view"; onChanged(); onClose() }
                }
            },
            dismissButton = { TextButton(onClick = { step = "view" }) { Text("Cancel") } }
        )
    }
}
