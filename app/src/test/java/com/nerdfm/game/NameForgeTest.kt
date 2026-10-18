package com.nerdfm.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The richer, opt-in name generator. */
class NameForgeTest {
    @get:Rule val temp = TemporaryFolder()

    private val legal = Regex("^[A-Za-z][A-Za-z'\\- ]*[A-Za-z]$")

    private fun names(nat: Int, n: Int, o: NameOptions = NameOptions(), seed: Long = 5L): List<PersonName> {
        val rnd = Random(seed + nat)
        return List(n) { NameForge.make(nat, rnd, o) }
    }

    @Test fun sameSeedGivesTheSameNames() {
        for (nat in Names.nations.indices) assertEquals(names(nat, 50).map { it.full }, names(nat, 50).map { it.full })
    }

    @Test fun everyNationProducesCleanNames() {
        for (nat in Names.nations.indices) for (o in listOf(NameOptions(), NameOptions(gender = Gender.FEMALE), NameOptions(age = 36), NameOptions(foreignChance = 0.6f))) {
            for (p in names(nat, 400, o)) {
                assertTrue("'${p.full}'", legal.matches(p.full))
                assertFalse("double space in '${p.full}'", p.full.contains("  "))
                assertTrue(p.full.length in 5..28)
                val low = p.full.lowercase()
                for (b in listOf("fuck", "cunt", "shit", "nazi", "fag")) assertFalse("'${p.full}'", low.contains(b))
                assertFalse(Regex("[bcdfghjklmnpqrstvwxz]{5,}").containsMatchIn(low.filter { it.isLetter() }))
            }
        }
    }

    @Test fun namesAreVariedInEveryNation() {
        for (nat in Names.nations.indices) {
            val all = names(nat, 600).map { it.full }
            assertTrue("${Names.nations[nat].name}: ${all.toSet().size} distinct of 600", all.toSet().size >= 540)
        }
    }

    @Test fun nationalCustomsAppear() {
        fun patterns(nation: String) = names(Names.nations.indexOfFirst { it.name == nation }, 1500, NameOptions(foreignChance = 0f)).map { it.pattern }.toSet()
        assertTrue(patterns("Almeron").contains("double"))
        assertTrue(patterns("Ixtal").contains("double"))
        assertTrue(patterns("Nordheim").contains("patronymic"))
        assertTrue(patterns("Brannock").contains("prefix"))
        assertTrue(patterns("Daruvia").contains("prefix"))
        assertTrue(patterns("Quillon").contains("particle"))
        assertTrue(patterns("Korvane").contains("particle"))
        assertTrue(patterns("Itsuki") == setOf("plain"))
    }

    @Test fun patronymicsAreBuiltFromAGivenName() {
        val nat = Names.nations.indexOfFirst { it.name == "Nordheim" }
        val p = names(nat, 1500, NameOptions(foreignChance = 0f)).first { it.pattern == "patronymic" }
        assertTrue(p.surname.endsWith("sen"))
    }

    @Test fun femaleFormsAndGivenNamesFollowGender() {
        val kessara = Names.nations.indexOfFirst { it.name == "Kessara" }
        for (p in names(kessara, 800, NameOptions(gender = Gender.FEMALE, foreignChance = 0f)))
            assertFalse("'${p.full}'", p.surname.endsWith("ov") || p.surname.endsWith("ski"))
        for (nat in Names.nations.indices) {
            val inMale = names(nat, 300, NameOptions(gender = Gender.FEMALE)).count { it.given in Names.nations[nat].first }
            assertTrue("female names drawn from the male list: $inMale", inMale <= 15)
        }
    }

    @Test fun olderPlayersGetMoreClassicGivenNames() {
        val v = Names.nations.indexOfFirst { it.name == "Valdoria" }
        fun classic(age: Int) = names(v, 3000, NameOptions(age = age)).count { it.given !in Names.nations[v].first }
        val young = classic(19); val old = classic(35)
        assertTrue("young $young old $old", old > young * 3)
    }

    @Test fun mixedHeritageHappensAtAboutTheRequestedRate() {
        val v = Names.nations.indexOfFirst { it.name == "Brannock" }
        val mixed = names(v, 2000, NameOptions(foreignChance = 0.5f, nicknames = false)).count { it.pattern == "mixed" }
        assertTrue("mixed $mixed of 2000", mixed in 500..1100)
    }

    @Test fun nicknamesAreOccasionalAndFormatsAreSensible() {
        val all = names(0, 4000)
        val nick = all.count { it.nickname != null }
        assertTrue("nicknames $nick of 4000", nick in 120..380)
        val p = all.first { it.nickname != null }
        assertTrue(p.withNickname.contains("'${p.nickname}'"))
        assertEquals(p.given.first() + ". " + p.surname, p.short)
        assertNull(names(0, 500, NameOptions(nicknames = false)).firstOrNull { it.nickname != null })
        assertNull(names(0, 500, NameOptions(age = 16)).firstOrNull { it.nickname != null })
    }

    @Test fun theOldGeneratorIsUntouchedAndStaysTheDefault() {
        val rnd1 = Random(42L); val rnd2 = Random(42L)
        for (nat in Names.nations.indices) assertEquals(Names.fullName(nat, rnd1), Names.fullName(nat, rnd2))
        val w = WorldGen.generate(7L, "x", 0)
        assertFalse(w.richNames)
        assertEquals(0, NameForge.fixDuplicates(w))
    }

    @Test fun aRichWorldHasUniqueNamesAndKeepsTheSettingThroughSaveAndLoad() {
        val w = WorldGen.generate(8L, "x", 0, richNames = true)
        assertTrue(w.richNames)
        val all = w.players.values.map { it.name }
        assertEquals("duplicate names after generation", all.size, all.toSet().size)
        assertTrue(all.all { legal.matches(it) })
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertTrue(loaded.richNames)
    }

    @Test fun duplicateFixerIsDeterministicAndOnlyTouchesLaterPlayers() {
        fun build(): World {
            val w = WorldGen.generate(9L, "x", 0, richNames = true)
            val a = w.players.values.sortedBy { it.id }
            a[10].name = a[3].name
            a[20].name = a[3].name
            return w
        }
        val w1 = build(); val w2 = build()
        val first = w1.players.values.sortedBy { it.id }[3].name
        assertEquals(2, NameForge.fixDuplicates(w1))
        NameForge.fixDuplicates(w2)
        assertEquals(w1.players.values.sortedBy { it.id }.map { it.name }, w2.players.values.sortedBy { it.id }.map { it.name })
        assertEquals(first, w1.players.values.sortedBy { it.id }[3].name)
        assertEquals(w1.players.size, w1.players.values.map { it.name }.toSet().size)
    }
}
