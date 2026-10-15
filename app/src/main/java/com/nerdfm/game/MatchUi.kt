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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MatchScreen(w: World) {
    val eng = App.engine
    val fx = App.pendingFixture
    if (eng == null || fx == null) { App.mode = Mode.GAME; return }
    val ctx = LocalContext.current
    val scope = App.scope
    val me = w.human()
    val mine = if (fx.home == me.id) eng.home else eng.away
    var ver by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(0) }
    var showSub by remember { mutableStateOf(false) }
    ver.let { }

    LaunchedEffect(playing, speed) {
        if (playing) {
            while (playing && !eng.finished) {
                if (eng.atHalfTime) { playing = false; break }
                eng.step()
                ver++
                val d = when (speed) { 0 -> 650L; 1 -> 300L; 2 -> 110L; else -> 25L }
                delay(d)
            }
            if (eng.finished) playing = false
            ver++
        }
    }

    Column(Modifier.fillMaxSize()) {
        // scoreboard
        Row(
            Modifier.fillMaxWidth().background(C.surface).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            T(eng.home.club.name, 16, if (eng.home === mine) C.accent else C.text, true, Modifier.weight(1f))
            T("${eng.home.goals}  -  ${eng.away.goals}", 26, C.text, true)
            T(eng.away.club.name, 16, if (eng.away === mine) C.accent else C.text, true, Modifier.weight(1f).padding(start = 12.dp))
            T(if (eng.finished) "FT" else if (eng.atHalfTime) "HT" else eng.label(), 16, C.amber, true)
        }
        Row(Modifier.weight(1f).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // controls
            Panel(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                if (!eng.finished) {
                    if (eng.atHalfTime) {
                        Btn("Start 2nd half", modifier = Modifier.fillMaxWidth()) { eng.resumeSecondHalf(); ver++; playing = true }
                    } else {
                        Btn(if (playing) "Pause" else if (eng.minutesPlayed == 0) "Kick off" else "Resume", modifier = Modifier.fillMaxWidth()) {
                            playing = !playing
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    T("Speed", 11, C.muted)
                    Seg(listOf("1x", "2x", "5x", "Max"), speed, { speed = it })
                    Spacer(Modifier.height(6.dp))
                    Btn("Sim to full-time", modifier = Modifier.fillMaxWidth()) {
                        playing = false
                        eng.simToEnd(); ver++
                    }
                    Spacer(Modifier.height(6.dp))
                    Btn("Substitution (${5 - mine.subs} left)", enabled = mine.subs < 5 && mine.bench.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        playing = false; showSub = true
                    }
                } else {
                    T("Full time", 16, C.accent, true)
                    val motm = eng.manOfTheMatch()
                    if (motm != null) T("Man of the match: ${motm.p.name} (${"%.1f".format(motm.rating.coerceIn(3f, 10f))})", 12, lines = 3)
                    Spacer(Modifier.height(8.dp))
                    Btn("Continue", modifier = Modifier.fillMaxWidth()) {
                        scope.launch {
                            heavy("Processing results...") {
                                Game.finishHuman(w, fx, eng)
                                Game.completeWeek(w)
                            }
                            withContext(Dispatchers.IO) { Persistence.save(ctx.filesDir, w) }
                            App.engine = null
                            App.pendingFixture = null
                            App.mode = Mode.GAME
                            App.bump()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                T("Live instructions", 12, C.accent, true)
                val tc = mine.tactics
                T("Mentality", 10, C.muted)
                Seg(listOf("UD", "Def", "Bal", "Att", "All"), tc.mentality + 2, { tc.mentality = it - 2; ver++ })
                T("Tempo", 10, C.muted)
                Seg(listOf("Slow", "Norm", "Fast"), tc.tempo, { tc.tempo = it; ver++ })
                T("Pressing", 10, C.muted)
                Seg(listOf("Low", "Norm", "High"), tc.pressing, { tc.pressing = it; ver++ })
                T("Line", 10, C.muted)
                Seg(listOf("Deep", "Norm", "High"), tc.line, { tc.line = it; ver++ })
                T("Width", 10, C.muted)
                Seg(listOf("Narr", "Norm", "Wide"), tc.width, { tc.width = it; ver++ })
            }
            // commentary
            Panel(Modifier.weight(1.4f).fillMaxHeight()) {
                T("Commentary", 13, C.accent, true)
                val evs = eng.events.reversed()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(evs.size) { i ->
                        val e = evs[i]
                        val col = when (e.kind) {
                            1 -> C.green; 2 -> C.amber; 3 -> C.red; 4 -> C.accent; 5 -> C.red; 7 -> C.text; 8 -> C.amber; else -> C.muted
                        }
                        Row {
                            T(e.label, 11, C.muted, modifier = Modifier.width(44.dp))
                            T(e.text, 12, col, e.kind == 1 || e.kind == 3, Modifier.weight(1f), 3)
                        }
                    }
                }
            }
            // stats + lineup
            Panel(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                T("Stats", 13, C.accent, true)
                StatLine("Possession", "${eng.possessionHome()}%", "${100 - eng.possessionHome()}%")
                StatLine("Shots", "${eng.home.shots}", "${eng.away.shots}")
                StatLine("On target", "${eng.home.sot}", "${eng.away.sot}")
                StatLine("xG", "%.2f".format(eng.home.xg), "%.2f".format(eng.away.xg))
                StatLine("Corners", "${eng.home.corners}", "${eng.away.corners}")
                StatLine("Fouls", "${eng.home.fouls}", "${eng.away.fouls}")
                StatLine("Yellows", "${eng.home.yellows}", "${eng.away.yellows}")
                StatLine("Reds", "${eng.home.reds}", "${eng.away.reds}")
                Spacer(Modifier.height(8.dp))
                T("Your XI", 13, C.accent, true)
                for (ps in mine.onPitch) {
                    Row {
                        Cell(ps.slot.label, 30, C.muted)
                        PlayerCell(ps.p, 112, if (ps.injured) C.red else C.text)
                        Cell("${ps.cond.roundToInt()}%", 38, if (ps.cond < 60f) C.amber else C.muted)
                        Cell("%.1f".format(ps.rating.coerceIn(3f, 10f)), 30, C.text, true)
                    }
                }
            }
        }
    }

    if (showSub) {
        var outSel by remember { mutableStateOf<PS?>(null) }
        var inSel by remember { mutableStateOf<PS?>(null) }
        AlertDialog(
            onDismissRequest = { showSub = false },
            title = { Text("Substitution") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        T("Off", 12, C.muted, true)
                        for (ps in mine.onPitch) {
                            Box(Modifier.fillMaxWidth().background(if (outSel === ps) C.accent.copy(alpha = 0.3f) else Color.Transparent)
                                .tapOrProfile(ps.p) { outSel = ps }.padding(4.dp)) {
                                T("${ps.slot.label} ${ps.p.name} ${ps.cond.roundToInt()}%", 12)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        T("On", 12, C.muted, true)
                        for (ps in mine.bench) {
                            Box(Modifier.fillMaxWidth().background(if (inSel === ps) C.accent.copy(alpha = 0.3f) else Color.Transparent)
                                .tapOrProfile(ps.p) { inSel = ps }.padding(4.dp)) {
                                T("${ps.p.pos.label} ${ps.p.name} (${ps.p.ovr}) · ${"%.1f".format(ps.rating.coerceIn(3f, 10f))}", 12)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Btn("Make change", enabled = outSel != null && inSel != null) {
                    val o = outSel
                    val i = inSel
                    if (o != null && i != null) eng.substitute(mine, o, i)
                    showSub = false
                    ver++
                }
            },
            dismissButton = { TextButton(onClick = { showSub = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StatLine(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        T(a, 12, C.text, true, Modifier.width(54.dp))
        T(label, 11, C.muted, modifier = Modifier.weight(1f))
        T(b, 12, C.text, true)
    }
}
