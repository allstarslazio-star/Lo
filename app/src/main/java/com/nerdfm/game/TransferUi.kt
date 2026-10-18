package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun PlayerRow(w: World, p: Player, onClick: () -> Unit) {
    val me = w.human()
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cell(p.pos.label, 36, C.muted)
        PlayerTag(w, p, 190.dp)
        Cell("${p.age}", 30)
        Cell(if (p.clubId >= 0) w.clubs[p.clubId].name else "Free agent", 150, C.muted)
        val o = shownOvr(w, p)
        Cell("$o" + if (isKnown(w, p)) "" else "?", 38, ovrColor(o), true)
        Cell(potText(w, p), 56, C.muted)
        Cell(money(p.value()), 70)
        if (p.clubId >= 0) Cell(money(Transfers.askingPrice(w, p, me)), 70, C.amber) else Cell("Free", 70, C.green)
        Cell(if (w.shortlist.contains(p.id)) "*" else "", 16, C.accent)
    }
    HorizontalDivider(color = C.line)
}

@Composable
fun TransferScreen(w: World) {
    val me = w.human()
    var tab by remember { mutableIntStateOf(w.transferSearchTab) }
    var sel by remember { mutableStateOf<Player?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var lgIdx by remember { mutableIntStateOf(w.transferFilterLeague) }
    var posIdx by remember { mutableIntStateOf(w.transferFilterPosition) }
    var minOvr by remember { mutableIntStateOf(w.transferFilterMinOverall) }
    var maxAge by remember { mutableIntStateOf(w.transferFilterMaxAge) }
    var msg by remember { mutableStateOf("") }
    var counterOffer by remember { mutableStateOf<Offer?>(null) }
    tick.let { }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seg(listOf("Search", "Free agents", "Shortlist", "Offers (${w.offers.size})"), tab, { tab = it; w.transferSearchTab = it })
            Spacer(Modifier.width(10.dp))
            T(w.windowText() + "  |  Budget " + money(me.transferBudget), 12, if (w.isWindowOpen()) C.green else C.amber)
        }
        Spacer(Modifier.height(6.dp))
        if (tab <= 1) {
            val results = remember(tab, lgIdx, posIdx, minOvr, maxAge, tick, w.week, w.year) {
                w.players.values.filter { p ->
                    val fa = p.clubId < 0
                    (if (tab == 1) fa else (!fa && p.clubId != me.id)) &&
                        (tab == 1 || lgIdx == 0 || w.clubs[p.clubId].tier == lgIdx - 1) &&
                        (posIdx == 0 || p.pos.ordinal == posIdx - 1) &&
                        p.age <= maxAge && shownOvr(w, p) >= minOvr
                }.sortedByDescending { shownOvr(w, it) }.take(60)
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Panel(Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    T("Filters", 13, C.accent, true)
                    if (tab == 0) {
                        T("League", 11, C.muted)
                        Seg(listOf("Any") + (1..7).map { "$it" }, lgIdx, { lgIdx = it; w.transferFilterLeague = it })
                    }
                    Spacer(Modifier.height(4.dp))
                    T("Position", 11, C.muted)
                    val plist = listOf("Any") + Pos.values().map { it.label }
                    // two rows to fit width
                    Seg(plist.take(6), if (posIdx < 6) posIdx else -1, { posIdx = it; w.transferFilterPosition = it })
                    Spacer(Modifier.height(3.dp))
                    Seg(plist.drop(6), if (posIdx >= 6) posIdx - 6 else -1, { posIdx = it + 6; w.transferFilterPosition = it + 6 })
                    Spacer(Modifier.height(6.dp))
                    T("Min overall (est.): $minOvr", 11, C.muted)
                    Slider(value = minOvr.toFloat(), onValueChange = { minOvr = it.roundToInt(); w.transferFilterMinOverall = minOvr }, valueRange = 20f..90f)
                    T("Max age: $maxAge", 11, C.muted)
                    Slider(value = maxAge.toFloat(), onValueChange = { maxAge = it.roundToInt(); w.transferFilterMaxAge = maxAge }, valueRange = 17f..38f)
                    T("Player interest is shown before contract talks. Filters stay saved until you change them.", 10, C.muted, lines = 4)
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Row {
                        Cell("Pos", 36, C.muted); Cell("Name", 190, C.muted); Cell("Age", 30, C.muted)
                        Cell("Club", 150, C.muted); Cell("Ovr", 38, C.muted); Cell("Pot", 56, C.muted)
                        Cell("Value", 70, C.muted); Cell("Asking", 70, C.muted)
                    }
                    if (results.isEmpty()) T("No players match.", 12, C.muted)
                    LazyColumn {
                        items(results, key = { it.id }) { p -> PlayerRow(w, p) { sel = p } }
                    }
                }
            }
        } else if (tab == 2) {
            Panel(Modifier.fillMaxSize()) {
                val list = w.shortlist.mapNotNull { w.players[it] }
                if (list.isEmpty()) T("Shortlist is empty. Open a player and tap 'Add to shortlist'.", 12, C.muted)
                LazyColumn { items(list, key = { it.id }) { p -> PlayerRow(w, p) { sel = p } } }
            }
        } else {
            Panel(Modifier.fillMaxSize()) {
                if (w.offers.isEmpty()) T("No offers right now. Listing players for transfer attracts bids.", 12, C.muted, lines = 2)
                if (msg.isNotEmpty() && counterOffer == null) T(msg, 12, C.amber, lines = 2)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(w.offers.toList()) { o ->
                        val p = w.players[o.playerId]
                        if (p != null) {
                            Row(Modifier.fillMaxWidth().background(C.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlayerPortrait(w, p, 44.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    T("${p.name} (${p.pos.label}, ${p.ovr})", 13, bold = true)
                                    T("${w.clubs[o.fromClub].name} offer ${money(o.fee)}  |  value ${money(p.value())}", 12, C.muted, lines = 2)
                                }
                                Btn("Accept") { msg = Transfers.acceptOffer(w, o); tick++; App.bump() }
                                Spacer(Modifier.width(4.dp))
                                Btn("Counter") { counterOffer = o }
                                Spacer(Modifier.width(4.dp))
                                Btn("Reject") { w.offers.remove(o); w.addNews("Transfer offer rejected", "You rejected ${w.clubs[o.fromClub].name}'s offer for ${p.name}."); tick++; App.bump() }
                            }
                        }
                    }
                }
            }
        }
    }
    counterOffer?.let { offer ->
        val player = w.players[offer.playerId]
        var feeText by remember(offer.playerId, offer.fee) { mutableStateOf((offer.fee * 1.15).toLong().toString()) }
        AlertDialog(
            onDismissRequest = { counterOffer = null },
            title = { Text("Negotiate transfer fee") },
            text = {
                Column {
                    T("Original bid: ${money(offer.fee)}", 12, C.muted)
                    OutlinedTextField(value = feeText, onValueChange = { feeText = it.filter(Char::isDigit).take(12) }, singleLine = true, label = { Text("Your counteroffer") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    T("Counteroffers depend on the player's value and the buying club's reputation and budget.", 11, C.muted, lines = 3)
                    if (msg.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        T(msg, 12, if (msg.contains("accepted", true)) C.green else C.amber, lines = 4)
                    }
                }
            },
            confirmButton = { Btn("Send counteroffer") {
                msg = Transfers.counterOffer(w, offer, feeText.toLongOrNull() ?: 0L)
                if (w.offers.none { it === offer }) counterOffer = null else if (offer.fee > 0L && msg.contains("accepted", true)) counterOffer = null
                tick++; App.bump()
            } },
            dismissButton = { TextButton(onClick = { counterOffer = null }) { Text("Cancel") } }
        )
    }
    sel?.let { p -> PlayerDialog(w, p, { sel = null }) { tick++; App.bump() } }
}
