package com.symmetry.ordereat.domain

import com.symmetry.ordereat.domain.usecase.PickRandomRestaurantUseCase
import com.symmetry.ordereat.domain.usecase.PickResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class PickRandomRestaurantUseCaseTest {

    private val pick = PickRandomRestaurantUseCase()
    private val today = LocalDate.of(2026, 10, 7)
    private val restaurants = listOf(
        restaurant("a", "飯"),
        restaurant("b", "飯", "麵"),
        restaurant("c", "飲料"),
    )

    @Test
    fun `category filter keeps only matching restaurants`() {
        repeat(20) { seed ->
            val result = pick(restaurants, emptyList(), setOf("飲料"), null, today, random = Random(seed))
            assertEquals(PickResult.Picked(restaurants[2]), result)
        }
    }

    @Test
    fun `unknown category returns NoMatch`() {
        assertEquals(PickResult.NoMatch, pick(restaurants, emptyList(), setOf("火鍋"), null, today))
    }

    @Test
    fun `exclude covers today and previous N days but not N+1`() {
        val sessions = listOf(
            session("a", today), // 今天
            session("b", today.minusDays(3)), // 第 3 天前：排除
            session("c", today.minusDays(4)), // 第 4 天前：不排除
        )
        val ids = PickRandomRestaurantUseCase.recentRestaurantIds(sessions, today, excludeDays = 3)
        assertEquals(setOf("a", "b"), ids)

        repeat(20) { seed ->
            val result = pick(restaurants, sessions, emptySet(), 3, today, random = Random(seed))
            assertEquals(PickResult.Picked(restaurants[2]), result)
        }
    }

    @Test
    fun `all excluded returns AllExcluded, ignoring exclusion picks again`() {
        val sessions = restaurants.map { session(it.id, today.minusDays(1)) }
        assertEquals(PickResult.AllExcluded, pick(restaurants, sessions, emptySet(), 3, today))
        val ignored = pick(restaurants, sessions, emptySet(), null, today)
        assert(ignored is PickResult.Picked)
    }

    @Test
    fun `reroll avoids previous pick when other candidates exist`() {
        repeat(50) { seed ->
            val result = pick(restaurants, emptyList(), emptySet(), null, today, previousId = "a", random = Random(seed))
            assertNotEquals(PickResult.Picked(restaurants[0]), result)
        }
    }

    @Test
    fun `reroll with single candidate returns the same restaurant`() {
        val result = pick(restaurants, emptyList(), setOf("飲料"), null, today, previousId = "c")
        assertEquals(PickResult.Picked(restaurants[2]), result)
    }
}
