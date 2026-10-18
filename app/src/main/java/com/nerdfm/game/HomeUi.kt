package com.nerdfm.game

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The dashboard: next fixture, recent results and actionable notices, so most tasks start from one screen. */
@Composable
fun HomeScreen(w: World) {
    val me = w.human()
    val tick = App.tick
    tick.let { }
    val notices = remember(w, tick, w.week, w.year) { App.notices.visible(w) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                T("Next fixture", 14, C.accent, true)
                val fx = w.humanFixture()
                if (fx == null) {
                    T(if (w.week == 0) "Pre-season. Prepare the squad, then press Begin season." else "No fixture this week.", 12, C.muted, lines = 3)
                } else {
                    val home = fx.home == me.id
                    val opp = w.clubs[if (home) fx.away else fx.home]
                    T("Round ${fx.round}: ${if (home) "home" else "away"} v ${opp.name}", 13, bold = true, lines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Their rating ", 11, C.muted); Stars(strengthStars(Lineups.strength(w, opp)), 12)
                        T("   Yours ", 11, C.muted); Stars(strengthStars(Lineups.strength(w, me)), 12)
                    }
                    val position = w.leagueOf(opp.id).sortedTable().indexOfFirst { it.clubId == opp.id } + 1
                    T("They are $position${Game.ord(position)} in the table", 11, C.muted)
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                T("Recent results", 14, C.accent, true)
                val results = recentResults(w)
                if (results.isEmpty()) T("No matches played yet this season.", 12, C.muted)
                for (line in results) T(line.text, 12, line.color, lines = 1)
            }
            Panel(Modifier.fillMaxWidth()) {
                T("Club snapshot", 14, C.accent, true)
                T("Board confidence ${me.boardConfidence.roundToInt()}%", 12, if (me.boardConfidence < 35f) C.red else C.text)
                T("Balance ${money(me.balance)}   |   Wage bill ${money(w.wageBill(me))}/wk", 12, if (me.balance < 0) C.red else C.muted, lines = 2)
                T("${w.manager} \u00B7 ${w.seasonLabel()} \u00B7 ${w.dateLabel()}", 11, C.muted, lines = 2)
            }
        }
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Notifications (${notices.size})", 14, C.accent, true, Modifier.weight(1f))
                if (notices.isNotEmpty()) Btn("Dismiss all", modifier = Modifier.minTouch()) { App.notices.dismissAll(w); App.bump() }
            }
            if (notices.isEmpty()) {
                EmptyState("All clear. Nothing needs your attention this week.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(notices, key = { it.id }) { n -> NoticeCard(w, n) }
                }
            }
        }
    }
}

private class ResultLine(val text: String, val color: androidx.compose.ui.graphics.Color)

private fun recentResults(w: World): List<ResultLine> {
    val me = w.humanClubId
    val lg = w.leagueOf(me)
    return lg.fixtures.filter { it.played && (it.home == me || it.away == me) }.sortedByDescending { it.round }.take(5).map { f ->
        val home = f.home == me
        val mine = if (home) f.hg else f.ag
        val theirs = if (home) f.ag else f.hg
        val opp = w.clubs[if (home) f.away else f.home].name
        val tag = if (mine > theirs) "W" else if (mine < theirs) "L" else "D"
        ResultLine("R${f.round} $tag ${if (home) "H" else "A"} $opp $mine-$theirs", if (tag == "W") C.green else if (tag == "L") C.red else C.text)
    }
}

@Composable
fun NoticeCard(w: World, n: Notice) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(C.card).padding(8.dp)
            .describe("${n.level.name.lowercase()} notice. ${n.title}. ${n.body}"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(n.title, 13, noticeColor(n.level), true, Modifier.weight(1f), lines = 2)
            T(n.level.name.lowercase(), 10, C.muted)
        }
        T(n.body, 12, C.text, lines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val target = n.target
            if (target != null && n.actionLabel != null) Btn(n.actionLabel, modifier = Modifier.minTouch()) { App.navigate(target) }
            TextButton(onClick = { App.notices.dismiss(w, n.id); App.bump() }, modifier = Modifier.minTouch()) { Text("Dismiss", fontSize = 12.sp) }
        }
    }
}

/** Dismissible walkthrough strip. It never blocks input: experienced managers can skip it with one tap. */
@Composable
fun TutorialBanner() {
    val tick = App.tutorialTick
    tick.let { }
    val t = App.tutorial
    if (!t.active) return
    val step = t.current
    Row(
        Modifier.fillMaxWidth().background(C.card).padding(horizontal = 10.dp, vertical = 4.dp).describe("Tutorial step ${t.index + 1} of ${t.total}. ${step.title}. ${step.body}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            T("Tutorial ${t.index + 1}/${t.total}: ${step.title}", 12, C.accent, true)
            T(step.body, 11, C.text, lines = 2)
        }
        Btn("Show me", modifier = Modifier.minTouch()) { App.navigate(step.screen) }
        Btn("Back", enabled = t.index > 0, modifier = Modifier.minTouch()) { t.previous(); App.tutorialTick++ }
        Btn(if (t.isLast) "Finish" else "Next", modifier = Modifier.minTouch()) { t.next(); App.tutorialTick++ }
        TextButton(onClick = { t.skip(); App.tutorialTick++ }, modifier = Modifier.minTouch()) { Text("Skip", fontSize = 12.sp) }
    }
}
