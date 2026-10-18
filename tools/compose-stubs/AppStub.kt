package com.nerdfm.game
// Stand-ins for UI-layer symbols that live in files this offline check does not compile (MainActivity.kt, Squad.kt).
object App { var screen = "Inbox"; var busy = ""; var tick = 0; fun bump() { tick++ } }
fun isKnown(w: World, p: Player): Boolean = true
fun shownOvr(w: World, p: Player): Int = p.ovr
