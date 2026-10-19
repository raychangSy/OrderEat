package com.symmetry.ordereat.domain

import com.symmetry.ordereat.domain.model.Member
import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.usecase.BuildOrderSummaryUseCase
import com.symmetry.ordereat.domain.usecase.FindMissingMembersUseCase
import com.symmetry.ordereat.domain.usecase.FormatOrderTextUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrderSummaryUseCasesTest {

    private val lessRice = SelectedOption("rice", "飯量", "少飯", 0)
    private val lines = listOf(
        line("Ann", "燒腩飯", 120, createdAt = 1),
        line("Ivy", "燒腩飯", 120, quantity = 2, createdAt = 2, note = "不要蔥"),
        line("Mark", "燒腩飯", 120, selections = listOf(lessRice), createdAt = 3),
        line("Ann", "雙寶飯", 120, selections = listOf(SelectedOption("two", "雙寶", "燒腩", 0), SelectedOption("two", "雙寶", "叉燒", 0)), createdAt = 4),
    )

    @Test
    fun `groups by item and options, sums quantity and amount`() {
        val summary = BuildOrderSummaryUseCase()(lines)
        assertEquals(3, summary.byItem.size)
        val plain = summary.byItem.first { it.itemName == "燒腩飯" && it.optionsText.isEmpty() }
        assertEquals(3, plain.quantity)
        assertEquals(360, plain.amount)
        assertEquals(listOf("Ivy：不要蔥"), plain.notes)
        assertEquals("燒腩+叉燒", summary.byItem.first { it.itemName == "雙寶飯" }.optionsText)
        assertEquals(600, summary.total)
        assertEquals(5, summary.itemCount)
    }

    @Test
    fun `per member totals`() {
        val byMember = BuildOrderSummaryUseCase()(lines).byMember.associate { it.memberName to it.total }
        assertEquals(mapOf("Ann" to 240, "Ivy" to 240, "Mark" to 120), byMember)
    }

    @Test
    fun `missing members are active members without lines`() {
        val members = listOf(
            Member("id-Ann", "Ann", true),
            Member("id-14", "14", true),
            Member("id-Old", "Old", false),
        )
        assertEquals(listOf("14"), FindMissingMembersUseCase()(members, lines).map { it.name })
    }

    @Test
    fun `text output includes items, total and per member`() {
        val summary = BuildOrderSummaryUseCase()(lines)
        val text = FormatOrderTextUseCase()(session("a", LocalDate.of(2026, 10, 7), lines), summary)
        assertTrue(text.startsWith("【午餐】店a　2026-10-07"))
        assertTrue(text.contains("燒腩飯 ×3"))
        assertTrue(text.contains("燒腩飯（少飯） ×1"))
        assertTrue(text.contains("　└ Ivy：不要蔥"))
        assertTrue(text.contains("共 5 份，總計 $600"))
        assertTrue(text.contains("Ann　$240"))
    }
}
