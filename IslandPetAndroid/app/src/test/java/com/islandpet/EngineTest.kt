package com.islandpet

import com.islandpet.model.GameKind
import com.islandpet.model.GameState
import com.islandpet.model.Mood
import com.islandpet.model.Species
import com.islandpet.model.adopt
import com.islandpet.model.buy
import com.islandpet.model.checkAchievements
import com.islandpet.model.clean
import com.islandpet.model.feed
import com.islandpet.model.finishGame
import com.islandpet.model.heal
import com.islandpet.model.pat
import com.islandpet.model.play
import com.islandpet.model.quickFeed
import com.islandpet.model.tick
import com.islandpet.model.toggleSleep
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val hour = 3_600_000L

    private fun hatched(species: Species = Species.CAT): Pair<GameState, Long> {
        val s = GameState()
        val t0 = 1_700_000_000_000L
        s.adopt("咪咪", species, t0)
        repeat(6) { s.pat(t0) }
        assertFalse("6 次敲蛋后应孵化", s.pet!!.isEgg)
        return s to t0
    }

    @Test
    fun eggHatchesAfterThreeMinutes() {
        val s = GameState()
        s.adopt("蛋", Species.DOG, 0)
        s.tick(60_000)
        assertTrue(s.pet!!.isEgg)
        s.tick(181_000)
        assertFalse(s.pet!!.isEgg)
    }

    @Test
    fun statsDecayOverTimeAndStayInRange() {
        val (s, t0) = hatched()
        s.tick(t0 + 5 * hour)
        val p = s.pet!!
        assertTrue(p.hunger < 80)
        listOf(p.hunger, p.happiness, p.energy, p.cleanliness, p.health).forEach { assertTrue(it in 0.0..100.0) }
        // 3 天不管：饿坏、生病，但数值不越界
        s.tick(t0 + 80 * hour)
        assertTrue(s.pet!!.hunger == 0.0)
        assertTrue(s.pet!!.isSick)
        assertEquals(Mood.SICK, s.pet!!.mood(t0 + 80 * hour))
    }

    @Test
    fun interactionsWork() {
        val (s, t0) = hatched()
        s.tick(t0 + 4 * hour)
        val before = s.pet!!.hunger
        s.feed("fish")
        assertTrue(s.pet!!.hunger > before)
        assertEquals(1, s.inventory["fish"])
        s.quickFeed()
        s.play()
        s.clean()
        s.inventory["medicine"] = 1
        s.pet!!.isSick = true
        s.heal()
        assertFalse(s.pet!!.isSick)
        s.pet!!.energy = 30.0
        s.toggleSleep()
        assertTrue(s.pet!!.isSleeping)
        assertTrue(s.buy("bow").startsWith("购买了"))
        s.finishGame(GameKind.CATCH, 45, 27)
        val unlocked = s.checkAchievements().map { it.name }
        assertTrue("FIRST_FEED" in unlocked && "CATCH_PRO" in unlocked && "HATCH" in unlocked)
    }

    @Test
    fun saveRoundTrip() {
        val (s, _) = hatched(Species.SLIME)
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(GameState.serializer(), s)
        val back = json.decodeFromString(GameState.serializer(), text)
        assertEquals(s, back)
    }
}
