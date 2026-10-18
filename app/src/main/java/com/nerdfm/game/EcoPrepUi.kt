package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreparationTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val s = Preparation.state(w)
    val fx = w.humanFixture()
    val focus = Preparation.focusOf(w)
    val teach = Preparation.teachingQuality(w, me.id)
    val spq = Preparation.setPieceQuality(w, me.id)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Tactical familiarity", 15, C.accent, true)
            T("${s.familiarity.toInt()} / 100 (${Preparation.label(s.familiarity)})", 14, if (s.familiarity >= 70f) C.green else if (s.familiarity >= 50f) C.amber else C.red, true)
            T("Same tactics for ${s.stableWeeks} weeks, ${s.seasonChanges} change(s) this season.", 11, C.muted, lines = 2)
            if (s.lastChangeNote.isNotEmpty()) T(s.lastChangeNote, 11, C.muted, lines = 2)
            T("Changing formation costs a lot of familiarity; small tweaks cost a little. A good assistant manager and coaches teach faster.", 10, C.muted, lines = 4)
            T("Teaching quality: " + MedicalRules.label(teach), 11, lines = 2)
            Spacer(Modifier.height(8.dp))
            T("Set pieces", 15, C.accent, true)
            T("${s.setPieces.toInt()} / 100", 14, C.text, true)
            T("Set-piece coach: " + MedicalRules.label(spq) + (if (Staffing.inRole(w, me.id, StaffRole.COACH_SET_PIECES).isEmpty()) " (none hired)" else ""), 11, lines = 2)
            T("Set-piece work lifts the ceiling slowly; without a coach it stays near the middle.", 10, C.muted, lines = 3)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("This week's focus", 15, C.accent, true)
            val options = PrepFocus.values().toList()
            Seg(options.map { it.label }, focus.ordinal, { Preparation.setFocus(w, options[it]); refresh() })
            T(focus.blurb, 11, C.muted, lines = 4)
            if (focus == PrepFocus.OPPOSITION && fx == null) T("No match this week, so this focus will do nothing.", 11, C.amber, lines = 2)
            if (focus == PrepFocus.OPPOSITION && fx != null) T("Better data makes it count more (data level ${Analytics.humanLevel(w).toInt()}).", 11, C.muted, lines = 2)
            Spacer(Modifier.height(8.dp))
            T("What it does in a match", 13, C.accent, true)
            T("Familiarity: " + signed(Preparation.familiarityFactor(w)) + " sharpness", 11)
            T("Set pieces: " + signed(Preparation.setPieceFactor(w)) + " sharpness", 11)
            T("The focus resets to a balanced week after each week is played.", 10, C.muted, lines = 3)
        }
    }
}

private fun signed(v: Float): String = (if (v >= 0f) "+" else "") + "%.2f".format(v)
