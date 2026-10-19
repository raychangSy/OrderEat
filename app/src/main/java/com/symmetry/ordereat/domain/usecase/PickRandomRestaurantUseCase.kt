package com.symmetry.ordereat.domain.usecase

import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

sealed interface PickResult {
    data class Picked(val restaurant: Restaurant) : PickResult

    /** 分類篩選後沒有任何店 */
    data object NoMatch : PickResult

    /** 有符合分類的店，但全部最近都吃過 */
    data object AllExcluded : PickResult
}

class PickRandomRestaurantUseCase @Inject constructor() {

    /**
     * @param categories 空集合 = 不篩選
     * @param excludeDays null = 不排除；N = 排除「今天與前 N 天」吃過的店
     * @param previousId 重抽時盡量不要抽到同一間
     */
    operator fun invoke(
        restaurants: List<Restaurant>,
        sessions: List<OrderSession>,
        categories: Set<String>,
        excludeDays: Int?,
        today: LocalDate,
        previousId: String? = null,
        random: Random = Random.Default,
    ): PickResult {
        val matched = if (categories.isEmpty()) {
            restaurants
        } else {
            restaurants.filter { r -> r.categories.any { it in categories } }
        }
        if (matched.isEmpty()) return PickResult.NoMatch

        val candidates = if (excludeDays == null) {
            matched
        } else {
            val recent = recentRestaurantIds(sessions, today, excludeDays)
            matched.filterNot { it.id in recent }
        }
        if (candidates.isEmpty()) return PickResult.AllExcluded

        val pool = candidates.filter { it.id != previousId }.ifEmpty { candidates }
        return PickResult.Picked(pool.random(random))
    }

    companion object {
        fun recentRestaurantIds(sessions: List<OrderSession>, today: LocalDate, excludeDays: Int): Set<String> {
            val since = today.minusDays(excludeDays.toLong())
            return sessions
                .filter { !it.date.isBefore(since) && !it.date.isAfter(today) }
                .mapNotNull { it.restaurantId }
                .toSet()
        }
    }
}
