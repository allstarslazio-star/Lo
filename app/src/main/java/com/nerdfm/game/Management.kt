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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

private val TRAINING_FOCUSES = listOf("Balanced", "Attacking", "Defending", "Passing", "Physical", "Goalkeeping")

@Composable
fun TrainingScreen(w: World) {
    val me = w.human()
    var filter by remember { mutableIntStateOf(0) }
    var changed by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<Player?>(null) }
    changed.let { }
    val squad = w.squad(me).sortedWith(compareBy<Player> { it.pos.ordinal }.thenByDescending { it.ovr })
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Training centre", 17, C.accent, true)
                T("Facilities ${me.facilities}/20  |  Best coach ${me.coaches.maxOfOrNull { it.rating } ?: 45}/100", 12, C.muted)
            }
            Seg(listOf("All", "Starters", "Low sharpness"), filter, { filter = it })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel(Modifier.weight(1.55f).fillMaxHeight()) {
                Row {
                    Cell("Player", 160, C.muted); Cell("Fit", 42, C.muted); Cell("Sharp", 48, C.muted)
                    Cell("Focus", 110, C.muted); Cell("Intensity", 82, C.muted)
                }
                LazyColumn {
                    val filtered = squad.filter { p -> when(filter) {
                        1 -> me.lineup.contains(p.id)
                        2 -> p.sharpness < 60f
                        else -> true
                    } }
                    items(filtered, key = { it.id }) { p ->
                        Column(Modifier.fillMaxWidth().background(if (player?.id == p.id) C.card else Color.Transparent).tapOrProfile(p) { player = p }.padding(vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cell(p.name, 160); Cell("${p.condition.roundToInt()}%", 42, if (p.condition < 65f) C.amber else C.green)
                                Cell("${p.sharpness.roundToInt()}%", 48, if (p.sharpness < 55f) C.amber else C.green)
                                Cell(p.trainingFocus, 110, C.muted)
                                Cell(listOf("Light", "Normal", "Intense")[p.trainingIntensity], 82, C.text)
                            }
                            HorizontalDivider(color = C.line)
                        }
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                val p = player ?: squad.firstOrNull()
                if (p == null) T("No players in squad.", 13, C.muted) else {
                    T(p.name, 17, C.accent, true)
                    T("${p.pos.label}  |  Age ${p.age}  |  Overall ${p.ovr}  |  Potential ${p.pa}", 12, C.muted, lines = 2)
                    Spacer(Modifier.height(8.dp))
                    T("Fitness", 12, C.muted); Bar(p.condition / 100f, if (p.condition < 60) C.red else C.green, Modifier.fillMaxWidth())
                    T("${p.condition.roundToInt()}%", 12)
                    T("Match sharpness", 12, C.muted); Bar(p.sharpness / 100f, if (p.sharpness < 55) C.amber else C.accent, Modifier.fillMaxWidth())
                    T("${p.sharpness.roundToInt()}%", 12)
                    Spacer(Modifier.height(8.dp))
                    T("Individual focus", 12, C.muted)
                    TRAINING_FOCUSES.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { focus ->
                                Btn(focus, enabled = p.trainingFocus != focus, modifier = Modifier.weight(1f)) {
                                    p.trainingFocus = focus; changed++; App.bump()
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    T("Training intensity", 12, C.muted)
                    Seg(listOf("Light", "Normal", "Intense"), p.trainingIntensity, { p.trainingIntensity = it; changed++; App.bump() })
                    Spacer(Modifier.height(6.dp))
                    T(when(p.trainingIntensity) {
                        0 -> "Light sessions aid recovery but develop attributes more slowly."
                        2 -> "Intense sessions improve sharpness and development chances, but recovery is slower. Avoid this for tired or injured players."
                        else -> "Balanced workload with moderate development and recovery."
                    }, 11, C.muted, lines = 5)
                    Spacer(Modifier.height(8.dp))
                    T("Development tracking", 13, C.accent, true)
                    T("Season stats: ${p.apps} appearances, ${p.goals} goals, ${p.assists} assists", 12, C.muted, lines = 2)
                    T("Average rating: ${if (p.apps > 0) "%.2f".format(p.avgRating()) else "—"}/10", 12)
                    T("Training improves attributes gradually and never beyond the player's potential.", 11, C.muted, lines = 3)
                }
            }
        }
    }
}

@Composable
fun StaffScreen(w: World) {
    val me = w.human()
    var note by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    refresh.let { }
    if (w.coachMarket.isEmpty()) {
        val rng = Random(w.seed xor 0x4E455244L)
        val specialities = listOf("Attacking", "Defending", "Fitness", "Goalkeeping", "Youth", "Tactics")
        val first = listOf("Alex", "Marco", "Daniel", "Samuel", "Rui", "Thomas", "Ibrahim", "Victor", "Andre", "Luis", "Peter", "Milan")
        val last = listOf("Costa", "Meyer", "Otieno", "Silva", "Novak", "Bennett", "Kamau", "Rossi", "Mensah", "Kovac", "Diaz", "Nordin")
        repeat(18) {
            val rating = rng.nextInt(38, 91)
            w.coachMarket.add(Coach(w.nextCoachId++, "${first[rng.nextInt(first.size)]} ${last[rng.nextInt(last.size)]}", specialities[rng.nextInt(specialities.size)], rating, (rating * 90 + rng.nextInt(250, 1200)) / 50 * 50, rating))
        }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            T("Coaching staff", 16, C.accent, true)
            T("Coaches influence player development. Wages are included in the club's weekly wage bill.", 11, C.muted, lines = 3)
            Spacer(Modifier.height(6.dp))
            if (me.coaches.isEmpty()) T("No coaches hired yet.", 12, C.amber)
            LazyColumn {
                items(me.coaches, key = { it.id }) { c ->
                    Column(Modifier.fillMaxWidth().background(C.card).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { T(c.name, 13, bold = true); T("${c.specialty} coach · ${c.rating}/100", 11, C.muted) }
                            T("${money(c.wage.toLong())}/wk", 11, C.amber)
                            Spacer(Modifier.width(6.dp))
                            Btn("Release") {
                                me.coaches.removeAll { it.id == c.id }; me.balance -= c.wage * 4L
                                note = "${c.name} released. Four weeks of wages paid as compensation."; refresh++; App.bump()
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(5.dp))
            T("Weekly wage bill ${money(w.wageBill(me))} / ${money(me.wageBudget)}", 12, if (w.wageBill(me) > me.wageBudget) C.red else C.muted)
        }
        Panel(Modifier.weight(1.15f).fillMaxHeight()) {
            T("Coach market", 16, C.accent, true)
            T("Hire staff to improve training. Better coaches cost more, and available candidates remain until hired.", 11, C.muted, lines = 3)
            LazyColumn {
                items(w.coachMarket.toList(), key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().background(C.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(c.name, 13, bold = true)
                            T("${c.specialty} · Rating ${c.rating}/100", 11, C.muted)
                            T("${money(c.wage.toLong())}/week", 11, C.amber)
                        }
                        Btn("Hire", enabled = me.coaches.size < 8 && me.balance > c.wage * 8L && w.wageBill(me) + c.wage <= me.wageBudget * 1.15) {
                            me.coaches.add(c); me.balance -= c.wage * 2L; w.coachMarket.removeAll { it.id == c.id }
                            note = "${c.name} hired as ${c.specialty} coach."; w.addNews("Coaching appointment", "${me.name} hired ${c.name} (${c.specialty}, ${c.rating}/100).")
                            refresh++; App.bump()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (note.isNotEmpty()) { Spacer(Modifier.height(5.dp)); T(note, 12, C.green, lines = 3) }
        }
    }
}

@Composable
fun ClubCrest(c: Club, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(34.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * .12f, size.height * .08f)
            lineTo(size.width * .88f, size.height * .08f)
            lineTo(size.width * .82f, size.height * .62f)
            lineTo(size.width * .50f, size.height * .94f)
            lineTo(size.width * .18f, size.height * .62f)
            close()
        }
        drawPath(path, Color(c.colorA))
        drawLine(Color(c.colorB), androidx.compose.ui.geometry.Offset(size.width * .28f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .72f, size.height * .18f), strokeWidth = size.width * .08f)
        drawLine(Color(c.colorB), androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .24f), androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .72f), strokeWidth = size.width * .07f)
    }
}
