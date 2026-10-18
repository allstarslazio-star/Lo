package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQueryTest {
    private val world = World().apply { year = 2026 }

    private fun mk(
        id: Int, name: String, pos: Pos = Pos.CM, age: Int = 25, ovr: Int = 50, pa: Int = 60,
        pos2: Pos? = null, condition: Float = 100f, morale: Float = 70f, contractEnd: Int = 2029, wage: Int = 1000
    ): Player = Player(id, name, 0, age, pos, pos2, IntArray(NATTR) { 10 }, pa, 10, 10, 10, 10, 10).also {
        it.ovr = ovr; it.condition = condition; it.morale = morale; it.contractEnd = contractEnd; it.wage = wage; it.clubId = 0
    }

    private fun ids(r: QueryResult): List<Int> = r.players.map { it.id }

    @Test fun searchIgnoresCaseAndAccents() {
        val p = mk(1, "Mesut \u00D6zil")
        assertTrue(PlayerQuery.matchesName(p, "ozil"))
        assertTrue(PlayerQuery.matchesName(p, "OZIL"))
        assertTrue(PlayerQuery.matchesName(p, "  mesut   \u00F6z "))
        assertFalse(PlayerQuery.matchesName(p, "xavi"))
    }

    @Test fun everyTokenMustMatchAndBlankMatchesAll() {
        val p = mk(1, "Alex Morgan")
        assertTrue(PlayerQuery.matchesName(p, "morgan alex"))
        assertFalse(PlayerQuery.matchesName(p, "alex smith"))
        assertTrue(PlayerQuery.matchesName(p, ""))
        assertTrue(PlayerQuery.matchesName(p, "   "))
    }

    @Test fun positionFilterAlsoMatchesTheSecondaryPosition() {
        val list = listOf(mk(1, "A", Pos.CM), mk(2, "B", Pos.ST, pos2 = Pos.CM), mk(3, "C", Pos.GK))
        val r = PlayerQuery.run(world, list, PlayerFilter(positions = setOf(Pos.CM)))
        assertEquals(listOf(1, 2), ids(r).sorted())
    }

    @Test fun filtersCombineWithAnd() {
        val list = listOf(
            mk(1, "A", Pos.CM, condition = 95f, contractEnd = 2027),
            mk(2, "B", Pos.CM, condition = 60f, contractEnd = 2027),
            mk(3, "C", Pos.CM, condition = 95f, contractEnd = 2030),
            mk(4, "D", Pos.ST, condition = 95f, contractEnd = 2027)
        )
        val f = PlayerFilter(positions = setOf(Pos.CM), minFitness = 80, contract = ContractFilter.EXPIRING)
        assertEquals(listOf(1), ids(PlayerQuery.run(world, list, f)))
        val secure = PlayerQuery.run(world, list, PlayerFilter(contract = ContractFilter.SECURE))
        assertEquals(listOf(3), ids(secure))
    }

    @Test fun moraleAgeAndAvailabilityFilters() {
        val injured = mk(1, "Hurt").also { it.injWeeks = 3 }
        val banned = mk(2, "Banned").also { it.suspWeeks = 1 }
        val happy = mk(3, "Happy", morale = 90f, age = 19)
        val sad = mk(4, "Sad", morale = 30f, age = 33)
        val all = listOf(injured, banned, happy, sad)
        assertEquals(listOf(1), ids(PlayerQuery.run(world, all, PlayerFilter(availability = AvailabilityFilter.INJURED))))
        assertEquals(listOf(2), ids(PlayerQuery.run(world, all, PlayerFilter(availability = AvailabilityFilter.SUSPENDED))))
        assertEquals(listOf(1, 2), ids(PlayerQuery.run(world, all, PlayerFilter(availability = AvailabilityFilter.UNAVAILABLE))).sorted())
        assertEquals(listOf(3, 4), ids(PlayerQuery.run(world, all, PlayerFilter(availability = AvailabilityFilter.FIT))).sorted())
        assertEquals(listOf(3), ids(PlayerQuery.run(world, all, PlayerFilter(minMorale = 80))))
        assertEquals(listOf(3), ids(PlayerQuery.run(world, all, PlayerFilter(maxAge = 21))))
        assertEquals(listOf(4), ids(PlayerQuery.run(world, all, PlayerFilter(minAge = 30))))
    }

    @Test fun listedFilter() {
        val a = mk(1, "A").also { it.listed = true }
        val b = mk(2, "B")
        assertEquals(listOf(1), ids(PlayerQuery.run(world, listOf(a, b), PlayerFilter(contract = ContractFilter.LISTED))))
    }

    @Test fun sortingIsStableAndTiesBreakById() {
        val list = listOf(mk(5, "E", ovr = 60), mk(2, "B", ovr = 60), mk(9, "I", ovr = 70), mk(1, "A", ovr = 60))
        val desc = PlayerQuery.run(world, list, sort = PlayerSort(SortKey.OVERALL))
        assertEquals(listOf(9, 1, 2, 5), ids(desc))
        val asc = PlayerQuery.run(world, list, sort = PlayerSort(SortKey.OVERALL, descending = false))
        assertEquals(listOf(1, 2, 5, 9), ids(asc))
        assertEquals("repeating the query must give the identical order", ids(desc), ids(PlayerQuery.run(world, list.reversed(), sort = PlayerSort(SortKey.OVERALL))))
    }

    @Test fun everySortKeyOrdersSensibly() {
        val young = mk(1, "Zed", age = 18, pa = 90, ovr = 40, wage = 300, condition = 50f, morale = 40f)
        val old = mk(2, "Abe", age = 34, pa = 50, ovr = 80, wage = 9000, condition = 99f, morale = 95f)
        val list = listOf(young, old)
        fun top(key: SortKey) = ids(PlayerQuery.run(world, list, sort = PlayerSort(key))).first()
        assertEquals(2, top(SortKey.OVERALL))
        assertEquals(1, top(SortKey.POTENTIAL))
        assertEquals(1, top(SortKey.AGE))   // youngest first by default
        assertEquals(2, top(SortKey.WAGE))
        assertEquals(2, top(SortKey.FITNESS))
        assertEquals(2, top(SortKey.MORALE))
        assertEquals(2, top(SortKey.NAME))  // "Abe" before "Zed"
        assertEquals(2, top(SortKey.VALUE)) // an 80-rated 34-year-old is still worth more than a 40-rated teenager
    }

    @Test fun positionSortGroupsByPositionThenBestFirst() {
        val list = listOf(mk(1, "A", Pos.ST, ovr = 50), mk(2, "B", Pos.GK, ovr = 40), mk(3, "C", Pos.GK, ovr = 60), mk(4, "D", Pos.CM, ovr = 55))
        assertEquals(listOf(3, 2, 4, 1), ids(PlayerQuery.run(world, list, sort = PlayerSort(SortKey.POSITION))))
    }

    @Test fun togglingASortKeyFlipsDirectionAndANewKeyUsesItsNaturalDirection() {
        val s = PlayerSort(SortKey.OVERALL)
        assertTrue(s.descending)
        assertFalse(s.toggled(SortKey.OVERALL).descending)
        assertFalse(s.toggled(SortKey.AGE).descending)
        assertTrue(s.toggled(SortKey.WAGE).descending)
    }

    @Test fun sortUsesTheValuesTheManagerCanSee() {
        val list = listOf(mk(1, "A", ovr = 80), mk(2, "B", ovr = 40))
        val fogged = PlayerQuery.run(world, list, sort = PlayerSort(SortKey.OVERALL), overallOf = { if (it.id == 1) 30 else 55 })
        assertEquals(listOf(2, 1), ids(fogged))
    }

    @Test fun emptyResultsExplainThemselves() {
        val none = PlayerQuery.run(world, emptyList())
        assertEquals("There are no players to show here yet.", none.emptyMessage)
        val list = listOf(mk(1, "Alex"))
        val miss = PlayerQuery.run(world, list, PlayerFilter(text = "zzzz", positions = setOf(Pos.GK), minFitness = 90))
        assertNotNull(miss.emptyMessage)
        assertTrue(miss.emptyMessage!!, miss.emptyMessage!!.contains("zzzz") && miss.emptyMessage!!.contains("GK") && miss.emptyMessage!!.contains("90%"))
        assertEquals(1, miss.totalBeforeFilter)
        assertNull(PlayerQuery.run(world, list).emptyMessage)
    }

    @Test fun filterDescriptionAndDefaults() {
        assertTrue(PlayerFilter().isDefault)
        assertEquals("", PlayerFilter().describe())
        assertFalse(PlayerFilter(minMorale = 1).isDefault)
        assertTrue(PlayerFilter(contract = ContractFilter.EXPIRING).describe().contains("expiring"))
    }

    @Test fun comparisonScoresTheSameMeasuresForBothPlayers() {
        val a = mk(1, "Alpha", ovr = 70, pa = 80, wage = 2000, age = 22)
        val b = mk(2, "Beta", ovr = 60, pa = 80, wage = 5000, age = 30)
        val cmp = PlayerComparison.compare(world, a, b)
        val byLabel = cmp.rows.associateBy { it.label }
        assertEquals(-1, byLabel.getValue("Overall").winner)
        assertEquals(0, byLabel.getValue("Potential").winner)
        assertEquals("lower wage wins", -1, byLabel.getValue("Wage").winner)
        assertEquals("age is shown but never scored", 0, byLabel.getValue("Age").winner)
        assertEquals(0, byLabel.getValue("Appearances").winner)
        assertEquals(ATTRS.size, cmp.rows.count { r -> ATTRS.any { it.label == r.label } })
        assertTrue(cmp.winsA > cmp.winsB)
        assertTrue(cmp.verdict("Alpha", "Beta").startsWith("Alpha leads"))
    }

    @Test fun comparisonUsesTheSuppliedAttributeView() {
        val a = mk(1, "Alpha"); val b = mk(2, "Beta")
        val cmp = PlayerComparison.compare(world, a, b, attrOf = { p, _ -> if (p.id == 1) 5 else 15 })
        assertTrue(cmp.winsB > cmp.winsA)
        assertTrue(cmp.verdict("Alpha", "Beta").startsWith("Beta leads"))
    }

    @Test fun identicalPlayersAreLevel() {
        val a = mk(1, "Same"); val b = mk(2, "Same")
        val cmp = PlayerComparison.compare(world, a, b)
        assertEquals(0, cmp.winsA); assertEquals(0, cmp.winsB)
        assertTrue(cmp.verdict("A", "B").contains("level"))
    }

    @Test fun worksOnAFullGeneratedSquadWithoutChangingIt() {
        val w = WorldGen.generate(21L, "Q", 0)
        val squad = w.squad(w.human())
        val before = StateFingerprint.of(w)
        val r = PlayerQuery.run(w, squad, PlayerFilter(positions = setOf(Pos.CM, Pos.DM)), PlayerSort(SortKey.POTENTIAL))
        assertTrue(r.players.isNotEmpty())
        assertTrue(r.players.zipWithNext().all { (x, y) -> x.pa >= y.pa })
        assertTrue(r.players.all { it.pos == Pos.CM || it.pos == Pos.DM || it.pos2 == Pos.CM || it.pos2 == Pos.DM })
        PlayerComparison.compare(w, r.players.first(), r.players.last())
        assertEquals("querying must not mutate the career", before, StateFingerprint.of(w))
    }
}
