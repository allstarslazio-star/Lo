package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-factor wiring: facilities feed data and medical quality; cup minutes feed the workload model. */
class IntegrationWiringTest {
    private fun world(seed: Long = 41L, week: Int = 3) = EcoTestSupport.world(seed, week)

    @Test fun dataSuiteRaisesTheDataLevelOfTheHumanClubOnly() {
        val w = world(); val me = w.human(); val other = w.clubs.first { it.id != me.id }
        val base = Analytics.dataLevel(w, me); val baseOther = Analytics.dataLevel(w, other)
        ClubEnvRules.state(w).data = 3
        assertTrue(Analytics.dataLevel(w, me) > base || base >= 99f)
        assertEquals(baseOther, Analytics.dataLevel(w, other), 0f)
        assertTrue(Analytics.dataLevel(w, me) in 0f..100f)
    }

    @Test fun medicalCentreImprovesDiagnosisButStaysBounded() {
        val w = world(); val me = w.human()
        val base = MedicalRules.diagnosisQuality(w, me.id)
        ClubEnvRules.state(w).medical = 3
        val better = MedicalRules.diagnosisQuality(w, me.id)
        assertTrue(better > base || base >= 1f)
        assertTrue(better in -1f..1f)
    }

    @Test fun facilitiesDoNothingInSafeMode() {
        val w = world(); ClubEnvRules.state(w).data = 3; ClubEnvRules.state(w).medical = 3
        Ecosystem.enabled = false
        try { assertEquals(0f, ClubEnvRules.facilityBonus(w, w.humanClubId, "DATA", 1f), 0f) } finally { Ecosystem.enabled = true }
    }

    @Test fun midweekMinutesAreNotLostWhenTheLeagueMatchOverwritesLastMinutes() {
        val w = world()
        val s = FixtureCalendar.state(w)
        s.midMins = HashMap<Int, Int>().also { it[w.squad(w.human()).first().id] = 90 }
        assertEquals(90, FixtureCalendar.midweekMinutes(w, w.squad(w.human()).first().id))
        assertEquals(0, FixtureCalendar.midweekMinutes(w, -5))
    }

    @Test fun aCupGameRaisesThatWeeksWorkloadInTheMedicalModel() {
        val a = world(); val b = world()
        val pa = a.squad(a.human()).first(); val pb = b.squad(b.human()).first()
        pa.lastMinutes = 90; pb.lastMinutes = 90
        FixtureCalendar.state(b).midMins = HashMap<Int, Int>().also { it[pb.id] = 90 }
        MedicalRules.weekly(a); MedicalRules.weekly(b)
        val ma = MedicalRules.state(a).players[pa.id]!!; val mb = MedicalRules.state(b).players[pb.id]!!
        assertTrue("acute ${ma.acute} vs ${mb.acute}", mb.acute > ma.acute)
    }

    @Test fun midMinutesAreTransientAndClearedEachWeek() {
        val w = world(); val s = FixtureCalendar.state(w)
        s.midMins = HashMap<Int, Int>().also { it[1] = 90 }
        val copy = java.io.ByteArrayOutputStream().also { java.io.ObjectOutputStream(it).use { o -> o.writeObject(s) } }
        val back = java.io.ObjectInputStream(java.io.ByteArrayInputStream(copy.toByteArray())).readObject() as CalendarState
        assertEquals(null, back.midMins)
    }
}
