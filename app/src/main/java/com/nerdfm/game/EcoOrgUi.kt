package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun trait(v: Int): String = when {
    v >= 16 -> "very high"
    v >= 12 -> "high"
    v >= 8 -> "average"
    v >= 5 -> "low"
    else -> "very low"
}

@Composable
fun BoardTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val bs = BoardRules.state(w)
    val board = BoardRules.boardOf(w, me.id)
    var msg by remember { mutableStateOf("") }
    val open = bs.suggestions.filter { !it.handled }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Owner", 15, C.accent, true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardFace(board.owner.name)
                T(board.owner.name + ", owner since " + board.owner.sinceYear, 12, lines = 2, modifier = Modifier.weight(1f))
            }
            T("Ambition " + trait(board.owner.ambition) + ", patience " + trait(board.owner.patience) + ", wealth " + trait(board.owner.wealth), 11, C.muted, lines = 2)
            T("Transfer policy: " + board.owner.policy.label, 12)
            T(board.owner.policy.blurb, 11, C.muted, lines = 2)
            if (board.freezeWeeks > 0) T("Spending frozen for " + board.freezeWeeks + " more week(s).", 12, C.amber, lines = 2)
            Spacer(Modifier.height(6.dp))
            T("Chairman", 13, C.accent, true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardFace(board.chairman.name, 36.dp)
                T(board.chairman.name + ", ambition " + trait(board.chairman.ambition) + ", meddling " + trait(board.chairman.meddling), 11, C.muted, lines = 3, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            T("Your contract", 13, C.accent, true)
            val c = bs.contract
            if (c == null) T("No contract on record yet.", 12, C.muted)
            else {
                T("Runs to " + c.endYear + " at " + money(c.weeklyWage) + " / week", 12)
                T("Dismissal compensation: " + c.compensationWeeks + " weeks of wages", 11, C.muted, lines = 2)
            }
            T("Board confidence " + me.boardConfidence.toInt() + " (you are dismissed below " + "%.1f".format(BoardRules.sackThreshold(w)) + " in the weekly check)", 11, C.muted, lines = 3)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Objectives", 15, C.accent, true)
            if (board.objectives.isEmpty()) T("The board has not set objectives yet.", 12, C.muted)
            for (o in board.objectives) {
                val col = when (o.status) { 1 -> C.green; 2 -> C.red; else -> C.text }
                T(o.describe(), 12, col, lines = 2)
                T((when (o.status) { 1 -> "Met"; 2 -> "Failed"; else -> "In progress" }) + ", latest value " + o.latest, 10, C.muted)
            }
            Spacer(Modifier.height(8.dp))
            T("Director of football", 15, C.accent, true)
            val d = board.director
            T(d.name + ", favours " + d.philosophy.label.lowercase(), 12, lines = 2)
            T("Judgement " + trait(d.ability) + ", stubbornness " + trait(d.stubbornness), 11, C.muted, lines = 2)
            Bar(d.alignment / 100f, if (d.alignment < 35f) C.red else C.accent, Modifier.fillMaxWidth())
            T("Alignment with you: " + d.alignment.roundToInt() + " / 100" + (if (d.alignment < 35f) " (he may sign players without asking)" else ""), 11, C.muted, lines = 2)
            Spacer(Modifier.height(4.dp))
            if (open.isEmpty()) T("No recommendations waiting.", 12, C.muted)
            for (sg in open) {
                val p = w.players[sg.playerId]
                if (p == null) { T("A recommended player is no longer on the market.", 11, C.muted); continue }
                T(p.name + " (" + p.pos.name + ", age " + p.age + ")", 12, C.text, true)
                T(sg.reason, 11, C.muted, lines = 3)
                val ap = Analytics.modelApproval(w, p)
                T("Data model: " + (if (ap > 0) "approves (rated about " + Analytics.modelRating(w, p) + ")" else if (ap == 0) "neutral" else "does not rate him at your level"), 11, if (ap > 0) C.green else if (ap < 0) C.amber else C.muted, lines = 2)
                val wage = Transfers.wantedWage(me, p)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("Sign (" + money(wage.toLong()) + " / wk)", p.clubId == -1) { msg = BoardRules.acceptSuggestion(w, sg.id).second; refresh() }
                    Btn("Decline") { msg = BoardRules.declineSuggestion(w, sg.id).second; refresh() }
                }
            }
            Spacer(Modifier.height(4.dp))
            T(msg, 12, C.accent, lines = 3)
        }
    }
}

@Composable
fun IdentityTab(w: World) {
    val me = w.human()
    val s = Identity.state(w)
    val cul = Identity.culture(w, me.id)
    val sus = Identity.sustainability(w, me)
    val home = Identity.homegrownCount(w, me.id)
    val h = s.homegrown
    val squad = w.squad(me).filter { h[it.id] == me.id }.sortedByDescending { it.ovr }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Club culture", 15, C.accent, true)
            for ((label, v) in listOf("Winning" to cul.winning, "Stability" to cul.stability, "Youth" to cul.youth)) {
                T(label + " " + v.roundToInt(), 12)
                Bar(v / 100f, C.accent, Modifier.fillMaxWidth())
            }
            T("A strong culture lifts match-day morale; a weak one costs it. The effect is capped at a few points.", 10, C.muted, lines = 3)
            Spacer(Modifier.height(8.dp))
            T("Sustainability " + sus.total + " / 100", 15, C.accent, true)
            for ((label, v) in listOf("Finances" to sus.finance, "Squad age balance" to sus.squadAge, "Academy pathway" to sus.youth, "Contract security" to sus.contracts)) {
                T(label + " " + v, 12)
                Bar(v / 100f, if (v < 40) C.red else C.accent, Modifier.fillMaxWidth())
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Academy and homegrown players", 15, C.accent, true)
            T("Academy rating " + Identity.academyRating(w, me) + " (includes your youth staff)", 12)
            T("Homegrown in squad " + home + " (club target " + Identity.quota(me) + ")", 12, if (home >= Identity.quota(me)) C.green else C.amber)
            T("League minutes to academy players under 22 this season: " + Identity.academyMinutePercent(w, me.id) + "%", 12, lines = 2)
            Spacer(Modifier.height(6.dp))
            if (squad.isEmpty()) T("You have no homegrown players. Youth intake each summer will start building the pathway.", 12, C.muted, lines = 3)
            for (p in squad) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Cell(p.name, 120)
                    Cell(p.pos.name, 36, C.muted)
                    Cell(p.age.toString(), 28, C.muted)
                    Cell(p.ovr.toString(), 28, ovrColor(p.ovr), true)
                }
            }
        }
    }
}
