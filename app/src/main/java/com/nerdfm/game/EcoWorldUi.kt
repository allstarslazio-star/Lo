package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun kindColor(k: Int) = when (k) { 1 -> C.amber; 2 -> C.accent; 3 -> C.muted; else -> C.text }

/** M4: languages, culture, dressing room, personal events. */
@Composable
fun PeopleTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    var msg by remember { mutableStateOf("") }
    var sel by remember { mutableIntStateOf(-1) }
    val squad = w.squad(me).sortedWith(compareByDescending<Player> { it.ovr }.thenBy { it.name })
    val open = People.openEvents(w)
    val cliques = People.cliques(w)
    val coach = People.coachQuality(w, me.id)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Dressing room", 15, C.accent, true)
            T("Working language: " + People.languageName(People.clubLanguage(w, me)) + ". Language coach: " + MedicalRules.label(coach), 11, C.muted, lines = 3)
            T("Cohesion " + me.teamCohesion.toInt() + "%", 12)
            Spacer(Modifier.height(4.dp))
            if (cliques.isEmpty()) T("No friendship groups yet.", 11, C.muted)
            for (c in cliques) T("Clique of " + c.size + ": " + c.take(4).joinToString { it.name }, 11, lines = 3)
            Spacer(Modifier.height(8.dp))
            T("Needs your attention", 13, C.accent, true)
            if (open.isEmpty()) T("Nothing at the moment.", 11, C.muted)
            for (e in open) {
                val p = w.players[e.playerId] ?: continue
                T(e.kind.label + ": " + p.name, 12, if (e.kind.positive) C.green else C.amber, lines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in EventResponse.values()) Btn(r.label) { msg = People.respond(w, e.id, r).second; refresh() }
                }
            }
            T(msg, 12, C.accent, lines = 3)
        }
        Panel(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Players", 15, C.accent, true)
            for (p in squad) {
                val prof = People.profile(w, p)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlayerTag(w, p, 170.dp, if (sel == p.id) C.accent else C.text, false, 22.dp)
                    Cell(prof?.mother?.removeSuffix(" tongue") ?: "-", 70, C.muted)
                    Cell((prof?.communication ?: 0).toString() + "%", 40, if ((prof?.communication ?: 100) < 60) C.red else C.green)
                    Btn(if (sel == p.id) "Hide" else "Profile") { sel = if (sel == p.id) -1 else p.id }
                }
                if (sel == p.id && prof != null) {
                    T("Mother tongue " + prof.mother + (if (prof.learned.isEmpty()) "" else "; learning " + prof.learned.joinToString()), 11, C.muted, lines = 3)
                    T("Culture " + prof.culture + ", settled " + prof.adaptation + "%, " + prof.tenure + " weeks at the club", 11, C.muted, lines = 2)
                    T("Agent: " + prof.agent, 11, C.muted)
                    val ties = People.tiesOf(w, p)
                    if (ties.isEmpty()) T("No close relationships.", 11, C.muted)
                    for ((o, t) in ties) T((when (t) { People.TIE_FRIEND -> "Friend: "; People.TIE_MENTOR -> "Mentor link: "; else -> "Rival: " }) + o.name, 11, if (t == People.TIE_RIVAL) C.red else C.text)
                }
            }
        }
    }
}

/** M5: fans, stadium and pitch. */
@Composable
fun FansTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val s = ClubEnvRules.state(w)
    val e = s.clubs.getValue(me.id)
    var msg by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Supporters", 15, C.accent, true)
            T("Mood " + e.mood.toInt() + "% (" + ClubEnvRules.moodLabel(e.mood) + ")", 13, if (e.mood < 30f) C.red else C.text)
            T("Expected attendance " + ClubEnvRules.attendance(w, me) + " of " + me.capacity(), 12)
            T("A loud crowd lifts the home side a little; a hostile one weighs on it.", 10, C.muted, lines = 3)
            Spacer(Modifier.height(6.dp))
            T("Decisions", 13, C.accent, true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("Freeze ticket prices", !ClubEnvRules.priceFrozen(w)) { msg = ClubEnvRules.freezePrices(w).second; refresh() }
                Btn("Fan forum", ClubEnvRules.forumBlockedReason(w) == null) { msg = ClubEnvRules.holdForum(w).second; refresh() }
            }
            ClubEnvRules.forumBlockedReason(w)?.let { T(it, 10, C.muted) }
            if (ClubEnvRules.priceFrozen(w)) T("Prices are frozen this season.", 10, C.muted)
            T(msg, 12, C.accent, lines = 3)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Stadium and pitch", 15, C.accent, true)
            T("Stadium condition " + e.stadium.toInt() + "%", 12)
            T("Pitch quality " + e.pitch.toInt() + "%", 12, if (e.pitch < 40f) C.red else C.text)
            T("Bad pitches hurt fast, technical teams most. The groundkeeper and these works keep it playable.", 10, C.muted, lines = 3)
            Spacer(Modifier.height(6.dp))
            for (kind in listOf("PITCH", "STADIUM")) ProjectRow(w, kind, refresh) { msg = it }
            val running = s.projects.filter { it.kind == "PITCH" || it.kind == "STADIUM" }
            for (p in running) T(p.label + ": " + p.weeksLeft + " weeks left", 11, C.amber)
        }
    }
}

