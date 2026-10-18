package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faces and flags: pure specification tests (the drawing itself is checked on a phone). */
class PortraitSpecTest {
    private fun faces(nation: Int, n: Int, age: Int = 25, kind: Int = 0, female: Boolean = false) =
        (1..n).map { AvatarSpec.of(it, nation, age, kind, female) }

    @Test fun theSamePersonAlwaysHasTheSameFace() {
        for (nation in Names.nations.indices) for (id in listOf(1, 77, 5000)) {
            assertEquals(AvatarSpec.of(id, nation, 27).signature(), AvatarSpec.of(id, nation, 27).signature())
        }
        assertNotEquals(AvatarSpec.of(10, 0, 27, kind = 0).signature(), AvatarSpec.of(10, 0, 27, kind = 1).signature())
    }

    @Test fun everyFieldStaysInRange() {
        for (nation in -1..Names.nations.size) for (age in listOf(16, 25, 38, 55)) for (female in listOf(false, true)) {
            for (f in faces(nation, 120, age, 0, female)) {
                assertTrue(f.skinIndex in 0 until AvatarSpec.SKIN.size)
                assertTrue(f.hairStyle in 0 until HairStyle.COUNT)
                assertTrue(f.beard in 0..4)
                assertTrue(f.faceW in 0.8f..1.1f && f.faceH in 0.85f..1.1f)
                assertTrue(f.eyeSpacing in 0.8f..1.2f && f.brow in 0.6f..1.6f && f.nose in 0.6f..1.5f && f.mouthW in 0.7f..1.4f && f.ear in 0.7f..1.4f)
                assertTrue(f.skin in AvatarSpec.SKIN.toList() && f.hair in AvatarSpec.HAIR.toList() && f.eye in AvatarSpec.EYES.toList())
            }
        }
    }

    @Test fun facesAreVaried() {
        val sigs = faces(0, 1000).map { it.signature() }.toSet()
        assertTrue("distinct faces: ${sigs.size}", sigs.size >= 950)
    }

    @Test fun everyNationShowsARangeOfSkinTonesButWithARecognisableCentre() {
        val mean = HashMap<Int, Double>()
        for (nation in Names.nations.indices) {
            val f = faces(nation, 1500)
            assertTrue("${Names.nations[nation].name} spans ${f.map { it.skinIndex }.toSet().size} tones", f.map { it.skinIndex }.toSet().size >= 3)
            mean[nation] = f.map { it.skinIndex }.average()
        }
        val tamburi = Names.nations.indexOfFirst { it.name == "Tamburi" }
        val nordheim = Names.nations.indexOfFirst { it.name == "Nordheim" }
        assertTrue(mean.getValue(tamburi) > mean.getValue(nordheim) + 2.0)
    }

    @Test fun ageBringsGreyHairAndHairLoss() {
        val young = faces(0, 2000, 20); val old = faces(0, 2000, 40)
        assertTrue(old.count { it.hair == AvatarSpec.HAIR[AvatarSpec.GREY] } > young.count { it.hair == AvatarSpec.HAIR[AvatarSpec.GREY] } * 5)
        fun thin(l: List<AvatarSpec>) = l.count { it.hairStyle == HairStyle.BALD || it.hairStyle == HairStyle.RECEDING }
        assertTrue("thin hair young ${thin(young)} old ${thin(old)}", thin(old) > thin(young) * 3)
        assertEquals(0, faces(0, 500, 16).count { it.lines })
        assertTrue(faces(0, 500, 40).all { it.lines })
    }

    @Test fun womenNeverGetBeardsOrMaleOnlyStyles() {
        for (f in faces(3, 1000, 30, 1, true)) {
            assertEquals(BeardStyle.NONE, f.beard)
            assertTrue(f.female)
            assertTrue(f.hairStyle in listOf(HairStyle.LONG, HairStyle.BOB, HairStyle.BUN, HairStyle.SHORT, HairStyle.CURLY, HairStyle.PART))
        }
        assertTrue(faces(0, 1000, 40).any { it.beard != BeardStyle.NONE })
        assertTrue(faces(0, 1000, 16).count { it.beard != BeardStyle.NONE } == 0)
    }

    @Test fun everyNationHasADistinctWellFormedFlag() {
        val seen = HashSet<String>()
        for (nation in Names.nations.indices) {
            val d = Flags.design(nation)
            assertTrue("${Names.nations[nation].name} flag has no layers", d.size >= 2)
            assertEquals(FlagLayer.RECT, d[0].kind)
            assertTrue("first layer must fill the flag", d[0].a == 0f && d[0].b == 0f && d[0].c == 1f && d[0].d == 1f)
            assertTrue(d.map { it.color }.toSet().size >= 2)
            for (l in d) {
                assertTrue(l.color and 0xFF000000L == 0xFF000000L)
                assertTrue(l.a in 0f..1f && l.b in 0f..1f && l.c in 0f..1f && l.d in 0f..1f)
            }
            assertTrue("duplicate flag design for ${Names.nations[nation].name}", seen.add(Flags.signature(nation)))
        }
        assertEquals(Flags.design(-1).size, Flags.design(999).size)
        assertFalse(Flags.design(-1).isEmpty())
    }
}
