package com.nerdfm.game

import java.text.Normalizer
import kotlin.math.abs

/** Contract filter: "expiring" means the deal ends at the coming season rollover (see Game.endSeason). */
enum class ContractFilter(val label: String) { ANY("Any"), EXPIRING("Expiring"), SECURE("Secure"), LISTED("Listed") }

enum class AvailabilityFilter(val label: String) { ANY("Any"), FIT("Fit"), INJURED("Injured"), SUSPENDED("Suspended"), UNAVAILABLE("Unavailable") }

enum class SortKey(val label: String) {
    POSITION("Position"), OVERALL("Overall"), POTENTIAL("Potential"), AGE("Age"), VALUE("Value"),
    WAGE("Wage"), FITNESS("Fitness"), MORALE("Morale"), NAME("Name")
}

data class PlayerSort(val key: SortKey = SortKey.POSITION, val descending: Boolean = key != SortKey.POSITION && key != SortKey.NAME && key != SortKey.AGE) {
    /** Tapping the same column again flips direction; a new column starts with its natural direction. */
    fun toggled(next: SortKey): PlayerSort =
        if (next == key) copy(descending = !descending) else PlayerSort(next)
}

/** Every criterion is optional and criteria combine with AND. */
data class PlayerFilter(
    val text: String = "",
    val positions: Set<Pos> = emptySet(),
    val minFitness: Int = 0,
    val minMorale: Int = 0,
    val contract: ContractFilter = ContractFilter.ANY,
    val availability: AvailabilityFilter = AvailabilityFilter.ANY,
    val minAge: Int = 0,
    val maxAge: Int = 99
) {
    val isDefault: Boolean get() = this == PlayerFilter()

    /** Human-readable description of what is active, used by the empty-state message. */
    fun describe(): String {
        val parts = ArrayList<String>()
        if (text.isNotBlank()) parts += "name \"${text.trim()}\""
        if (positions.isNotEmpty()) parts += "position " + positions.sortedBy { it.ordinal }.joinToString("/") { it.label }
        if (minFitness > 0) parts += "fitness \u2265 $minFitness%"
        if (minMorale > 0) parts += "morale \u2265 $minMorale"
        if (contract != ContractFilter.ANY) parts += "contract ${contract.label.lowercase()}"
        if (availability != AvailabilityFilter.ANY) parts += "availability ${availability.label.lowercase()}"
        if (minAge > 0 || maxAge < 99) parts += "age $minAge\u2013$maxAge"
        return parts.joinToString(", ")
    }
}

class QueryResult(val players: List<Player>, val totalBeforeFilter: Int, val filter: PlayerFilter, val emptyMessage: String?)

object PlayerQuery {
    /** Lower-cases and strips accents so "Ozil" finds "Özil"; collapses runs of whitespace. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(c)
        return sb.toString().lowercase().trim().replace(Regex("\\s+"), " ")
    }

    fun matchesName(player: Player, query: String): Boolean {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val name = normalize(player.name)
        return tokens.all { name.contains(it) }
    }

    fun matches(world: World, p: Player, f: PlayerFilter): Boolean {
        if (!matchesName(p, f.text)) return false
        if (f.positions.isNotEmpty() && p.pos !in f.positions && (p.pos2 == null || p.pos2 !in f.positions)) return false
        if (p.condition < f.minFitness) return false
        if (p.morale < f.minMorale) return false
        if (p.age < f.minAge || p.age > f.maxAge) return false
        val expiring = p.contractEnd <= world.year + 1
        when (f.contract) {
            ContractFilter.ANY -> {}
            ContractFilter.EXPIRING -> if (!expiring) return false
            ContractFilter.SECURE -> if (expiring) return false
            ContractFilter.LISTED -> if (!p.listed) return false
        }
        when (f.availability) {
            AvailabilityFilter.ANY -> {}
            AvailabilityFilter.FIT -> if (p.injWeeks > 0 || p.suspWeeks > 0) return false
            AvailabilityFilter.INJURED -> if (p.injWeeks <= 0) return false
            AvailabilityFilter.SUSPENDED -> if (p.suspWeeks <= 0) return false
            AvailabilityFilter.UNAVAILABLE -> if (p.injWeeks <= 0 && p.suspWeeks <= 0) return false
        }
        return true
    }

    /**
     * Filters and sorts [source]. [overallOf] and [potentialOf] let the UI sort by what the manager can actually see
     * (scouting fog) rather than the hidden true values. Ties always break by player id, so the order is stable and
     * identical on every run.
     */
    fun run(
        world: World,
        source: List<Player>,
        filter: PlayerFilter = PlayerFilter(),
        sort: PlayerSort = PlayerSort(),
        overallOf: (Player) -> Int = { it.ovr },
        potentialOf: (Player) -> Int = { it.pa }
    ): QueryResult {
        val kept = source.filter { matches(world, it, filter) }
        val comparator = comparatorFor(sort, overallOf, potentialOf)
        val ordered = kept.sortedWith(comparator.thenBy { it.id })
        return QueryResult(ordered, source.size, filter, emptyMessageFor(source.size, ordered.size, filter))
    }

