package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PHASES = listOf("", "Treatment", "Rehab", "Return to play")

private fun riskColor(r: Int) = when (r) { 2 -> C.red; 1 -> C.amber; 0 -> C.green; else -> C.muted }
private fun riskWord(r: Int) = when (r) { 2 -> "High"; 1 -> "Moderate"; 0 -> "Low"; else -> "?" }

@Composable
fun MedicalTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val s = MedicalRules.state(w)
    var msg by remember { mutableStateOf("") }
    val diag = MedicalRules.diagnosisQuality(w, me.id)
    val monitor = MedicalRules.monitoringQuality(w, me.id)
    val monitored = MedicalRules.isMonitored(w, me.id)
    val sum = MedicalRules.summary(w, me.id)
    val rows = MedicalRules.riskRows(w)
    val flagged = rows.count { it.risk >= 1 && it.player.injWeeks == 0 }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Medical department", 15, C.accent, true)
            T("Diagnosis and rehab: " + MedicalRules.label(diag), 12, lines = 2)
            T("Workload monitoring: " + (if (monitored) MedicalRules.label(monitor) else "none (no sports scientist or fitness coach)"), 12, if (monitored) C.text else C.amber, lines = 2)
            T("Better doctors and physios shorten injuries; sports scientists and fitness coaches make overuse injuries rarer. Hire them in the Organisation tab.", 10, C.muted, lines = 4)
            Spacer(Modifier.height(6.dp))
            T("This season", 13, C.accent, true)
            T("Injured now " + sum.injuredNow + ", injuries " + sum.seasonInjuries + ", weeks lost " + sum.seasonWeeksLost, 12, lines = 2)
            T("Relapses on the current squad (career): " + sum.relapses, 11, C.muted, lines = 2)
            Spacer(Modifier.height(6.dp))
            T("Rehab policy", 13, C.accent, true)
            Seg(RehabPolicy.values().map { it.label }, s.policy.ordinal, { msg = MedicalRules.setPolicy(w, RehabPolicy.values()[it]).second; refresh() })
            T(s.policy.blurb, 11, C.muted, lines = 3)
            Spacer(Modifier.height(6.dp))
            T("Load management", 13, C.accent, true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("Rest flagged players (" + flagged + ")", monitored && flagged > 0) { msg = MedicalRules.applyLoadAdvice(w).second; refresh() }
                Btn("Back to normal", s.advised.isNotEmpty()) { msg = MedicalRules.releaseLoadAdvice(w).second; refresh() }
            }
            if (!monitored) T("Disabled: hire a sports scientist or fitness coach first.", 10, C.amber, lines = 2)
            else if (flagged == 0) T("Disabled: nobody is flagged at the moment.", 10, C.muted, lines = 2)
            Spacer(Modifier.height(4.dp))
            T(msg, 12, C.accent, lines = 4)
            Spacer(Modifier.height(8.dp))
            T("Recent injuries", 13, C.accent, true)
            if (s.log.isEmpty()) T("No injuries recorded yet.", 11, C.muted)
            for (r in s.log.take(10)) {
                val name = w.players[r.playerId]?.name ?: "Former player"
                T(r.year.toString() + " wk " + r.week + ": " + name + ", " + r.type + ", " + r.weeks + " wk" + (if (r.relapse) " (relapse)" else ""), 11, if (r.relapse) C.red else C.muted, lines = 2)
            }
        }
        Panel(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Squad workload", 15, C.accent, true)
            T("Ratio compares this week's load with the player's normal. Above about 1.3 the risk of overuse injuries and a small match-day condition penalty apply.", 10, C.muted, lines = 3)
            Spacer(Modifier.height(4.dp))
            if (rows.isEmpty()) T("You have no players.", 12, C.muted)
            for (r in rows) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Cell(r.player.name, 110)
                    Cell(r.player.pos.name, 34, C.muted)
                    Cell(if (r.risk < 0) "-" else "%.2f".format(r.acwr), 34, riskColor(r.risk), true)
                    Cell(riskWord(r.risk), 56, riskColor(r.risk))
                    Cell(if (r.player.injWeeks > 0) r.player.injWeeks.toString() + " wk, " + PHASES[r.phase] else r.note, 190, if (r.player.injWeeks > 0) C.red else C.muted)
                    Cell(r.injuries.toString(), 20, C.muted)
                }
            }
            Spacer(Modifier.height(4.dp))
            T("Last column: injuries so far in this career.", 10, C.muted)
        }
    }
}
