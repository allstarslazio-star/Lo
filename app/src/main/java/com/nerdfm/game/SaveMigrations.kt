package com.nerdfm.game

/** Raised when an in-memory save upgrade cannot be completed. The file on disk is never touched by a migration. */
class MigrationFailedException(val fromVersion: Int, message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Ordered, one-format-at-a-time upgrades for Java-serialised saves.
 *
 * Rules (M8):
 *  - Any persisted-model change must add a [Step] here and bump [Persistence.CURRENT_SAVE_VERSION].
 *  - A step mutates only the in-memory [World] it is given. If it throws, the world is discarded and the original
 *    file is left exactly as it was (see Persistence.readFile -> LoadResult.Unreadable).
 *  - A missing step is an error, never a silent jump to the current version.
 */
object SaveMigrations {
    class Step(val from: Int, val to: Int, val description: String, val migrate: (World) -> Unit)

    /** The real upgrade path. Behaviour is identical to the inline migration it replaces. */
    val DEFAULT_STEPS: List<Step> = listOf(
        Step(1, 2, "relationship state (M3)") { world ->
            world.managerReputation = 50f
            for (club in world.clubs) {
                club.teamCohesion = 55f
                club.boardObjective = club.boardObjective.coerceIn(0, 4)
            }
            for (player in world.players.values) {
                player.playingTimePromise = 0
                player.promiseWeeksRemaining = 0
                player.promiseBrokenWeeks = 0
            }
        },
        Step(2, 3, "business layer (created lazily by World.biz())") { },
        Step(3, 4, "club ecosystem holder (created lazily by World.eco())") { }
    )

    @Volatile private var active: List<Step> = DEFAULT_STEPS

    /** Test hook: runs [block] with a different step list and always restores the real one. */
    internal fun <T> withSteps(steps: List<Step>, block: () -> T): T {
        val previous = active
        active = steps
        try { return block() } finally { active = previous }
    }

    /** The steps that would run to take [from] up to [target], or null when the chain is broken. */
    fun pathFrom(from: Int, target: Int = Persistence.CURRENT_SAVE_VERSION): List<Step>? {
        val path = ArrayList<Step>()
        var version = from
        while (version < target) {
            val step = active.firstOrNull { it.from == version } ?: return null
            if (step.to <= version) return null
            path += step
            version = step.to
            if (path.size > MAX_STEPS) return null
        }
        return path
    }

    /** Upgrades [world] in memory to [target]. Throws [MigrationFailedException] and leaves callers' files alone. */
    fun migrate(world: World, target: Int = Persistence.CURRENT_SAVE_VERSION) {
        var guard = 0
        while (world.saveVersion < target) {
            val from = world.saveVersion
            val step = active.firstOrNull { it.from == from }
                ?: throw MigrationFailedException(from, "no migration step exists from save format $from")
            if (step.to <= from) throw MigrationFailedException(from, "step ${step.from}->${step.to} does not advance the format")
            try {
                step.migrate(world)
            } catch (e: Exception) {
                throw MigrationFailedException(
                    from, "step ${step.from}->${step.to} (${step.description}) failed: ${e.message ?: e.javaClass.simpleName}", e
                )
            }
            world.saveVersion = step.to
            if (++guard > MAX_STEPS) throw MigrationFailedException(from, "migration did not converge after $MAX_STEPS steps")
        }
        if (world.saveVersion > target) {
            throw MigrationFailedException(world.saveVersion, "save format ${world.saveVersion} is newer than the supported $target")
        }
    }

    private const val MAX_STEPS = 50
}
