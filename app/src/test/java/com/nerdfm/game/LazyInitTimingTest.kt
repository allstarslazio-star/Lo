package com.nerdfm.game

import org.junit.Test

class LazyInitTimingTest {
    @Test fun printFirstTouchCosts() {
        val w = WorldGen.generate(77L, "T", 0)
        fun t(n: String, f: () -> Unit) { val s = System.nanoTime(); f(); println("first touch $n: ${(System.nanoTime() - s) / 1_000_000L} ms") }
        t("Staffing.state") { Staffing.state(w) }
        t("Medical.state") { MedicalRules.state(w) }
        t("Analytics.state") { Analytics.state(w) }
        t("People.state") { People.state(w) }
        t("ClubEnv.state") { ClubEnvRules.state(w) }
        t("Calendar.state") { FixtureCalendar.state(w) }
        t("Board.state") { BoardRules.state(w) }
        t("Identity.state") { Identity.state(w) }
        t("Ecosystem.preWeek") { Ecosystem.preWeek(w) }
    }
}
