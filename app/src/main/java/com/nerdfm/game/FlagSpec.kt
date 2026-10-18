package com.nerdfm.game

/**
 * One shape of a flag. Positions and sizes are fractions of the flag (x and width of its width, y and height of its
 * height), except circle radii, which are a fraction of the height.
 */
class FlagLayer(val kind: Int, val color: Long, val a: Float = 0f, val b: Float = 0f, val c: Float = 0f, val d: Float = 0f) {
    companion object {
        /** Filled rectangle: x = a, y = b, width = c, height = d. */
        const val RECT = 0
        /** Filled circle: centre x = a, centre y = b, radius = c (of the height). */
        const val CIRCLE = 1
        /** Diamond: centre x = a, centre y = b, half-width = c, half-height = d. */
        const val DIAMOND = 2
        /** Both diagonals as a saltire, band thickness = a (of the height). */
        const val SALTIRE = 3
    }
}

/** Flags of the fictional nations, in the order of [Names.nations]. They are invented designs, not real flags. */
object Flags {
    private fun full(color: Long) = FlagLayer(FlagLayer.RECT, color, 0f, 0f, 1f, 1f)
    private fun rect(color: Long, x: Float, y: Float, w: Float, h: Float) = FlagLayer(FlagLayer.RECT, color, x, y, w, h)
    private fun circle(color: Long, x: Float, y: Float, r: Float) = FlagLayer(FlagLayer.CIRCLE, color, x, y, r)
    private fun diamond(color: Long, x: Float, y: Float, rx: Float, ry: Float) = FlagLayer(FlagLayer.DIAMOND, color, x, y, rx, ry)

    private val DESIGNS: Map<String, List<FlagLayer>> = mapOf(
        "Valdoria" to listOf(full(0xFF1F7A6EL), rect(0xFFF1E9D6L, 0.3333f, 0f, 0.3334f, 1f), rect(0xFFC4543AL, 0.6667f, 0f, 0.3333f, 1f), circle(0xFFE0B040L, 0.5f, 0.5f, 0.14f)),
        "Nordheim" to listOf(full(0xFF1B3A6BL), rect(0xFFE8C547L, 0.28f, 0f, 0.14f, 1f), rect(0xFFE8C547L, 0f, 0.43f, 1f, 0.14f)),
        "Kessara" to listOf(full(0xFF9C2A2AL), rect(0xFFF2F2F2L, 0f, 0.3333f, 1f, 0.3334f), rect(0xFF2D4A7AL, 0f, 0.6667f, 1f, 0.3333f), circle(0xFFE0B040L, 0.5f, 0.5f, 0.13f)),
        "Almeron" to listOf(full(0xFFC8452DL), rect(0xFFF2C14EL, 0f, 0.25f, 1f, 0.5f), diamond(0xFF7A1F14L, 0.5f, 0.5f, 0.1f, 0.2f)),
        "Tamburi" to listOf(full(0xFF2E8B57L), rect(0xFFE9B824L, 0.3333f, 0f, 0.3334f, 1f), rect(0xFFC0392BL, 0.6667f, 0f, 0.3333f, 1f), diamond(0xFF111111L, 0.5f, 0.5f, 0.08f, 0.24f)),
        "Zephyria" to listOf(full(0xFF4A90C8L), rect(0xFFFFFFFFL, 0f, 0.2f, 1f, 0.2f), rect(0xFFFFFFFFL, 0f, 0.6f, 1f, 0.2f),
            rect(0xFF1F4E8CL, 0f, 0f, 0.4f, 0.6f), rect(0xFFFFFFFFL, 0.17f, 0f, 0.06f, 0.6f), rect(0xFFFFFFFFL, 0f, 0.27f, 0.4f, 0.06f)),
        "Brannock" to listOf(full(0xFF1B2F5EL), FlagLayer(FlagLayer.SALTIRE, 0xFFFFFFFFL, 0.16f), rect(0xFFFFFFFFL, 0.42f, 0f, 0.16f, 1f), rect(0xFFFFFFFFL, 0f, 0.38f, 1f, 0.24f),
            rect(0xFFC1272DL, 0.455f, 0f, 0.09f, 1f), rect(0xFFC1272DL, 0f, 0.435f, 1f, 0.13f)),
        "Quillon" to listOf(full(0xFF2A4D9BL), rect(0xFFF4EFE6L, 0.3333f, 0f, 0.3334f, 1f), rect(0xFFD4A017L, 0.6667f, 0f, 0.3333f, 1f)),
        "Daruvia" to listOf(full(0xFF1E6B4AL), circle(0xFFFFFFFFL, 0.4f, 0.5f, 0.26f), circle(0xFF1E6B4AL, 0.45f, 0.5f, 0.21f), diamond(0xFFE0B040L, 0.62f, 0.5f, 0.05f, 0.12f)),
        "Ixtal" to listOf(full(0xFF4FA3D9L), rect(0xFFFFFFFFL, 0f, 0.3333f, 1f, 0.3334f), diamond(0xFFE0B040L, 0.5f, 0.5f, 0.09f, 0.24f)),
        "Korvane" to listOf(full(0xFFB5322BL), rect(0xFF1E1E1EL, 0f, 0.4167f, 1f, 0.1666f), circle(0xFFE0B040L, 0.5f, 0.5f, 0.1f)),
        "Itsuki" to listOf(full(0xFFF5F3EEL), circle(0xFF2E3A87L, 0.42f, 0.5f, 0.24f), rect(0xFF2E3A87L, 0f, 0.88f, 1f, 0.12f))
    )

    private val FALLBACK = listOf(full(0xFF555555L), circle(0xFFFFFFFFL, 0.5f, 0.5f, 0.2f))

    fun design(nation: Int): List<FlagLayer> {
        if (nation < 0 || nation >= Names.nations.size) return FALLBACK
        return DESIGNS[Names.nations[nation].name] ?: FALLBACK
    }

    /** Short text form used to compare designs. */
    fun signature(nation: Int): String = design(nation).joinToString("|") { "${it.kind}:${it.color}:${it.a}:${it.b}:${it.c}:${it.d}" }
}
