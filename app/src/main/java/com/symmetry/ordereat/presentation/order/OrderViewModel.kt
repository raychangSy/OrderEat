package com.symmetry.ordereat.presentation.order

import androidx.lifecycle.viewModelScope
import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.model.MenuItem
import com.symmetry.ordereat.domain.model.OptionGroup
import com.symmetry.ordereat.domain.model.OrderLine
import com.symmetry.ordereat.domain.model.OrderSession
import com.symmetry.ordereat.domain.model.Restaurant
import com.symmetry.ordereat.domain.model.RestaurantMenu
import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.model.SelectionType
import com.symmetry.ordereat.domain.model.SessionStatus
import com.symmetry.ordereat.domain.repository.AppClock
import com.symmetry.ordereat.domain.repository.MemberRepository
import com.symmetry.ordereat.domain.repository.MenuRepository
import com.symmetry.ordereat.domain.repository.SessionRepository
import com.symmetry.ordereat.domain.repository.SettingsRepository
import com.symmetry.ordereat.domain.repository.SyncRepository
import com.symmetry.ordereat.domain.repository.UploadOutcome
import com.symmetry.ordereat.domain.usecase.OptionRules
import com.symmetry.ordereat.domain.usecase.Orderer
import com.symmetry.ordereat.domain.usecase.SaveLineResult
import com.symmetry.ordereat.domain.usecase.SaveManualLineUseCase
import com.symmetry.ordereat.domain.usecase.SaveMenuItemLineUseCase
import com.symmetry.ordereat.presentation.mvi.MviViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class OrdererCell(
    val orderer: Orderer,
    val lineCount: Int,
    val total: Int,
    val isGuest: Boolean,
)

data class OptionSheetState(
    val item: MenuItem,
    val groups: List<OptionGroup>,
    /** groupId → 已選的選項名稱 */
    val selected: Map<String, List<String>>,
    val quantity: Int = 1,
    val note: String = "",
    val errors: Map<String, String> = emptyMap(),
    val editingLine: OrderLine? = null,
) {
    val unitPrice: Int
        get() = OptionRules.unitPrice(
            item.price,
            groups.flatMap { g ->
                val names = selected[g.id].orEmpty()
                g.choices.filter { it.name in names }.map { SelectedOption(g.id, g.name, it.name, it.price) }
            },
        )
}

data class OrderState(
    val loading: Boolean = true,
    val session: OrderSession? = null,
    val restaurant: Restaurant? = null,
    val menu: RestaurantMenu = RestaurantMenu.Empty,
    val members: List<Member> = emptyList(),
    /** 這次新增、還沒點東西的訪客 */
    val pendingGuests: List<String> = emptyList(),
    val activeOrderer: Orderer? = null,
    val optionSheet: OptionSheetState? = null,
    val showGuestDialog: Boolean = false,
    val showManualItemDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    /** 有設定後端、今天的團才能開單（插件只看今天的團） */
    val canJoin: Boolean = false,
    val openerName: String = "",
    val showOpenJoinDialog: Boolean = false,
    val joinBusy: Boolean = false,
) {
    private val lines: List<OrderLine> get() = session?.lines.orEmpty()

    val total: Int get() = lines.sumOf { it.subtotal }

    val canDelete: Boolean get() = session != null && !session.isRemoteOnly

    /** 插件跟單的明細（上次同步帶回來的） */
    val extensionLines: List<OrderLine> get() = lines.filter { it.fromExtension }

    /** 啟用中的成員 + 已點餐的停用成員 + 訪客 */
    val orderers: List<OrdererCell>
        get() {
            val memberCells = members
                .filter { m -> m.active || lines.any { it.memberId == m.id } }
                .map { m -> cellOf(Orderer(m.id, m.name), isGuest = false) }
            val guestNames = (lines.filter { it.memberId == null }.map { it.memberName } + pendingGuests).distinct()
            return memberCells + guestNames.map { cellOf(Orderer(null, it), isGuest = true) }
        }

    val activeLines: List<OrderLine>
        get() = activeOrderer?.let { o -> lines.filter { it.belongsTo(o) } }.orEmpty()

    private fun cellOf(orderer: Orderer, isGuest: Boolean): OrdererCell {
        val own = lines.filter { it.belongsTo(orderer) }
        return OrdererCell(orderer, own.sumOf { it.quantity }, own.sumOf { it.subtotal }, isGuest)
    }
}

fun OrderLine.belongsTo(orderer: Orderer): Boolean =
    if (orderer.memberId != null) memberId == orderer.memberId else memberId == null && memberName == orderer.name

