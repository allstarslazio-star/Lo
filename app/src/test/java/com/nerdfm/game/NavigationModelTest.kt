package com.nerdfm.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationModelTest {
    private fun done(r: NavigationModel.Result) = assertEquals(NavigationModel.Result.Done, r)

    @Test fun startsOnHomeAndIgnoresUnknownOrRepeatedDestinations() {
        val nav = NavigationModel()
        assertEquals("Home", nav.current)
        assertEquals(NavigationModel.Result.NoOp, nav.navigate("Nowhere"))
        assertEquals(NavigationModel.Result.NoOp, nav.navigate("Home"))
        assertFalse(nav.canGoBack)
        assertEquals(NavigationModel.Result.NoOp, nav.back())
        assertEquals("Home", NavigationModel("garbage").current)
    }

    @Test fun backWalksTheHistoryInReverse() {
        val nav = NavigationModel()
        done(nav.navigate("Squad")); done(nav.navigate("Tactics")); done(nav.navigate("Transfers"))
        assertEquals(listOf("Home", "Squad", "Tactics"), nav.backStack)
        done(nav.back()); assertEquals("Tactics", nav.current)
        done(nav.back()); assertEquals("Squad", nav.current)
        done(nav.back()); assertEquals("Home", nav.current)
        assertEquals(NavigationModel.Result.NoOp, nav.back())
    }

    @Test fun revisitingAScreenCollapsesTheLoopInsteadOfDuplicatingIt() {
        val nav = NavigationModel()
        done(nav.navigate("Squad")); done(nav.navigate("Tactics")); done(nav.navigate("Squad"))
        assertEquals("Squad", nav.current)
        assertEquals(listOf("Home"), nav.backStack)
        done(nav.back())
        assertEquals("Home", nav.current)
    }

    @Test fun unsavedChangesBlockNavigationAndBackUntilConfirmed() {
        val nav = NavigationModel()
        done(nav.navigate("Finance"))
        nav.markDirty("Finance", "wage cap not set")
        val blocked = nav.navigate("Squad")
        assertTrue(blocked is NavigationModel.Result.Blocked)
        assertEquals("wage cap not set", (blocked as NavigationModel.Result.Blocked).reason)
        assertEquals("nothing may change while blocked", "Finance", nav.current)
        assertTrue(nav.back() is NavigationModel.Result.Blocked)
        assertEquals("Finance", nav.current)
        assertEquals("Home", nav.pendingTarget)
        nav.cancelPending()
        assertNull(nav.pendingTarget)
        assertTrue(nav.navigate("Squad") is NavigationModel.Result.Blocked)
        done(nav.discardAndContinue())
        assertEquals("Squad", nav.current)
        assertFalse(nav.isDirty("Finance"))
    }

    @Test fun discardingABlockedBackPressGoesBack() {
        val nav = NavigationModel()
        done(nav.navigate("Finance"))
        nav.markDirty("Finance")
        assertTrue(nav.back() is NavigationModel.Result.Blocked)
        done(nav.discardAndContinue())
        assertEquals("Home", nav.current)
    }

    @Test fun savingTheChangesUnblocksNavigation() {
        val nav = NavigationModel()
        done(nav.navigate("Finance"))
        nav.markDirty("Finance")
        nav.markClean("Finance")
        done(nav.navigate("Club"))
        assertEquals("Club", nav.current)
    }

    @Test fun discardWithNothingPendingDoesNothing() {
        assertEquals(NavigationModel.Result.NoOp, NavigationModel().discardAndContinue())
    }

    @Test fun stateSurvivesScreenRecreationThroughEncodeAndRestore() {
        val nav = NavigationModel()
        done(nav.navigate("Squad")); done(nav.navigate("Players"))
        val again = NavigationModel()
        again.restore(nav.encode())
        assertEquals("Players", again.current)
        assertEquals(nav.backStack, again.backStack)
    }

    @Test fun restoreNeverProducesAnInvalidState() {
        for (junk in listOf(null, "", "   ", "|", "???", "Nope|Squad", "Squad|Nope,,Squad,Home,Home")) {
            val nav = NavigationModel()
            nav.restore(junk)
            assertTrue("current must be a real destination for '$junk'", Destination.isKnown(nav.current))
            assertTrue(nav.backStack.all { Destination.isKnown(it) && it != nav.current })
        }
        val nav = NavigationModel()
        nav.restore("Squad|Nope,,Squad,Home,Home")
        assertEquals("Squad", nav.current)
        assertEquals(listOf("Home"), nav.backStack.filter { it == "Home" }.take(1))
    }

    @Test fun resetClearsHistoryAndGuards() {
        val nav = NavigationModel()
        done(nav.navigate("Squad")); nav.markDirty("Squad")
        nav.reset()
        assertEquals("Home", nav.current)
        assertFalse(nav.canGoBack)
        assertFalse(nav.isDirty("Squad"))
        nav.reset("Club")
        assertEquals("Club", nav.current)
    }

    @Test fun aLongRandomWalkNeverCorruptsTheStack() {
        val nav = NavigationModel()
        val rnd = Random(99)
        val names = Destination.ALL.map { it.screen } + listOf("Bogus")
        repeat(2_000) {
            when (rnd.nextInt(4)) {
                0 -> nav.back()
                1 -> {
                    nav.markDirty(nav.current)
                    if (nav.navigate(names[rnd.nextInt(names.size)]) is NavigationModel.Result.Blocked) nav.discardAndContinue() else nav.markClean(nav.current)
                }
                else -> nav.navigate(names[rnd.nextInt(names.size)])
            }
            assertTrue(Destination.isKnown(nav.current))
            val stack = nav.backStack
            assertTrue(stack.size <= NavigationModel.MAX_STACK)
            assertEquals("no duplicates", stack.size, stack.toSet().size)
            assertFalse("the current screen is never in its own history", nav.current in stack)
        }
    }

    @Test fun destinationsMatchThePlannedPrimarySections() {
        assertEquals(listOf("Home", "Squad", "Tactics", "Competitions", "Transfers", "Club"), Destination.PRIMARY.map { it.label })
        assertEquals("League", Destination.LEAGUE.screen)
        assertEquals(Destination.ALL.size, Destination.ALL.map { it.screen }.toSet().size)
        assertEquals(Destination.ALL.size, Destination.PRIMARY.size + Destination.SECONDARY.size)
        // every screen key the Shell can render
        val shellScreens = setOf("Home", "Inbox", "Squad", "Players", "People", "Tactics", "Training", "Staff", "League", "Transfers", "Finance", "Business", "Ecosystem", "Club", "Diagnostics")
        assertEquals(shellScreens, Destination.ALL.map { it.screen }.toSet())
    }

    @Test fun navigatingNeverTouchesTheCareer() {
        val w = WorldGen.generate(13L, "Nav", 0)
        val before = StateFingerprint.of(w)
        val nav = NavigationModel()
        for (d in Destination.ALL) nav.navigate(d.screen)
        repeat(5) { nav.back() }
        assertEquals(before, StateFingerprint.of(w))
    }
}