    /** Sorting calls a key function O(n log n) times; with thousands of players the expensive ones are worked out once each. */
    private fun <T> memo(f: (Player) -> T): (Player) -> T {
        val cache = HashMap<Int, T>()
        return { p -> cache.getOrPut(p.id) { f(p) } }
    }

    private fun comparatorFor(sort: PlayerSort, rawOverall: (Player) -> Int, rawPotential: (Player) -> Int): Comparator<Player> {
        val overallOf = memo(rawOverall)
        val potentialOf = memo(rawPotential)
        val valueOf = memo { it.value() }
        val nameOf = memo { normalize(it.name) }
        val base: Comparator<Player> = when (sort.key) {
            SortKey.POSITION -> compareBy<Player> { it.pos.ordinal }.thenByDescending { overallOf(it) }
            SortKey.OVERALL -> compareBy<Player> { overallOf(it) }
            SortKey.POTENTIAL -> compareBy<Player> { potentialOf(it) }
            SortKey.AGE -> compareBy<Player> { it.age }
            SortKey.VALUE -> compareBy<Player> { valueOf(it) }
            SortKey.WAGE -> compareBy<Player> { it.wage }
            SortKey.FITNESS -> compareBy<Player> { it.condition }
            SortKey.MORALE -> compareBy<Player> { it.morale }
            SortKey.NAME -> compareBy<Player> { nameOf(it) }
        }
        // POSITION already embeds its own secondary order; flipping applies to the whole comparator.
        return if (sort.descending) base.reversed() else base
    }

    fun emptyMessageFor(total: Int, shown: Int, filter: PlayerFilter): String? {
        if (shown > 0) return null
        if (total == 0) return "There are no players to show here yet."
        val what = filter.describe()
        return if (what.isEmpty()) "No players found." else "No players match $what. Clear a filter or change the search."
    }
}

/** One line of a side-by-side comparison. [winner] is -1 (A), 0 (level / not scored) or 1 (B). */
class ComparisonRow(val label: String, val a: String, val b: String, val winner: Int)

class Comparison(val rows: List<ComparisonRow>, val winsA: Int, val winsB: Int) {
    fun verdict(nameA: String, nameB: String): String = when {
        winsA > winsB -> "$nameA leads on $winsA of ${winsA + winsB} scored measures"
        winsB > winsA -> "$nameB leads on $winsB of ${winsA + winsB} scored measures"
        else -> "$nameA and $nameB are level"
    }
}

object PlayerComparison {
    /**
     * Compares two players on the same measures, then on every attribute. [attrOf] lets the UI pass the fogged value
     * for unscouted players. Age is shown but not scored; wage is scored as lower-is-better.
     */
    fun compare(
        world: World,
        a: Player,
        b: Player,
        attrOf: (Player, Int) -> Int = { p, i -> p.attrs[i] },
        overallOf: (Player) -> Int = { it.ovr }
    ): Comparison {
        val rows = ArrayList<ComparisonRow>()
        var winsA = 0
        var winsB = 0
        fun add(label: String, va: Double, vb: Double, fa: String, fb: String, higherBetter: Boolean = true, scored: Boolean = true) {
            val w = if (!scored || abs(va - vb) < 1e-9) 0 else if ((va > vb) == higherBetter) -1 else 1
            if (w < 0) winsA++ else if (w > 0) winsB++
            rows += ComparisonRow(label, fa, fb, w)
        }
        fun addInt(label: String, va: Int, vb: Int, higherBetter: Boolean = true, scored: Boolean = true) =
            add(label, va.toDouble(), vb.toDouble(), va.toString(), vb.toString(), higherBetter, scored)

        addInt("Overall", overallOf(a), overallOf(b))
        add("Potential", a.pa / 20.0, b.pa / 20.0, "%.1f".format(a.pa / 20.0), "%.1f".format(b.pa / 20.0))
        addInt("Age", a.age, b.age, scored = false)
        add("Value", a.value().toDouble(), b.value().toDouble(), money(a.value()), money(b.value()))
        add("Wage", a.wage.toDouble(), b.wage.toDouble(), money(a.wage.toLong()), money(b.wage.toLong()), higherBetter = false)
        addInt("Fitness", a.condition.toInt(), b.condition.toInt())
        addInt("Morale", a.morale.toInt(), b.morale.toInt())
        addInt("Form", a.form.toInt(), b.form.toInt())
        addInt("Appearances", a.apps, b.apps, scored = false)
        addInt("Goals", a.goals, b.goals, scored = false)
        addInt("Assists", a.assists, b.assists, scored = false)
        add("Avg rating", a.avgRating().toDouble(), b.avgRating().toDouble(), if (a.apps == 0) "-" else "%.2f".format(a.avgRating()),
            if (b.apps == 0) "-" else "%.2f".format(b.avgRating()), scored = a.apps > 0 && b.apps > 0)
        val contractA = a.contractEnd
        val contractB = b.contractEnd
        addInt("Contract ends", contractA, contractB, scored = false)
        for (i in 0 until NATTR) addInt(ATTRS[i].label, attrOf(a, i), attrOf(b, i))
        return Comparison(rows, winsA, winsB)
    }
}