@Composable
private fun ProjectRow(w: World, kind: String, refresh: () -> Unit, say: (String) -> Unit) {
    val reason = ClubEnvRules.blockedReason(w, kind)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Btn(ClubEnvRules.kindLabel(kind) + " (" + money(ClubEnvRules.upgradeCost(w, kind)) + ")", reason == null) { say(ClubEnvRules.startProject(w, kind).second); refresh() }
    }
    if (reason != null) T("Disabled: " + reason, 10, C.muted, lines = 2)
}

/** M5: facilities. */
@Composable
fun FacilitiesTab(w: World, refresh: () -> Unit) {
    val s = ClubEnvRules.state(w)
    var msg by remember { mutableStateOf("") }
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Facilities", 15, C.accent, true)
        T("Each level costs money and takes weeks. Spending is booked once, when work starts.", 10, C.muted, lines = 2)
        for (kind in listOf("TRAINING", "MEDICAL", "ACADEMY", "DATA")) {
            Spacer(Modifier.height(6.dp))
            T(ClubEnvRules.kindLabel(kind) + ": level " + ClubEnvRules.level(s, kind) + " of " + ClubEnvRules.FACILITY_MAX, 13)
            T(when (kind) {
                "TRAINING" -> "Faster player progression."
                "MEDICAL" -> "Faster weekly recovery."
                "ACADEMY" -> "A better youth intake."
                else -> "Feeds the data level of the analytics milestone (not built yet)."
            }, 10, C.muted, lines = 2)
            val running = s.projects.firstOrNull { it.kind == kind }
            if (running != null) T("Under way: " + running.weeksLeft + " weeks left", 11, C.amber)
            ProjectRow(w, kind, refresh) { msg = it }
        }
        T(msg, 12, C.accent, lines = 3)
    }
}

/** M5: press conferences. */
@Composable
fun MediaTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    var msg by remember { mutableStateOf("") }
    Panel(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        T("Media", 15, C.accent, true)
        T("Media pressure on a club of your size: " + String.format("%.1f", Media.pressure(me)) + "x", 11, C.muted)
        for (phase in 0..1) {
            val q = Media.question(w, phase)
            Spacer(Modifier.height(6.dp))
            T(if (phase == 0) "Before the match" else "After the match", 13, C.accent, true)
            if (q == null) { T("No press conference to give.", 11, C.muted); continue }
            T("The press ask about " + q.topic + (w.players[q.playerId]?.let { " and about " + it.name } ?: "") + ".", 12, lines = 3)
            for (style in PressStyle.values()) {
                Btn(style.label) { msg = Media.answer(w, phase, style).second; refresh() }
                T(style.blurb, 10, C.muted, lines = 2)
            }
        }
        T(msg, 12, C.accent, lines = 3)
    }
}

/** M6: calendar, brackets and congestion. */
@Composable
fun CalendarTab(w: World) {
    var view by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Seg(listOf("Calendar", "Cup", "Continental"), view, { view = it })
        Spacer(Modifier.height(6.dp))
        Panel(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (view) {
                0 -> {
                    for (warn in FixtureCalendar.congestion(w)) T(warn, 11, C.amber, lines = 2)
                    for (e in FixtureCalendar.humanCalendar(w)) T("Wk " + e.week + "  " + e.label, 11, if (e.week < w.week) C.muted else kindColor(e.kind), lines = 2)
                }
                else -> {
                    val cont = view == 2
                    val s = w.ecosystem?.calendar
                    if (cont && s != null && s.contClubs.size == FixtureCalendar.CONT_SIZE) {
                        for (g in 0..1) {
                            T("Group " + ('A' + g), 13, C.accent, true)
                            for (id in FixtureCalendar.groupTable(w, g)) T(w.clubs[id].name + "  " + (s.contPoints[id] ?: 0) + " pts", 11, if (id == w.humanClubId) C.accent else C.text)
                        }
                    }
                    val lines = FixtureCalendar.bracketLines(w, cont)
                    if (lines.isEmpty()) T("Nothing played yet this season.", 11, C.muted)
                    for (l in lines) T(l, 11, lines = 2)
                }
            }
        }
    }
}
