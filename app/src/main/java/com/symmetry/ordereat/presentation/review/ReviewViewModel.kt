package com.symmetry.ordereat.presentation.review

import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.repository.MemberRepository
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.UploadOutcome
import com.symmetry.ordereat.domain.usecase.BuildOrderSummaryUseCase
import com.symmetry.ordereat.domain.usecase.FindMissingMembersUseCase
import com.symmetry.ordereat.domain.usecase.FormatOrderTextUseCase
import com.symmetry.ordereat.domain.usecase.OrderSummary
import com.symmetry.ordereat.domain.usecase.SubmitSessionUseCase
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ReviewState(
    val session: OrderSession? = null,
    val summary: OrderSummary? = null,
    val missingMembers: List<String> = emptyList(),
    val note: String = "",
    val noteLoaded: Boolean = false,
    /** 送出後輸入框清空；這時沒改過就沿用已存的備註，避免重送把備註洗掉 */
    val noteEdited: Boolean = false,
    val submitting: Boolean = false,
    val showMissingConfirm: Boolean = false,
    val showDeleteDialog: Boolean = false,
    /** 送出結果訊息（成功或已排入補送） */
    val resultMessage: String? = null,
    val summaryText: String = "",
)

sealed interface ReviewIntent {
    data class ChangeNote(val note: String) : ReviewIntent
    data object Submit : ReviewIntent
    data object ConfirmSubmitWithMissing : ReviewIntent
    data object DismissMissingConfirm : ReviewIntent
    data object CopyText : ReviewIntent

    /** 開單中：同步跟單，看插件點了什麼 */
    data object RefreshJoin : ReviewIntent
    data object RequestDelete : ReviewIntent
    data object DismissDelete : ReviewIntent
    data object ConfirmDelete : ReviewIntent
}

sealed interface ReviewEffect {
    data class CopyToClipboard(val text: String) : ReviewEffect
    data class ShowMessage(val message: String) : ReviewEffect
    data object ClearFocus : ReviewEffect
    data object Deleted : ReviewEffect
}

@HiltViewModel(assistedFactory = ReviewViewModel.Factory::class)
class ReviewViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val sessionRepository: SessionRepository,
    private val submitSession: SubmitSessionUseCase,
    private val buildSummary: BuildOrderSummaryUseCase,
    private val formatText: FormatOrderTextUseCase,
    private val findMissingMembers: FindMissingMembersUseCase,
    memberRepository: MemberRepository,
) : MviViewModel<ReviewState, ReviewIntent, ReviewEffect>(ReviewState()) {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): ReviewViewModel
    }

    init {
        viewModelScope.launch {
            combine(sessionRepository.observeSession(sessionId), memberRepository.observeMembers()) { session, members ->
                session to members
            }.collect { (session, members) ->
                if (session == null) return@collect
                val summary = buildSummary(session.lines)
                setState {
                    copy(
                        session = session,
                        summary = summary,
                        missingMembers = findMissingMembers(members, session.lines).map { it.name },
                        // 備註只在第一次載入時帶入，避免打字時被資料庫回寫覆蓋游標
                        note = if (noteLoaded) note else session.note,
                        noteLoaded = true,
                        summaryText = formatText(session.copy(note = if (noteEdited) note else session.note), summary),
                    )
                }
            }
        }
    }

    override fun handleIntent(intent: ReviewIntent) {
        when (intent) {
            is ReviewIntent.ChangeNote -> {
                setState {
                    copy(
                        note = intent.note,
                        noteEdited = true,
                        summaryText = session?.let { s -> summary?.let { formatText(s.copy(note = intent.note), it) } }.orEmpty(),
                    )
                }
                viewModelScope.launch { sessionRepository.updateNote(sessionId, intent.note) }
            }

            ReviewIntent.Submit -> {
                if (currentState.missingMembers.isNotEmpty()) {
                    setState { copy(showMissingConfirm = true) }
                } else {
                    submit()
                }
            }

            ReviewIntent.ConfirmSubmitWithMissing -> {
                setState { copy(showMissingConfirm = false) }
                submit()
            }

            ReviewIntent.DismissMissingConfirm -> setState { copy(showMissingConfirm = false) }
            ReviewIntent.CopyText -> sendEffect(ReviewEffect.CopyToClipboard(currentState.summaryText))
            ReviewIntent.RefreshJoin -> refreshJoin()
            ReviewIntent.RequestDelete -> setState { copy(showDeleteDialog = true) }
            ReviewIntent.DismissDelete -> setState { copy(showDeleteDialog = false) }
            ReviewIntent.ConfirmDelete -> viewModelScope.launch {
                setState { copy(showDeleteDialog = false, submitting = true) }
                sessionRepository.deleteSession(sessionId)
                    .onSuccess { sendEffect(ReviewEffect.Deleted) }
                    .onFailure {
                        setState { copy(submitting = false) }
                        sendEffect(ReviewEffect.ShowMessage("刪除失敗：${it.message}"))
                    }
            }
        }
    }

    private fun submit() {
        if (currentState.submitting) return
        setState { copy(submitting = true, resultMessage = null) }
        viewModelScope.launch {
            if (currentState.noteEdited) sessionRepository.updateNote(sessionId, currentState.note)
            // 開單中按送出＝結單：插件不能再加，並帶回所有人的明細
            val closing = currentState.session?.joinOpen == true
            val outcome = if (closing) sessionRepository.closeJoin(sessionId) else submitSession(sessionId)
            val message = when (outcome) {
                UploadOutcome.Uploaded -> if (closing) "已結單並送出到 Google Sheet" else "已送出到 Google Sheet"
                is UploadOutcome.Queued -> "目前無法上傳（${outcome.reason}），已存在手機，有網路會自動補送"
                is UploadOutcome.Failed -> "送出失敗：${outcome.reason}"
            }
            setState {
                if (outcome is UploadOutcome.Failed) {
                    copy(submitting = false, resultMessage = message)
                } else {
                    // 備註已存進這團並送出，輸入框清空給下次用
                    copy(submitting = false, resultMessage = message, note = "", noteEdited = false)
                }
            }
            sendEffect(ReviewEffect.ClearFocus)
            sendEffect(ReviewEffect.ShowMessage(message))
        }
    }

    private fun refreshJoin() {
        val session = currentState.session ?: return
        if (currentState.submitting || !session.joinOpen) return
        setState { copy(submitting = true) }
        viewModelScope.launch {
            if (currentState.noteEdited) sessionRepository.updateNote(sessionId, currentState.note)
            val message = when (val outcome = sessionRepository.openForJoin(sessionId, session.openedBy)) {
                UploadOutcome.Uploaded -> "已同步跟單"
                is UploadOutcome.Queued -> "目前連不到 Google（${outcome.reason}），有網路會自動補送"
                is UploadOutcome.Failed -> "同步失敗：${outcome.reason}"
            }
            setState { copy(submitting = false) }
            sendEffect(ReviewEffect.ShowMessage(message))
        }
    }
}
