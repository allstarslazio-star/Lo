package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Club Ecosystem hub. Each milestone adds tabs here; every tab is a separate Composable in its own file. */
private val ECO_TABS = listOf("Organisation", "People", "Medical", "Analytics", "Board", "Identity", "Fans", "Facilities", "Media", "Calendar", "Preparation")

@Composable
fun EcosystemScreen(w: World) {
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    val refresh: () -> Unit = { tick++; App.bump() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { Seg(ECO_TABS, tab, { tab = it }) }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> OrganisationTab(w, refresh)
                1 -> PeopleTab(w, refresh)
                2 -> MedicalTab(w, refresh)
                3 -> AnalyticsTab(w, refresh)
                4 -> BoardTab(w, refresh)
                5 -> IdentityTab(w)
                6 -> FansTab(w, refresh)
                7 -> FacilitiesTab(w, refresh)
                8 -> MediaTab(w, refresh)
                9 -> CalendarTab(w)
                else -> PreparationTab(w, refresh)
            }
        }
    }
}

private fun abilityColor(v: Int) = attrColor(v)

@Composable
private fun OrganisationTab(w: World, refresh: () -> Unit) {
    val me = w.human()
    val s = Staffing.state(w)
    var dept by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val depts = StaffDept.values()
    val bidsNow = s.bids.toList()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Your staff", 15, C.accent, true)
            val net = Staffing.netWeeklyCost(w, me)
            T("Payroll " + money(Staffing.weeklyWages(w, me.id)) + " / week, net effect on your budget " + (if (net >= 0) "+" else "") + money(net) + " / week", 11, C.muted, lines = 2)
            if (bidsNow.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                T("Poaching offers", 13, C.amber, true)
                for (b in bidsNow) {
                    val m = s.members[b.staffId] ?: continue
                    T(w.clubs[b.bidderClubId].name + " offer " + money(b.newWage.toLong()) + " / week for " + m.name + " (" + m.role.label + ")", 11, lines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("Match offer") { msg = Staffing.matchBid(w, m.id).second; refresh() }
                        Btn("Let him go") { msg = Staffing.acceptBid(w, m.id).second; refresh() }
                    }
                }
            }
            val staff = Staffing.members(w, me.id)
            val vacant = Staffing.vacancies(w, me.id)
            if (staff.isEmpty()) T("You employ nobody yet. Pick a department on the right and hire someone.", 12, C.muted, lines = 3)
            for (d in depts) {
                val here = staff.filter { it.role.dept == d }
                val empty = vacant.filter { it.dept == d }
                if (here.isEmpty() && empty.isEmpty()) continue
                Spacer(Modifier.height(6.dp))
                T(d.label, 13, C.accent, true)
                for (m in here.sortedBy { it.role.ordinal }) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Cell(m.role.label, 120)
                        StaffTag(m, 150.dp)
                        Cell(m.ability.toString(), 24, abilityColor(m.ability), true)
                        Cell(money(m.wage.toLong()), 60, C.muted)
                        Cell("to " + m.contractEnd, 48, if (m.contractEnd <= w.year) C.amber else C.muted)
                        Cell(m.morale.toInt().toString() + "%", 36, if (m.morale < 40f) C.red else C.muted)
                        Btn("Renew 2y", m.morale >= 20f) { msg = Staffing.renew(w, m.id, 2).second; refresh() }
                        Btn("Release") { msg = Staffing.fire(w, m.id).second; refresh() }
                    }
                }
                for (r in empty) T("Vacant: " + r.label, 12, C.amber)
            }
            Spacer(Modifier.height(6.dp))
            T(msg, 12, C.accent, lines = 3)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Candidates", 15, C.accent, true)
            Seg(depts.map { it.label }, dept, { dept = it })
            Spacer(Modifier.height(4.dp))
            val pool = Staffing.freeAgents(w).filter { it.role.dept == depts[dept] }.take(14)
            if (pool.isEmpty()) T("Nobody in this department is available right now. New candidates appear each week.", 12, C.muted, lines = 3)
            for (m in pool) {
                val full = Staffing.members(w, me.id).count { it.role == m.role } >= m.role.slots
                val tooBig = m.reputation - me.rep > 30
                val cost = m.wage.toLong() * Staffing.SIGN_ON_WEEKS
                val why = if (full) "Your " + m.role.label.lowercase() + " places are full." else if (tooBig) "Not interested in a club of your standing." else if (me.balance < cost) "Cannot afford the sign-on fee." else ""
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Cell(m.role.label, 110)
                    StaffTag(m, 140.dp)
                    Cell(m.ability.toString(), 24, abilityColor(m.ability), true)
                    Cell(money(m.wage.toLong()), 56, C.muted)
                    Btn("Hire 2y", why.isEmpty()) { msg = Staffing.hire(w, m.id, 2).second; refresh() }
                }
                if (why.isNotEmpty()) T(why, 10, C.muted, lines = 2)
            }
            Spacer(Modifier.height(6.dp))
            T("Hiring costs " + Staffing.SIGN_ON_WEEKS + " weeks of wages up front. Only the difference to your starting payroll affects your weekly budget.", 10, C.muted, lines = 3)
        }
    }
}
