package com.nerdfm.game

import java.io.Serializable
import kotlin.random.Random

/** Match-day environment. It is generated from a stable fixture seed and is not career-save state. */
data class MatchConditions(
    val weather: Weather = Weather.CLEAR,
    val pitch: Pitch = Pitch.GOOD
) : Serializable {
    enum class Weather(val label: String, val passing: Float, val shooting: Float, val injury: Float) {
        CLEAR("Clear", 1.00f, 1.00f, 1.00f),
        RAIN("Rain", 0.94f, 0.97f, 1.12f),
        WIND("Windy", 0.96f, 0.93f, 1.00f),
        HOT("Hot", 0.97f, 0.98f, 1.20f)
    }

    enum class Pitch(val label: String, val passing: Float, val shooting: Float, val injury: Float) {
        GOOD("Good", 1.00f, 1.00f, 1.00f),
        WET("Wet", 0.96f, 0.97f, 1.08f),
        HEAVY("Heavy", 0.92f, 0.95f, 1.18f),
        POOR("Poor", 0.90f, 0.94f, 1.15f)
    }

    val passingMultiplier: Float get() = weather.passing * pitch.passing
    val shootingMultiplier: Float get() = weather.shooting * pitch.shooting
    val injuryMultiplier: Float get() = weather.injury * pitch.injury
    val summary: String get() = "${weather.label} · ${pitch.label} pitch"

    companion object {
        val NORMAL = MatchConditions()

        /** Stable per-fixture environment without consuming the match engine's random stream. */
        fun forSeed(seed: Long): MatchConditions {
            val random = Random(seed xor 0x4E657264464D4C)
            val weather = Weather.values()[random.nextInt(Weather.values().size)]
            val pitch = when {
                weather == Weather.RAIN && random.nextFloat() < 0.62f -> Pitch.WET
                weather == Weather.HOT && random.nextFloat() < 0.22f -> Pitch.HEAVY
                else -> Pitch.values()[random.nextInt(Pitch.values().size)]
            }
            return MatchConditions(weather, pitch)
        }
    }
}
