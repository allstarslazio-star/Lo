package com.nerdfm.game

/** Staff quality: medical/fitness departments speed recovery, the assistant manager lifts match-day morale. */
object StaffFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> = listOf(
        Factor("Medical and fitness staff", Channel.RECOVERY, Staffing.recoveryFactor(w, club.id)),
        Factor("Assistant manager", Channel.MORALE, Staffing.moraleFactor(w, club.id))
    )
}

/** Board climate and the director-of-football relationship (only the human club's board is simulated week to week). */
object BoardFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> {
        if (!club.human) return emptyList()
        val board = BoardRules.boardOf(w, club.id)
        val pressure = if (club.boardConfidence < 30f) -1f else if (club.boardConfidence > 75f) 0.5f else 0f
        val harmony = (board.director.alignment - 50f) / 50f * 0.8f
        return listOf(Factor("Board pressure", Channel.MORALE, pressure), Factor("Director alignment", Channel.MORALE, harmony))
    }
}

/** Club culture: stability, winning habit and academy pathway lift or sink the dressing room. */
object CultureFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> =
        listOf(Factor("Club culture", Channel.MORALE, Identity.moraleFactor(w, club.id)))
}
