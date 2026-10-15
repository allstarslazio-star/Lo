package com.nerdfm.game

import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterTacticDriftTest {
    private fun meanAiPressingGene(humanPressingTotal: Long): Double {
        val w = WorldGen.generate(777L, "Drift", 0)
        w.humanTacticSamples = 100
        w.humanPressingTotal = humanPressingTotal
        w.humanLineTotal = 100L
        w.humanTempoTotal = 100L
        val rnd = Random(1)
        repeat(50) { ManagerPopulationTraining.evolve(w, rnd) }
        return w.clubs.filter { !it.human }.map { it.managerGenome.pressing.toDouble() }.average()
    }

    @Test fun counterTacticBiasDoesNotCompoundIntoStoredGenomes() {
        // Human never presses (0) or always presses (2): AI genes must not run away to either bound.
        val lowPress = meanAiPressingGene(0L)
        val highPress = meanAiPressingGene(200L)
        assertTrue("mean AI pressing gene after 50 seasons was $lowPress", lowPress in 0.6..1.4)
        assertTrue("mean AI pressing gene after 50 seasons was $highPress", highPress in 0.6..1.4)
    }
}
