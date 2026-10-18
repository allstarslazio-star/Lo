package com.nerdfm.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

object HairStyle {
    const val BALD = 0
    const val BUZZ = 1
    const val SHORT = 2
    const val PART = 3
    const val CURLY = 4
    const val AFRO = 5
    const val LONG = 6
    const val MOHAWK = 7
    const val BUN = 8
    const val RECEDING = 9
    const val BOB = 10
    const val COUNT = 11
}

object BeardStyle {
    const val NONE = 0
    const val STUBBLE = 1
    const val MOUSTACHE = 2
    const val GOATEE = 3
    const val FULL = 4
}

/**
 * Everything needed to draw one face, as plain numbers (colours are 0xAARRGGBB longs), so it can be generated and tested
 * without any UI. The face is a pure function of (id, nation, age, kind, gender): the same person always looks the same,
 * and nothing is stored in the save.
 */
class AvatarSpec(
    val skin: Long, val skinShade: Long,
    val hair: Long, val hairStyle: Int,
    val beard: Int, val eye: Long,
    val faceW: Float, val faceH: Float,
    val eyeSpacing: Float, val brow: Float, val nose: Float, val mouthW: Float, val ear: Float,
    val glasses: Boolean, val lines: Boolean, val lip: Long, val female: Boolean,
    val skinIndex: Int
) {
    /** Stable text form, handy for tests and for checking two faces differ. */
    fun signature(): String = listOf(skin, hair, hairStyle, beard, eye, (faceW * 100).toInt(), (faceH * 100).toInt(),
        (eyeSpacing * 100).toInt(), (brow * 100).toInt(), (nose * 100).toInt(), (mouthW * 100).toInt(), glasses, lines).joinToString(",")

    companion object {
        /** Eight skin tones, lightest first. */
        val SKIN = longArrayOf(0xFFF5D6C3L, 0xFFEBC3A4L, 0xFFDDAE86L, 0xFFC99269L, 0xFFB07850L, 0xFF8F5B3AL, 0xFF6E432AL, 0xFF4F2F1EL)
        /** Black, dark brown, brown, light brown, blond, ginger, grey. */
        val HAIR = longArrayOf(0xFF1C1713L, 0xFF3B2A1EL, 0xFF5B3F2AL, 0xFF8A6A47L, 0xFFD8B56AL, 0xFFA4492AL, 0xFFB5B5B5L)
        val EYES = longArrayOf(0xFF3B2A1EL, 0xFF5B3F2AL, 0xFF2F6F9FL, 0xFF4F7F4AL, 0xFF6B6B6BL)
        const val GREY = 6

        // skin centre and spread per nation (index order of Names.nations); broad spreads keep every nation diverse
        private val SKIN_CENTRE = floatArrayOf(2.5f, 0.8f, 1.6f, 2.6f, 6.0f, 2.4f, 1.5f, 1.8f, 3.4f, 3.2f, 1.2f, 1.8f)
        private val SKIN_SPREAD = floatArrayOf(1.0f, 0.7f, 0.8f, 1.1f, 1.0f, 0.9f, 1.3f, 1.3f, 1.0f, 1.5f, 0.8f, 0.7f)
        // hair colour weights (black, dark brown, brown, light brown, blond, ginger) per nation
        private val HAIR_W = arrayOf(
            intArrayOf(30, 35, 20, 8, 4, 3), intArrayOf(5, 15, 20, 20, 35, 5), intArrayOf(10, 25, 30, 15, 15, 5),
            intArrayOf(35, 35, 18, 6, 3, 3), intArrayOf(85, 12, 3, 0, 0, 0), intArrayOf(40, 35, 15, 5, 3, 2),
            intArrayOf(8, 20, 25, 22, 12, 13), intArrayOf(15, 30, 28, 14, 10, 3), intArrayOf(45, 35, 15, 3, 1, 1),
            intArrayOf(35, 35, 20, 6, 3, 1), intArrayOf(8, 22, 28, 20, 18, 4), intArrayOf(90, 8, 2, 0, 0, 0))

        private fun blend(a: Long, b: Long, t: Float): Long {
            fun ch(shift: Int): Long {
                val x = ((a shr shift) and 0xFFL).toFloat()
                val y = ((b shr shift) and 0xFFL).toFloat()
                return (x + (y - x) * t).toInt().coerceIn(0, 255).toLong()
            }
            return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        private fun gauss(h: Long): Float {
            val u1 = (Mix.unit(Mix.h(h, 1L)) + 1e-6f).coerceAtMost(0.999999f)
            val u2 = Mix.unit(Mix.h(h, 2L))
            return (sqrt(-2.0 * ln(u1.toDouble())) * cos(2.0 * PI * u2)).toFloat()
        }

        private fun weighted(h: Long, weights: IntArray): Int {
            var r = Mix.int(h, weights.sum().coerceAtLeast(1))
            for (i in weights.indices) { r -= weights[i]; if (r < 0) return i }
            return 0
        }

        /**
         * [kind] keeps different groups of people from sharing faces: 0 player, 1 staff, 2 board member, 3 referee.
         * About one person in eight takes part of their looks from another nation (mixed families).
         */
        fun of(id: Int, nation: Int, age: Int, kind: Int = 0, female: Boolean = false): AvatarSpec {
            val n = Math.floorMod(nation, SKIN_CENTRE.size)
            val h = Mix.h(id.toLong(), kind.toLong(), 8800L)
            fun k(i: Long) = Mix.h(h, i)

            // --- skin: mostly the nation's range, sometimes another's (mixed families)
            var centre = SKIN_CENTRE[n]; var spread = SKIN_SPREAD[n]
            if (Mix.unit(k(10)) < 0.12f) {
                val o = Mix.int(k(11), SKIN_CENTRE.size)
                centre = (centre + SKIN_CENTRE[o]) / 2f; spread = 1.4f
            }
            val skinIdx = (centre + gauss(k(12)) * spread).toInt().coerceIn(0, SKIN.size - 1)
            val skin = SKIN[skinIdx]
            val shade = blend(skin, 0xFF2A140AL, 0.28f)

            // --- age: grey hair and hair loss
            val greyChance = ((age - 32) / 20f).coerceIn(0f, 0.9f)
            val balding = if (female) 0f else ((age - 27) / 30f).coerceIn(0f, 0.5f)
            var hairCol = weighted(k(20), HAIR_W[n])
            if (Mix.unit(k(21)) < greyChance) hairCol = GREY
            // --- hair style
            val dark = skinIdx >= 5
            val style: Int
            if (female) {
                val opts = intArrayOf(HairStyle.LONG, HairStyle.BOB, HairStyle.BUN, HairStyle.SHORT, HairStyle.CURLY, HairStyle.PART)
                val w = intArrayOf(30, 20, 15, 12, if (dark) 15 else 6, 10)
                style = opts[weighted(k(22), w)]
            } else if (Mix.unit(k(23)) < balding * (if (age >= 40) 1.6f else 1f)) {
                style = if (Mix.unit(k(24)) < 0.5f) HairStyle.BALD else HairStyle.RECEDING
            } else {
                val opts = intArrayOf(HairStyle.BUZZ, HairStyle.SHORT, HairStyle.PART, HairStyle.CURLY, HairStyle.AFRO, HairStyle.LONG, HairStyle.MOHAWK, HairStyle.BUN)
                val w = intArrayOf(12, 28, 18, if (dark) 22 else 10, if (dark) 14 else 1, 5, if (age <= 26) 4 else 1, 3)
                style = opts[weighted(k(22), w)]
            }

            // --- beard (men only), more likely and fuller with age
            var beard = BeardStyle.NONE
            if (!female && age >= 18 && Mix.unit(k(30)) < (0.18f + (age - 20) * 0.012f).coerceIn(0.1f, 0.5f)) {
                beard = 1 + weighted(k(31), intArrayOf(40, 15, 15, 30))
            }

            // --- eyes: lighter eyes are possible with lighter skin
            val eye = if (skinIdx <= 2 && Mix.unit(k(40)) < 0.3f) EYES[2 + Mix.int(k(41), 3)] else EYES[Mix.int(k(42), 2)]

            val glasses = (age >= 38 || kind == 1) && Mix.unit(k(50)) < 0.12f
            val lines = age >= 34
            val lip = blend(skin, if (female) 0xFFB5424AL else 0xFF8A3B33L, if (female) 0.55f else 0.35f)
            return AvatarSpec(
                skin, shade, HAIR[hairCol], style, beard, eye,
                0.86f + Mix.unit(k(60)) * 0.16f, 0.92f + Mix.unit(k(61)) * 0.14f,
                0.85f + Mix.unit(k(62)) * 0.3f, 0.7f + Mix.unit(k(63)) * 0.8f, 0.7f + Mix.unit(k(64)) * 0.7f,
                0.8f + Mix.unit(k(65)) * 0.5f, 0.8f + Mix.unit(k(66)) * 0.5f,
                glasses, lines, lip, female, skinIdx
            )
        }
    }
}
