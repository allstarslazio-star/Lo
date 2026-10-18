package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val ANALYTICS_SECTIONS = listOf("Data and opposition", "Recruitment targets", "Squad performance")

@Composable
fun AnalyticsTab(w: World, refresh: () -> Unit) {
    var section by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Seg(ANALYTICS_SECTIONS, section, { section = it })
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
                0 -> DataAndOpposition(w, refresh, msg) { msg = it }
                1 -> TargetsSection(w, refresh, msg) { msg = it }
                else -> PerformanceSection(w)
            }
        }
    }
}

@Composable
private fun DataAndOpposition(w: World, refresh: () -> Unit, msg: String, say: (String) -> Unit) {
    val me = w.human()
    val level = Analytics.humanLevel(w)
    val plan = Analytics.planOf(w, me)
    val net = Analytics.netWeeklyCost(w, me)
    val opp = Analytics.nextOpponent(w)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Data level " + level.roundToInt() + " / 100", 15, C.accent, true)
            Bar(level / 100f, if (level < 30f) C.red else C.accent, Modifier.fillMaxWidth())
            T("Rated " + Analytics.levelLabel(level) + ". 50 is normal for a club of your size.", 11, C.muted, lines = 2)
            Spacer(Modifier.height(4.dp))
            T("Analysts: " + MedicalRules.label(Analytics.analystQuality(w, me.id)), 12, lines = 2)
            T("Recruitment staff: " + MedicalRules.label(Analytics.recruitmentQuality(w, me.id)), 12, lines = 2)
            T("Better data narrows scouting error, speeds scouting up and gives a tiny sharpness benefit on match days. It never wins games by itself.", 10, C.muted, lines = 4)
            Spacer(Modifier.height(8.dp))
            T("Data package", 13, C.accent, true)
            Seg(DataPlan.values().map { it.label }, plan.ordinal, { say(Analytics.setPlan(w, DataPlan.values()[it]).second); refresh() })
            T(plan.blurb, 11, C.muted, lines = 3)
            T("Net effect on your weekly budget: " + (if (net >= 0) "+" else "") + money(net), 11, if (net > 0) C.amber else C.muted, lines = 2)
            Spacer(Modifier.height(4.dp))
            T(msg, 12, C.accent, lines = 4)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Next opponent", 15, C.accent, true)
            if (opp == null) T("No league fixture is left to prepare for.", 12, C.muted, lines = 2)
            else {
                val r = Analytics.oppositionReport(w, opp)
                T(opp.name, 13, C.text, true)
                T("Plays " + r.formation + ", " + r.mentality, 12, lines = 2)
                T("Strongest area: " + r.strongestGroup + ". Weakest: " + r.weakestGroup + ".", 12, lines = 2)
                T("Key player: " + r.keyPlayer + " (about " + r.keyPlayerOvr + ")", 12, lines = 2)
                T("Recent form: " + r.form, 12)
                T("Report confidence " + r.confidence + "%", 11, C.muted)
                Spacer(Modifier.height(4.dp))
                T(r.advice, 12, C.accent, lines = 4)
            }
        }
    }
}

@Composable
private fun TargetsSection(w: World, refresh: () -> Unit, msg: String, say: (String) -> Unit) {
    val targets = Analytics.dataTargets(w, 8)
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Players the data model likes", 15, C.accent, true)
        T("The rating carries an error band that shrinks as your data level rises. Add players to your shortlist to follow them in Transfers.", 10, C.muted, lines = 3)
        Spacer(Modifier.height(4.dp))
        if (targets.isEmpty()) T("The model has no recommendation right now: your squad has no thin area and nobody clearly better is available.", 12, C.muted, lines = 3)
        for (t in targets) {
            val p = t.player
            val inList = w.shortlist.contains(p.id)
            val where = if (p.clubId >= 0) w.clubs[p.clubId].name else "Free agent"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cell(p.name, 110)
                Cell(p.pos.label, 34, C.muted)
                Cell(p.age.toString(), 24, C.muted)
                Cell("~" + t.modelOvr, 34, ovrColor(t.modelOvr), true)
                Cell(where, 110, C.muted)
                Cell(t.reason, 190, C.text)
                Btn(if (inList) "Shortlisted" else "Shortlist", !inList) { w.shortlist.add(p.id); say(p.name + " added to your shortlist."); refresh() }
            }
        }
        Spacer(Modifier.height(4.dp))
        T(msg, 12, C.accent, lines = 2)
    }
}

@Composable
private fun PerformanceSection(w: World) {
    val rows = Analytics.performanceRows(w).sortedByDescending { it.rating }
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Squad performance this season (per 90 minutes)", 15, C.accent, true)
        Spacer(Modifier.height(4.dp))
        if (rows.isEmpty()) T("Players need at least 180 minutes before their numbers mean anything.", 12, C.muted, lines = 2)
        else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Cell("Player", 110, C.muted); Cell("Min", 36, C.muted); Cell("xG", 36, C.muted); Cell("Key passes", 64, C.muted); Cell("Tackles+int", 70, C.muted); Cell("Rating", 40, C.muted)
        }
        for (r in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cell(r.player.name, 110)
                Cell(r.minutes.toString(), 36, C.muted)
                Cell("%.2f".format(r.xgPer90), 36)
                Cell("%.2f".format(r.keyPassesPer90), 64)
                Cell("%.2f".format(r.defensivePer90), 70)
                Cell("%.1f".format(r.rating), 40, C.accent, true)
            }
        }
    }
}
