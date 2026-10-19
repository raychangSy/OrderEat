package com.symmetry.ordereat.domain.usecase

import com.symmetry.ordereat.domain.model.MenuItem
import com.symmetry.ordereat.domain.model.OptionGroup
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.SyncRepository
import com.symmetry.ordereat.domain.repository.UploadOutcome
import java.util.UUID
import javax.inject.Inject

class CreateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(mealType: String, restaurant: Restaurant): String =
        sessionRepository.createSession(clock.today(), mealType, restaurant)
}

class SubmitSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String): UploadOutcome = sessionRepository.submit(sessionId)
}

class SyncMasterDataUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(): Result<Unit> = syncRepository.syncMasterData()
}

/** 點餐人：成員或訪客（memberId = null） */
data class Orderer(val memberId: String?, val name: String)

sealed interface SaveLineResult {
    data object Saved : SaveLineResult
    data class Invalid(val errors: Map<String, String>) : SaveLineResult
}

class SaveMenuItemLineUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: AppClock,
) {
    /**
     * @param selected groupId → 已選的選項名稱
     * @param existing 編輯既有明細時傳入
     */
    suspend operator fun invoke(
        sessionId: String,
        orderer: Orderer,
        item: MenuItem,
        groups: List<OptionGroup>,
        selected: Map<String, List<String>>,
        quantity: Int,
        note: String,
        existing: OrderLine? = null,
    ): SaveLineResult {
        val errors = groups.mapNotNull { group ->
            OptionRules.error(group, selected[group.id].orEmpty().size)?.let { group.id to it }
        }.toMap()
        if (errors.isNotEmpty()) return SaveLineResult.Invalid(errors)

        // 依群組順序與選項順序排列，輸出文字才穩定
        val selections = groups.flatMap { group ->
            val names = selected[group.id].orEmpty()
            group.choices.filter { it.name in names }.map { SelectedOption(group.id, group.name, it.name, it.price) }
        }
        val line = OrderLine(
            id = existing?.id ?: UUID.randomUUID().toString(),
            sessionId = sessionId,
            memberId = orderer.memberId,
            memberName = orderer.name,
            menuItemId = item.id,
            itemName = item.name,
            selections = selections,
            quantity = quantity.coerceAtLeast(1),
            unitPrice = OptionRules.unitPrice(item.price, selections),
            note = note.trim(),
            createdAt = existing?.createdAt ?: clock.nowMillis(),
        )
        sessionRepository.upsertLine(line)
        return SaveLineResult.Saved
    }
}

class SaveManualLineUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        sessionId: String,
        orderer: Orderer,
        itemName: String,
        price: Int,
        quantity: Int,
        note: String,
    ) {
        sessionRepository.upsertLine(
            OrderLine(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                memberId = orderer.memberId,
                memberName = orderer.name,
                menuItemId = null,
                itemName = itemName.trim(),
                selections = emptyList(),
                quantity = quantity.coerceAtLeast(1),
                unitPrice = price.coerceAtLeast(0),
                note = note.trim(),
                createdAt = clock.nowMillis(),
            ),
        )
    }
}
