package com.nerdfm.game

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// What the player is shown about other clubs' players: exact for his own squad and fully scouted players, a noisy estimate otherwise.
// Kept free of UI code so it can be unit-tested and timed.

fun isKnown(w: World, p: Player): Boolean = p.clubId == w.humanClubId || w.scouted.contains(p.id)

fun scoutErr(w: World): Float = Analytics.scoutError(w)

fun shownAttr(w: World, p: Player, i: Int): Int = if (isKnown(w, p)) p.attrs[i] else shownAttrWith(p, i, scoutErr(w))

/** The scouting error is the same for every attribute and player, so callers that need many values compute it once. */
fun shownAttrWith(p: Player, i: Int, err: Float): Int {
    val noise = (((p.id * 7919 + i * 104729) % 5) + 5) % 5 - 2
    return clampI(p.attrs[i] + (noise * err).roundToInt(), 1, 20)
}

fun attrText(w: World, p: Player, i: Int): String {
    val s = shownAttr(w, p, i)
    if (isKnown(w, p)) return "$s"
    val e = ceil(scoutErr(w) * 1.5f).toInt()
    return if (e <= 0) "$s" else "${max(1, s - e)}-${min(20, s + e)}"
}

fun shownOvr(w: World, p: Player): Int = if (isKnown(w, p)) p.ovr else shownOvrWith(p, scoutErr(w))

fun shownOvrWith(p: Player, err: Float): Int {
    val a = IntArray(NATTR) { shownAttrWith(p, it, err) }
    return Roles.overall(a, p.pos).roundToInt()
}

/** Same as [shownOvr] for many players at once: the scouting error (which walks the staff lists) is computed a single time. */
fun shownOvrFn(w: World): (Player) -> Int {
    val err = scoutErr(w)
    return { p -> if (isKnown(w, p)) p.ovr else shownOvrWith(p, err) }
}

fun potText(w: World, p: Player): String {
    if (isKnown(w, p)) return "%.1f".format(p.pa / 20f)
    val lo = max(0f, (p.pa - 10) / 20f)
    val hi = min(5f, (p.pa + 10) / 20f)
    return "%.1f-%.1f".format(lo, hi)
}

