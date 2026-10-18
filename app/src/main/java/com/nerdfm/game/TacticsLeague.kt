package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlin.math.roundToInt

// ------------------------------------------------------------------ tactics

@Composable
fun TacticsScreen(w: World) {
    val me = w.human()
    val tc = me.tactics
    var tick by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }   // 0..10 xi, 11..17 bench, 100+pid reserve
    var info by remember { mutableStateOf<Player?>(null) }
    tick.let { }
    val f = Formations.list[tc.formation]
    val nextFx = w.humanFixture()
    val opponent = nextFx?.let { w.clubs[if (it.home == me.id) it.away else it.home] }

    fun idAt(code: Int): Int = when {
        code in 0..10 -> me.lineup[code]
        code in 11..17 -> me.bench[code - 11]
        code >= 100 -> code - 100
        else -> -1
    }
    fun put(code: Int, id: Int) {
        if (code in 0..10) me.lineup[code] = id
        else if (code in 11..17) me.bench[code - 11] = id
    }
    fun tap(code: Int) {
        if (selected == -1) { selected = code; return }
        if (selected == code) { selected = -1; return }
        val a = idAt(selected)
        val b = idAt(code)
        put(selected, b)
        put(code, a)
        me.autoPick = false
        selected = -1
        tick++
        App.bump()
    }

    val inSquad = HashSet<Int>()
    for (x in me.lineup) inSquad.add(x)
    for (x in me.bench) inSquad.add(x)
    val reserves = w.squad(me).filter { !inSquad.contains(it.id) }.sortedByDescending { it.ovr }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Instructions", 14, C.accent, true)
            T("Formation", 11, C.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(Formations.list.size) { i ->
                    val sel = i == tc.formation
                    Box(
                        Modifier.clip(RoundedCornerShape(2.dp)).background(if (sel) C.accent else C.card)
                            .clickable {
                                tc.formation = i
                                Lineups.autoPick(w, me); me.autoPick = true; selected = -1; tick++; App.bump()
                            }.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) { T(Formations.list[i].name, 12, if (sel) Color.Black else C.text, sel) }
                }
            }
            Spacer(Modifier.height(6.dp))
            T("Mentality", 11, C.muted)
            Seg(listOf("Ultra-D", "Defend", "Balanced", "Attack", "All-out"), tc.mentality + 2, { tc.mentality = it - 2; tick++ })
            T("Tempo", 11, C.muted)
            Seg(listOf("Slow", "Normal", "Fast"), tc.tempo, { tc.tempo = it; tick++ })
            T("Pressing", 11, C.muted)
            Seg(listOf("Low", "Normal", "High"), tc.pressing, { tc.pressing = it; tick++ })
            T("Defensive line", 11, C.muted)
            Seg(listOf("Deep", "Normal", "High"), tc.line, { tc.line = it; tick++ })
            T("Width", 11, C.muted)
            Seg(listOf("Narrow", "Normal", "Wide"), tc.width, { tc.width = it; tick++ })
            T("Passing", 11, C.muted)
            Seg(listOf("Short", "Mixed", "Direct"), tc.passing, { tc.passing = it; tick++ })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = me.autoPick, onCheckedChange = {
                    me.autoPick = it
                    if (it) Lineups.autoPick(w, me)
                    tick++; App.bump()
                })
                Spacer(Modifier.width(6.dp))
                T("Auto-pick XI each week", 12)
            }
            Btn("Pick best XI now", modifier = Modifier.fillMaxWidth()) { Lineups.autoPick(w, me); selected = -1; tick++; App.bump() }
            Spacer(Modifier.height(8.dp))
            T("Next opponent", 13, C.accent, true)
            if (opponent == null) {
                T("No upcoming league fixture.", 11, C.muted, lines = 2)
            } else {
                val of = Formations.list[opponent.tactics.formation.coerceIn(Formations.list.indices)]
                T("${opponent.name} · ${of.name}", 12, C.text, true, lines = 2)
                val ids = opponent.lineup.take(11)
                for (i in ids.indices) {
                    val op = w.players[ids[i]]
                    if (op != null) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Cell(of.slots[i].label, 32, C.muted)
                            Cell(op.name, 104, C.text)
                            Cell("${op.ovr}", 26, ovrColor(op.ovr), true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            T("Out-of-position players play at a heavy penalty.", 11, C.muted, lines = 3)
        }
        Panel(Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Tactical board · ${f.name}", 14, C.accent, true)
            T("Drag a shirt onto another to swap positions, or tap players in the lists.", 10, C.muted, lines = 2)
            Spacer(Modifier.height(4.dp))
            TacticalPitch(
                formation = f,
                playerIds = me.lineup.toList(),
                players = w.players,
                editable = true,
                modifier = Modifier.fillMaxWidth().height(330.dp),
                selectedIndex = selected,
                onSwap = { a, b ->
                    if (a in 0..10 && b in 0..10 && a != b) {
                        val tmp = me.lineup[a]; me.lineup[a] = me.lineup[b]; me.lineup[b] = tmp
                        me.autoPick = false; selected = -1; tick++; App.bump()
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            T("Starting XI", 13, C.accent, true)
            Column {
                for (i in 0 until 11) {
                    val p = w.players[me.lineup[i]]
                    val slot = f.slots[i]
                    Row(
                        Modifier.fillMaxWidth().background(if (selected == i) C.accent.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { tap(i) }.padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cell(slot.label, 36, C.accent, true)
                        if (p == null) Cell("-- empty --", 110, C.red) else {
                            Cell(p.name, 110, if (p.pos != slot && p.pos2 != slot) C.amber else C.text)
                            Cell("${p.ovrAt(slot)}", 30, ovrColor(p.ovrAt(slot)), true)
                            Cell("${p.condition.roundToInt()}%", 38, if (p.condition < 70f) C.amber else C.muted)
                            Cell(statusText(p), 45, C.red)
                        }
                    }
                    HorizontalDivider(color = C.line)
                }
            }
        }
        Panel(Modifier.weight(1.2f).fillMaxHeight()) {
            if (opponent != null) {
                val of = Formations.list[opponent.tactics.formation.coerceIn(Formations.list.indices)]
                T("Opponent · ${of.name}", 13, C.accent, true)
                TacticalPitch(of, opponent.lineup.toList(), w.players, editable = false, modifier = Modifier.fillMaxWidth().height(230.dp))
                Spacer(Modifier.height(6.dp))
            }
            T("Bench & reserves", 14, C.accent, true)
            LazyColumn(Modifier.weight(1f)) {
                items(7) { i ->
                    val p = w.players[me.bench[i]]
                    Row(
                        Modifier.fillMaxWidth().background(if (selected == 11 + i) C.accent.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { tap(11 + i) }.padding(vertical = 5.dp, horizontal = 2.dp)
                    ) {
                        Cell("SUB", 34, C.muted)
                        if (p == null) Cell("-- empty --", 150, C.muted) else {
                            Cell(p.name, 150); Cell(p.pos.label, 30, C.muted); Cell("${p.ovr}", 30, ovrColor(p.ovr), true); Cell(if (p.apps > 0) "%.1f".format(p.avgRating()) else "—", 34, C.accent, true)
                            Cell(statusText(p), 50, C.red)
                        }
                    }
                }
                item { HorizontalDivider(color = C.accent); T("Reserves", 11, C.muted) }
                items(reserves, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().background(if (selected == 100 + p.id) C.accent.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { tap(100 + p.id) }.padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Cell("RES", 34, C.muted); Cell(p.name, 150); Cell(p.pos.label, 30, C.muted)
                        Cell("${p.ovr}", 30, ovrColor(p.ovr), true); Cell(if (p.apps > 0) "%.1f".format(p.avgRating()) else "—", 34, C.accent, true); Cell(statusText(p), 50, C.red)
                    }
                }
            }
        }
    }
}

/** Interactive, pitch-shaped formation board. Dragging a shirt over another swaps the XI slots. */
@Composable
fun TacticalPitch(
    formation: Formation,
    playerIds: List<Int>,
    players: Map<Int, Player>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    onSwap: (Int, Int) -> Unit = { _, _ -> }
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF174D35))) {
        val width = maxWidth
        val height = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.2.dp.toPx()
            drawRect(Color(0xFF1B5B3E))
            drawRect(Color.White.copy(alpha = 0.75f), style = Stroke(stroke))
            drawLine(Color.White.copy(alpha = 0.75f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke)
            drawCircle(Color.White.copy(alpha = 0.75f), radius = size.height * 0.12f, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(stroke))
            drawRect(Color.White.copy(alpha = 0.65f), topLeft = Offset(0f, size.height * 0.30f), size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.40f), style = Stroke(stroke))
            drawRect(Color.White.copy(alpha = 0.65f), topLeft = Offset(size.width * 0.84f, size.height * 0.30f), size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.40f), style = Stroke(stroke))
            drawCircle(Color.White.copy(alpha = 0.7f), radius = size.height * 0.008f, center = Offset(size.width / 2f, size.height / 2f))
        }
        val positions = remember(formation) { formation.slots.indices.map { pitchPoint(formation.slots, it) } }
        val pxWidth = with(androidx.compose.ui.platform.LocalDensity.current) { width.toPx() }
        val pxHeight = with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }
        for (i in formation.slots.indices) {
            val (x, y) = positions[i]
            val p = players[playerIds.getOrElse(i) { -1 }]
            var drag by remember(i, formation.name) { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.offset(x = width * x - 29.dp, y = height * y - 21.dp)
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                    .width(58.dp).height(42.dp)
                    .background(if (selectedIndex == i) C.amber else if (editable) Color(0xFF0D7B50) else Color(0xFF273C8D), RoundedCornerShape(6.dp))
                    .then(if (editable) Modifier.pointerInput(i, pxWidth, pxHeight, positions) {
                        detectDragGestures(
                            onDragEnd = {
                                val nx = x + drag.x / pxWidth
                                val ny = y + drag.y / pxHeight
                                val target = positions.indices.filter { it != i }.minByOrNull { j ->
                                    val dx = positions[j].first - nx; val dy = positions[j].second - ny
                                    dx * dx + dy * dy
                                }
                                if (target != null) {
                                    val dx = positions[target].first - nx; val dy = positions[target].second - ny
                                    if (dx * dx + dy * dy < 0.10f) onSwap(i, target)
                                }
                                drag = Offset.Zero
                            },
                            onDrag = { change, amount -> change.consume(); drag += amount }
                        )
                    } else Modifier)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(formation.slots[i].label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text(p?.name?.split(" ")?.lastOrNull()?.take(8) ?: "Empty", color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    if (p != null) Text("${p.ovr}", color = Color(0xFFFFE082), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun pitchPoint(slots: List<Pos>, index: Int): Pair<Float, Float> {
    val pos = slots[index]
    val same = slots.indices.filter { slots[it] == pos }
    val rank = same.indexOf(index)
    val count = same.size
    val y = if (count == 1) 0.5f else 0.18f + rank * (0.64f / (count - 1))
    val x = when (pos) {
        Pos.GK -> 0.13f
        Pos.LB, Pos.CB, Pos.RB -> 0.25f
        Pos.DM -> 0.42f
        Pos.CM -> 0.50f
        Pos.LW, Pos.RW -> 0.68f
        Pos.AM -> 0.73f
        Pos.ST -> 0.88f
    }
    val sideY = when (pos) {
        Pos.LB, Pos.LW -> 0.12f
        Pos.RB, Pos.RW -> 0.88f
        else -> y
    }
    return x to sideY
}

// ------------------------------------------------------------------ league

@Composable
fun LeagueScreen(w: World) {
    val me = w.human()
    var li by remember { mutableIntStateOf(me.tier) }
    var tab by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(w.week.coerceIn(1, 34)) }
    val lg = w.leagues[li]
    Column(Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(7) { i ->
                val sel = i == li
                Box(
                    Modifier.clip(RoundedCornerShape(2.dp)).background(if (sel) C.accent else C.card)
                        .clickable { li = i }.padding(horizontal = 9.dp, vertical = 5.dp)
                ) { T("${i + 1}. ${w.leagues[i].name}", 11, if (sel) Color.Black else C.text, sel) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Seg(listOf("Table", "Fixtures & results", "Top scorers"), tab, { tab = it })
        Spacer(Modifier.height(6.dp))
        when (tab) {
            0 -> {
                val tbl = lg.sortedTable()
                Panel(Modifier.fillMaxSize()) {
                    Row {
                        Cell("#", 26, C.muted); Cell("Club", 190, C.muted); Cell("P", 28, C.muted); Cell("W", 28, C.muted)
                        Cell("D", 28, C.muted); Cell("L", 28, C.muted); Cell("GF", 32, C.muted); Cell("GA", 32, C.muted)
                        Cell("GD", 34, C.muted); Cell("Pts", 34, C.muted); Cell("Form", 70, C.muted)
                    }
                    LazyColumn {
                        items(tbl.size) { i ->
                            val r = tbl[i]
                            val c = w.clubs[r.clubId]
                            val zone = if (li > 0 && i < 3) C.green else if (li < 6 && i >= tbl.size - 3) C.red else C.muted
                            Row(
                                Modifier.fillMaxWidth().background(if (c.human) C.accent.copy(alpha = 0.18f) else Color.Transparent)
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Cell("${i + 1}", 26, zone, true)
                                Cell(c.name, 190, if (c.human) C.accent else C.text, c.human)
                                Cell("${r.p}", 28); Cell("${r.w}", 28); Cell("${r.d}", 28); Cell("${r.l}", 28)
                                Cell("${r.gf}", 32); Cell("${r.ga}", 32); Cell("${r.gd}", 34)
                                Cell("${r.pts}", 34, C.text, true); Cell(r.form.toString(), 70, C.muted)
                            }
                        }
                    }
                }
            }
            1 -> {
                Panel(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Btn("<", enabled = round > 1) { round-- }
                        Spacer(Modifier.width(8.dp))
                        T("Round $round", 14, bold = true)
                        Spacer(Modifier.width(8.dp))
                        Btn(">", enabled = round < 34) { round++ }
                    }
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(lg.fixtures.filter { it.round == round }) { fx ->
                            val mine = fx.home == me.id || fx.away == me.id
                            Row(
                                Modifier.fillMaxWidth().background(if (mine) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .padding(vertical = 5.dp)
                            ) {
                                T(w.clubs[fx.home].name, 13, modifier = Modifier.weight(1f))
                                T(if (fx.played) "${fx.hg} - ${fx.ag}" else "v", 13, C.accent, true, Modifier.width(56.dp))
                                T(w.clubs[fx.away].name, 13, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            else -> {
                val ids = lg.clubIds.toHashSet()
                val top = w.players.values.filter { it.clubId in ids && it.goals > 0 }
                    .sortedByDescending { it.goals * 100 + it.assists }.take(20)
                Panel(Modifier.fillMaxSize()) {
                    Row { Cell("Player", 190, C.muted); Cell("Club", 170, C.muted); Cell("G", 30, C.muted); Cell("A", 30, C.muted); Cell("Apps", 40, C.muted) }
                    LazyColumn {
                        items(top) { p ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Cell(p.name, 190); Cell(w.clubs[p.clubId].name, 170, C.muted)
                                Cell("${p.goals}", 30, C.text, true); Cell("${p.assists}", 30); Cell("${p.apps}", 40)
                            }
                        }
                    }
                }
            }
        }
    }
}