sealed interface OrderIntent {
    data class SelectOrderer(val orderer: Orderer) : OrderIntent
    data object ClearOrderer : OrderIntent
    data object ShowGuestDialog : OrderIntent
    data object DismissGuestDialog : OrderIntent
    data class AddGuest(val name: String) : OrderIntent
    data class ClickItem(val item: MenuItem) : OrderIntent
    data class ToggleChoice(val groupId: String, val choiceName: String) : OrderIntent
    data class ChangeQuantity(val delta: Int) : OrderIntent
    data class ChangeNote(val note: String) : OrderIntent
    data object ConfirmOptions : OrderIntent
    data object DismissOptions : OrderIntent
    data class EditLine(val line: OrderLine) : OrderIntent
    data class RemoveLine(val line: OrderLine) : OrderIntent
    data object ShowManualItemDialog : OrderIntent
    data object DismissManualItemDialog : OrderIntent
    data class AddManualItem(val name: String, val price: Int, val quantity: Int, val note: String) : OrderIntent
    data object RequestDelete : OrderIntent
    data object DismissDelete : OrderIntent
    data object ConfirmDelete : OrderIntent
    data object RequestOpenJoin : OrderIntent
    data object DismissOpenJoin : OrderIntent
    data class ConfirmOpenJoin(val openerName: String) : OrderIntent

    /** 開單中：送出手機上的單並帶回插件跟單的明細 */
    data object RefreshJoin : OrderIntent
}

sealed interface OrderEffect {
    data class ShowMessage(val message: String) : OrderEffect
    data object Close : OrderEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = OrderViewModel.Factory::class)
class OrderViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val sessionRepository: SessionRepository,
    private val saveMenuItemLine: SaveMenuItemLineUseCase,
    private val saveManualLine: SaveManualLineUseCase,
    private val settingsRepository: SettingsRepository,
    menuRepository: MenuRepository,
    memberRepository: MemberRepository,
    syncRepository: SyncRepository,
    clock: AppClock,
) : MviViewModel<OrderState, OrderIntent, OrderEffect>(OrderState()) {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): OrderViewModel
    }

    init {
        val session = sessionRepository.observeSession(sessionId)
        val restaurantId = session.filterNotNull().map { it.restaurantId }.distinctUntilChanged()
        val restaurant = restaurantId.flatMapLatest { id -> id?.let(menuRepository::observeRestaurant) ?: emptyFlow() }
        val menu = restaurantId.flatMapLatest { id -> id?.let(menuRepository::observeMenu) ?: emptyFlow() }

        viewModelScope.launch {
            session.collect { s ->
                setState {
                    copy(
                        session = s,
                        loading = false,
                        canJoin = s != null && !s.isRemoteOnly && s.date == clock.today() && syncRepository.isBackendConfigured,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(restaurant, menu) { r, m -> r to m }.collect { (r, m) -> setState { copy(restaurant = r, menu = m) } }
        }
        viewModelScope.launch {
            memberRepository.observeMembers().collect { setState { copy(members = it) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { setState { copy(openerName = it.openerName) } }
        }
    }

    override fun handleIntent(intent: OrderIntent) {
        when (intent) {
            is OrderIntent.SelectOrderer -> setState { copy(activeOrderer = intent.orderer) }
            OrderIntent.ClearOrderer -> {
                val orderer = currentState.activeOrderer
                setState { copy(activeOrderer = null) }
                uploadIfJoinOpen(orderer)
            }
            OrderIntent.ShowGuestDialog -> setState { copy(showGuestDialog = true) }
            OrderIntent.DismissGuestDialog -> setState { copy(showGuestDialog = false) }
            is OrderIntent.AddGuest -> addGuest(intent.name)
            is OrderIntent.ClickItem -> openSheet(intent.item, null)
            is OrderIntent.ToggleChoice -> toggleChoice(intent.groupId, intent.choiceName)
            is OrderIntent.ChangeQuantity -> updateSheet { copy(quantity = (quantity + intent.delta).coerceIn(1, 99)) }
            is OrderIntent.ChangeNote -> updateSheet { copy(note = intent.note) }
            OrderIntent.ConfirmOptions -> confirmOptions()
            OrderIntent.DismissOptions -> setState { copy(optionSheet = null) }
            is OrderIntent.EditLine -> if (!intent.line.fromExtension) editLine(intent.line)
            is OrderIntent.RemoveLine -> if (!intent.line.fromExtension) viewModelScope.launch {
                sessionRepository.removeLine(sessionId, intent.line.id)
                sendEffect(OrderEffect.ShowMessage("已刪除 ${intent.line.itemName}"))
            }

            OrderIntent.ShowManualItemDialog -> setState { copy(showManualItemDialog = true) }
            OrderIntent.DismissManualItemDialog -> setState { copy(showManualItemDialog = false) }
            is OrderIntent.AddManualItem -> addManualItem(intent)
            OrderIntent.RequestDelete -> setState { copy(showDeleteDialog = true) }
            OrderIntent.DismissDelete -> setState { copy(showDeleteDialog = false) }
            OrderIntent.ConfirmDelete -> viewModelScope.launch {
                setState { copy(showDeleteDialog = false, joinBusy = true) }
                sessionRepository.deleteSession(sessionId)
                    .onSuccess { sendEffect(OrderEffect.Close) }
                    .onFailure {
                        setState { copy(joinBusy = false) }
                        sendEffect(OrderEffect.ShowMessage("刪除失敗：${it.message}"))
                    }
            }

            OrderIntent.RequestOpenJoin -> setState { copy(showOpenJoinDialog = true) }
            OrderIntent.DismissOpenJoin -> setState { copy(showOpenJoinDialog = false) }
            is OrderIntent.ConfirmOpenJoin -> {
                val name = intent.openerName.trim()
                if (name.isEmpty()) return
                setState { copy(showOpenJoinDialog = false) }
                viewModelScope.launch { settingsRepository.setOpenerName(name) }
                runJoin("已開單，插件可以跟單了") { sessionRepository.openForJoin(sessionId, name) }
            }

            OrderIntent.RefreshJoin -> {
                val openedBy = currentState.session?.openedBy.orEmpty()
                runJoin("已同步跟單") { sessionRepository.openForJoin(sessionId, openedBy) }
            }
        }
    }

    /** 開放跟單中：每個人點完（換人）就上傳，插件那邊馬上看得到誰點了。沒改東西就不送 */
    private fun uploadIfJoinOpen(orderer: Orderer?) {
        val session = currentState.session ?: return
        if (orderer == null || !session.joinOpen || session.status != SessionStatus.ORDERING) return
        runJoin("已上傳 ${orderer.name} 的訂單") { sessionRepository.openForJoin(sessionId, session.openedBy) }
    }

    private fun runJoin(successMessage: String, action: suspend () -> UploadOutcome) {
        if (currentState.joinBusy) return
        setState { copy(joinBusy = true) }
        viewModelScope.launch {
            val message = when (val outcome = action()) {
                UploadOutcome.Uploaded -> successMessage
                is UploadOutcome.Queued -> "目前連不到 Google（${outcome.reason}），有網路會自動補送"
                is UploadOutcome.Failed -> "失敗：${outcome.reason}"
            }
            setState { copy(joinBusy = false) }
            sendEffect(OrderEffect.ShowMessage(message))
        }
    }

    private fun addGuest(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        val exists = currentState.orderers.any { it.orderer.name == name }
        setState {
            copy(
                showGuestDialog = false,
                pendingGuests = if (exists) pendingGuests else pendingGuests + name,
                activeOrderer = orderers.firstOrNull { it.orderer.name == name }?.orderer ?: Orderer(null, name),
            )
        }
    }

    private fun openSheet(item: MenuItem, editing: OrderLine?) {
        val groups = currentState.menu.groupsOf(item)
        val selected = if (editing != null) {
            editing.selections.groupBy({ it.groupId }, { it.choiceName })
        } else {
            // 必選單選先帶第一個選項，少點一下
            groups.filter { it.type == SelectionType.SINGLE && it.required && it.choices.isNotEmpty() }
                .associate { it.id to listOf(it.choices.first().name) }
        }
        setState {
            copy(
                optionSheet = OptionSheetState(
                    item = item,
                    groups = groups,
                    selected = selected,
                    quantity = editing?.quantity ?: 1,
                    note = editing?.note.orEmpty(),
                    editingLine = editing,
                ),
            )
        }
    }

    private fun editLine(line: OrderLine) {
        val item = currentState.menu.items.firstOrNull { it.id == line.menuItemId }
        if (item == null) {
            sendEffect(OrderEffect.ShowMessage("手動輸入的品項請刪除後重新輸入"))
            return
        }
        openSheet(item, line)
    }

    private fun toggleChoice(groupId: String, choiceName: String) {
        val sheet = currentState.optionSheet ?: return
        val group = sheet.groups.firstOrNull { it.id == groupId } ?: return
        val next = OptionRules.toggle(group, sheet.selected[groupId].orEmpty(), choiceName)
        if (next == null) {
            updateSheet { copy(errors = errors + (groupId to "${group.name}最多選 ${group.maxSelect} 項")) }
            return
        }
        updateSheet { copy(selected = selected + (groupId to next), errors = errors - groupId) }
    }

    private fun confirmOptions() {
        val sheet = currentState.optionSheet ?: return
        val orderer = currentState.activeOrderer ?: return
        viewModelScope.launch {
            val result = saveMenuItemLine(
                sessionId = sessionId,
                orderer = orderer,
                item = sheet.item,
                groups = sheet.groups,
                selected = sheet.selected,
                quantity = sheet.quantity,
                note = sheet.note,
                existing = sheet.editingLine,
            )
            when (result) {
                is SaveLineResult.Invalid -> updateSheet { copy(errors = result.errors) }
                SaveLineResult.Saved -> {
                    setState { copy(optionSheet = null) }
                    val verb = if (sheet.editingLine != null) "已更新" else "已加入"
                    sendEffect(OrderEffect.ShowMessage("$verb ${orderer.name}：${sheet.item.name}"))
                }
            }
        }
    }

    private fun addManualItem(intent: OrderIntent.AddManualItem) {
        val orderer = currentState.activeOrderer ?: return
        if (intent.name.isBlank()) return
        setState { copy(showManualItemDialog = false) }
        viewModelScope.launch {
            saveManualLine(sessionId, orderer, intent.name, intent.price, intent.quantity, intent.note)
            sendEffect(OrderEffect.ShowMessage("已加入 ${orderer.name}：${intent.name.trim()}"))
        }
    }

    private fun updateSheet(reducer: OptionSheetState.() -> OptionSheetState) {
        setState { copy(optionSheet = optionSheet?.reducer()) }
    }
}
