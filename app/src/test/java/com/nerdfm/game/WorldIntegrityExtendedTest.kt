package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorldIntegrityExtendedTest {
    private fun world(seed: Long = 5L): World = WorldGen.generate(seed, "Audit", 0)

    private fun codes(w: World): Set<String> = WorldIntegrity.audit(w).issues.map { it.code }.toSet()

    private fun errorCodes(w: World): Set<String> = WorldIntegrity.inspect(w).map { it.code }.toSet()

    @Test fun aFreshGeneratedWorldHasNoErrorsAndTheAuditDoesNotMutateIt() {
        val w = world()
        val before = StateFingerprint.of(w)
        val report = WorldIntegrity.audit(w)
        assertTrue(report.summary(), report.ok)
        assertEquals(before, StateFingerprint.of(w))
    }

    @Test fun inspectKeepsReturningErrorsOnlyAndInspectAllAddsWarnings() {
        val w = world()
        val p = w.squad(w.human()).first()
        p.contractEnd = w.year - 2 // registered with an expired contract: suspicious, not impossible
        assertTrue(WorldIntegrity.inspect(w).none { it.code == "contract.expired" })
        assertTrue(WorldIntegrity.inspectAll(w).any { it.code == "contract.expired" && it.severity == WorldIntegrity.Severity.WARNING })
        WorldIntegrity.requireValid(w) // warnings never fail the fail-fast assertion
    }

    @Test fun requireValidThrowsOnAnErrorAndNamesTheCode() {
        val w = world()
        w.clubs[3].balance = WorldIntegrity.MAX_MONEY + 1
        try {
            WorldIntegrity.requireValid(w)
            fail("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("club.balance"))
        }
    }

    @Test fun duplicateAndMisplacedIdentifiersAreReported() {
        val w = world()
        w.clubs.add(Club(w.clubs[0].id, "Clone FC", 0, 50, 9000, 0L, 0L))
        val c = errorCodes(w)
        assertTrue(c.toString(), "club.duplicate-id" in c && "club.index" in c)
        val w2 = world()
        w2.leagues.add(League(w2.leagues[0].id, "Clone League", 0))
        assertTrue("league.duplicate-id" in errorCodes(w2))
    }

    @Test fun nextPlayerIdBehindTheHighestIdIsAWarning() {
        val w = world()
        w.nextPid = 3
        assertTrue("player.next-id" in codes(w))
        assertFalse("player.next-id" in errorCodes(w))
    }

    @Test fun contractDatesAndWagesAreValidated() {
        val w = world()
        val players = w.squad(w.human())
        players[0].contractEnd = w.year + 50
        players[1].wage = -5
        players[2].wage = WorldIntegrity.MAX_WAGE + 1
        val c = errorCodes(w)
        assertTrue(c.toString(), "contract.date" in c && "contract.wage" in c)
    }

    @Test fun freeAgentsKeepingAContractAreFlaggedAsWarnings() {
        val w = world()
        val free = w.players.values.first { it.clubId < 0 }
        free.wage = 900
        assertTrue("contract.free-agent" in codes(w))
        assertFalse("contract.free-agent" in errorCodes(w))
    }

    @Test fun impossibleFinancesAreReported() {
        val w = world()
        w.clubs[1].revenue = -1L
        w.clubs[2].balance = -WorldIntegrity.MAX_MONEY - 5
        w.clubs[3].facilities = 25
        val c = errorCodes(w)
        assertTrue(c.toString(), "club.finance" in c && "club.balance" in c && "club.rating" in c)
    }

    @Test fun playerStatisticsAreValidated() {
        val w = world()
        val list = w.squad(w.human())
        list[0].apps = 0; list[0].goals = 3
        list[1].apps = 2; list[1].assists = -1
        list[2].apps = 1; list[2].ratingSum = Float.NaN
        list[3].apps = 2; list[3].ratingSum = 12f // a legal total: each appearance is rated 3..10
        list[4].apps = 1; list[4].ratingSum = 40f
        val c = codes(w)
        assertTrue(c.toString(), "stats.without-apps" in c && "stats.negative" in c && "stats.rating" in c)
        assertTrue(WorldIntegrity.inspect(w).any { it.code == "stats.rating" && it.detail.contains("non-finite") })
    }

    @Test fun duplicateAndConflictingFixturesAreReported() {
        val w = world()
        val league = w.leagues[0]
        val f = league.fixtures[0]
        league.fixtures.add(Fixture(f.round, f.home, f.away))
        val c = errorCodes(w)
        assertTrue(c.toString(), "fixture.duplicate" in c && "fixture.pair-duplicate" in c && "fixture.conflict" in c)
    }

    @Test fun fixturesWithClubsFromAnotherLeagueOrAnImpossibleRoundAreReported() {
        val w = world()
        w.leagues[0].fixtures.add(Fixture(1, w.leagues[0].clubIds[0], w.leagues[1].clubIds[0]))
        w.leagues[2].fixtures.add(Fixture(99, w.leagues[2].clubIds[0], w.leagues[2].clubIds[1]))
        val c = errorCodes(w)
        assertTrue(c.toString(), "fixture.foreign-club" in c && "fixture.round" in c)
    }

    @Test fun aPlayedFixtureInTheFutureIsAnErrorButAnUnplayedPastOneIsAWarning() {
        val w = world()
        w.leagues[0].fixtures.first { it.round == 10 }.apply { played = true; hg = 1; ag = 0 }
        assertTrue("fixture.future-played" in errorCodes(w))
        val v = world()
        v.week = 6
        assertTrue("fixture.unplayed-past" in codes(v))
        assertFalse("fixture.unplayed-past" in errorCodes(v))
    }

    @Test fun standingsThatDisagreeWithResultsAreReported() {
        val w = world()
        w.week = 2
        val league = w.leagues[0]
        val f = league.fixtures.first { it.round == 1 }
        f.played = true; f.hg = 3; f.ag = 0 // recorded in the fixture list only; the table was never updated
        val c = errorCodes(w)
        assertTrue(c.toString(), "table.mismatch" in c)
    }

    @Test fun standingsBuiltFromTheRealRecordPathAreConsistent() {
        val w = world()
        w.week = 1
        val league = w.leagues[0]
        for (f in league.fixtures.filter { it.round == 1 }) Game.record(w, league, f, 2, 1)
        assertTrue(WorldIntegrity.audit(w).summary(), WorldIntegrity.audit(w).ok)
        league.table.getValue(league.clubIds[0]).pts += 1
        val c = errorCodes(w)
        assertTrue(c.toString(), "table.arithmetic" in c || "table.mismatch" in c)
    }

    @Test fun tableMembershipAndFormAreChecked() {
        val w = world()
        w.leagues[1].table.remove(w.leagues[1].clubIds[0])
        assertTrue("table.membership" in errorCodes(w))
        val v = world()
        v.leagues[1].table.getValue(v.leagues[1].clubIds[0]).form.append("XYZ")
        assertTrue("table.form" in errorCodes(v))
    }

    @Test fun offersReferencingMissingRecordsAreReported() {
        val w = world()
        val owned = w.squad(w.human()).first()
        w.offers.add(Offer(9_999_999, 1, 1000L, 5))
        w.offers.add(Offer(owned.id, 9_999, 1000L, 5))
        w.offers.add(Offer(owned.id, 1, -10L, 5))
        val c = errorCodes(w)
        assertTrue(c.toString(), "offer.player" in c && "offer.club" in c && "offer.fee" in c)
    }

    @Test fun staleShortlistEntriesAreOnlyWarnings() {
        val w = world()
        w.shortlist.add(9_999_999)
        w.scouted.add(9_999_998)
        assertTrue("shortlist.player" in codes(w) && "scouted.player" in codes(w))
        assertTrue(WorldIntegrity.audit(w).ok)
    }

    @Test fun loanRecordsAreValidatedWithoutFalseAlarmsForRetiredPlayers() {
        val w = world()
        w.biz().loans.add(LoanDeal(1, 9_999_999, 0, 1, 100, 50, w.year + 1, 10, 0L)) // retired player
        assertTrue("loan.stale" in codes(w))
        assertTrue("loan.stale" !in errorCodes(w))
        w.biz().loans.add(LoanDeal(2, w.squad(w.human()).first().id, 0, 999, 100, 50, w.year + 1, 10, 0L))
        assertTrue("loan.club" in errorCodes(w))
    }

    @Test fun clauseAndSponsorRecordsAreValidated() {
        val w = world()
        val pid = w.squad(w.human()).first().id
        w.biz().clauses[pid] = ContractClauses().apply { releaseClause = -1L; sellOnPct = 140; sellOnClubId = 4_000 }
        w.biz().sponsors.add(SponsorDeal(1, "BadCo", 0, -5L, 0L, 0L, w.year + 1))
        val c = errorCodes(w)
        assertTrue(c.toString(), "clause.value" in c && "clause.club" in c && "sponsor.value" in c)
    }

    @Test fun clubStructureProblemsAreReported() {
        val w = world()
        w.clubs[4].tier = 3
        w.clubs[5].players.clear()
        w.clubs[6].tactics.formation = 99
        w.clubs[7].boardConfidence = Float.NaN
        val c = errorCodes(w)
        assertTrue(c.toString(), "club.tier" in c && "club.squad-empty" in c && "club.tactics" in c && "club.confidence" in c)
    }

    @Test fun issuesArePerCodeCappedButEveryOccurrenceIsCounted() {
        val w = world()
        var broken = 0
        for (p in w.players.values) { if (broken >= 200) break; p.age = 5; broken++ }
        val report = WorldIntegrity.audit(w)
        assertEquals(WorldIntegrity.DEFAULT_PER_CODE_LIMIT, report.issues.count { it.code == "player.age" })
        assertEquals(200, report.totals["player.age"])
        assertTrue(report.errorCount >= 200)
        assertTrue(report.summary().contains("player.age x200"))
    }

    @Test fun anUnrelatedValidWorldReportsNothingBeforeAndAfterAWeek() {
        val w = world(9L)
        Game.startWeek(w)
        Game.completeWeek(w)
        assertTrue(WorldIntegrity.audit(w).summary(), WorldIntegrity.audit(w).ok)
    }

    @Test fun summaryOfACleanWorldSaysSo() {
        val w = world(3L)
        val r = WorldIntegrity.audit(w)
        if (r.issues.isEmpty()) assertEquals("World integrity: no findings.", r.summary())
        else assertTrue(r.summary().startsWith("World integrity:"))
    }
}
