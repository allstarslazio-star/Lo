package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainMenu() {
    val ctx = LocalContext.current
    val scope = App.scope
    var hasSave by remember { mutableStateOf(Persistence.exists(ctx.filesDir)) }
    var loadMessage by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("NERD FM", 44, C.accent, true)
            T("A deep, brutal, fully offline football management sim", 15, C.muted, lines = 2)
            Spacer(Modifier.height(16.dp))
            T("7 divisions  |  126 clubs  |  5,000+ procedurally generated players", 13, C.text)
            T("Tick-based match engine  |  Hidden potential  |  Scouting fog  |  Board pressure", 13, C.text)
        }
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("New Career", modifier = Modifier.fillMaxWidth()) { App.mode = Mode.NEWGAME }
            Btn("Continue Career", enabled = hasSave, modifier = Modifier.fillMaxWidth()) {
                scope.launch {
                    val result = heavy("Loading career...") { Persistence.load(ctx.filesDir) }
                    when (result) {
                        is LoadResult.Loaded -> {
                            App.world = result.world
                            App.screen = "Inbox"
                            App.mode = Mode.GAME
                        }
                        LoadResult.NoSave -> {
                            hasSave = false
                            loadMessage = "No saved career was found. You can start a new career."
                        }
                        is LoadResult.Incompatible -> {
                            hasSave = Persistence.exists(ctx.filesDir)
                            val backup = result.backupFileName?.let { " Preserved as $it." }.orEmpty()
                            loadMessage = "This career save is incompatible with this version of Nerd FM. It was not loaded.$backup ${result.reason}"
                        }
                        is LoadResult.Corrupt -> {
                            hasSave = Persistence.exists(ctx.filesDir)
                            val backup = result.backupFileName?.let { " Preserved as $it." }.orEmpty()
                            loadMessage = "This career save is damaged and could not be loaded.$backup ${result.reason}"
                        }
                    }
                }
            }
        }
    }
    if (loadMessage != null) {
        AlertDialog(
            onDismissRequest = { loadMessage = null },
            title = { Text("Career save") },
            text = { Text(loadMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { loadMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
fun NewGameScreen() {
    val ctx = LocalContext.current
    val scope = App.scope
    var name by remember { mutableStateOf("Alex Morgan") }
    var tier by remember { mutableIntStateOf(2) }
    var preview by remember { mutableStateOf<World?>(null) }
    var seed by remember { mutableStateOf(System.currentTimeMillis() % 100000) }
    var picked by remember { mutableIntStateOf(-1) }

    LaunchedEffect(seed) {
        preview = null
        preview = withContext(Dispatchers.Default) { WorldGen.generate(seed, "x", 0) }
    }
    val pw = preview
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("< Back") { App.mode = Mode.MENU }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                label = { Text("Manager name") }, modifier = Modifier.width(240.dp)
            )
            Btn("Reroll world") { seed += 1; picked = -1 }
            Spacer(Modifier.weight(1f))
            Btn("Start career", enabled = pw != null && picked >= 0) {
                scope.launch {
                    val w = heavy("Building the football world...") {
                        val g = WorldGen.generate(seed, name.ifBlank { "Manager" }, picked)
                        g.addNews("Welcome", "The board have appointed you at ${g.human().name}. Expect a tough job.")
                        g
                    }
                    App.world = w
                    App.screen = "Inbox"
                    App.mode = Mode.GAME
                    withContext(Dispatchers.IO) { Persistence.save(ctx.filesDir, w) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (pw == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(7) { i ->
                val sel = i == tier
                Box(
                    Modifier.clip(RoundedCornerShape(2.dp)).background(if (sel) C.accent else C.card)
                        .clickable { tier = i; picked = -1 }.padding(horizontal = 10.dp, vertical = 6.dp)
                ) { T("${i + 1}. ${pw.leagues[i].name}", 12, if (sel) androidx.compose.ui.graphics.Color.Black else C.text, sel) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.weight(1.4f).fillMaxHeight()) {
                Row {
                    Cell("Club", 160, C.muted, true); Cell("Rep", 66, C.muted); Cell("Squad", 66, C.muted)
                    Cell("Stadium", 58, C.muted); Cell("Cash", 66, C.muted); Cell("Wage bud.", 82, C.muted)
                }
                val ids = pw.leagues[tier].clubIds.sortedByDescending { pw.clubs[it].rep }
                LazyColumn {
                    items(ids, key = { it }) { id ->
                        val c = pw.clubs[id]
                        val sel = picked == id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp))
                                .background(if (sel) C.accent.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { picked = id }.padding(vertical = 4.dp)
                        ) {
                            Cell(c.name, 160, C.text, sel)
                            Box(Modifier.width(66.dp)) { Stars(repStars(c.rep), 11) }
                            Box(Modifier.width(66.dp)) { Stars(strengthStars(Lineups.strength(pw, c)), 11) }
                            Cell("${c.capacity()}", 58); Cell(money(c.balance), 66); Cell(money(c.wageBudget) + "/w", 82)
                        }
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                if (picked < 0) {
                    T("Pick a club.", 14, C.muted)
                    T("Every league is playable. Lower leagues are poorer, thinner and nastier - but promotion pays.", 12, C.muted, lines = 4)
                } else {
                    val c = pw.clubs[picked]
                    val rank = Game.leagueRank(pw, c) + 1
                    T(c.name, 18, C.accent, true)
                    T(pw.leagues[c.tier].name, 13, C.muted)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Reputation ", 13); Stars(repStars(c.rep), 13)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Squad rating ", 13); Stars(strengthStars(Lineups.strength(pw, c)), 13)
                    }
                    T("Board expects: " + expectation(rank, 18), 13, lines = 2)
                    T("Revenue: ${money(c.revenue)} / yr", 13)
                    T("Transfer budget: ${money(c.transferBudget)}", 13)
                    T("Wage budget: ${money(c.wageBudget)} / wk", 13)
                    T("Facilities ${c.facilities}/20  Youth ${c.youth}/20  Scouting ${c.scout}/20", 12, C.muted, lines = 2)
                    Spacer(Modifier.height(6.dp))
                    T("Top players:", 12, C.muted)
                    for (p in pw.squad(c).sortedByDescending { it.ovr }.take(5)) {
                        Row { Cell(p.pos.label, 36, C.muted); Cell(p.name, 150); Cell("${p.ovr}", 30, ovrColor(p.ovr), true) }
                    }
                }
            }
        }
    }
}

fun expectation(rank: Int, n: Int): String = when {
    rank <= 2 -> "win the league (top 2)"
    rank <= 5 -> "finish in the top 5 (promotion hunt)"
    rank <= 10 -> "finish in the top half"
    rank <= 15 -> "stay clear of the bottom 3"
    else -> "avoid relegation - survival is success"
}
