package com.symmetry.ordereat.domain

import com.symmetry.ordereat.domain.model.SelectedOption
import com.symmetry.ordereat.domain.model.SelectionType
import com.symmetry.ordereat.domain.usecase.OptionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OptionRulesTest {

    @Test
    fun `required multi with max 2 means exactly 2 (雙寶五擇二)`() {
        val g = group(SelectionType.MULTI, required = true, maxSelect = 2)
        assertNotNull(OptionRules.error(g, 0))
        assertNotNull(OptionRules.error(g, 1))
        assertNull(OptionRules.error(g, 2))
        assertNotNull(OptionRules.error(g, 3))
    }

    @Test
    fun `optional multi with max means at most`() {
        val g = group(SelectionType.MULTI, required = false, maxSelect = 3)
        assertNull(OptionRules.error(g, 0))
        assertNull(OptionRules.error(g, 3))
        assertNotNull(OptionRules.error(g, 4))
    }

    @Test
    fun `required multi unlimited needs at least one`() {
        val g = group(SelectionType.MULTI, required = true, maxSelect = 0)
        assertNotNull(OptionRules.error(g, 0))
        assertNull(OptionRules.error(g, 5))
    }

    @Test
    fun `single required and optional`() {
        assertNotNull(OptionRules.error(group(SelectionType.SINGLE, required = true), 0))
        assertNull(OptionRules.error(group(SelectionType.SINGLE, required = true), 1))
        assertNull(OptionRules.error(group(SelectionType.SINGLE, required = false), 0))
    }

    @Test
    fun `multi toggle blocks selecting beyond max`() {
        val g = group(SelectionType.MULTI, required = true, maxSelect = 2)
        val two = listOf("燒腩", "烤鴨")
        assertNull(OptionRules.toggle(g, two, "叉燒"))
        assertEquals(listOf("烤鴨"), OptionRules.toggle(g, two, "燒腩"))
        assertEquals(listOf("燒腩", "叉燒"), OptionRules.toggle(g, listOf("燒腩"), "叉燒"))
    }

    @Test
    fun `single toggle replaces, and required cannot be cleared`() {
        val required = group(SelectionType.SINGLE, required = true)
        assertEquals(listOf("烤鴨"), OptionRules.toggle(required, listOf("燒腩"), "烤鴨"))
        assertEquals(listOf("燒腩"), OptionRules.toggle(required, listOf("燒腩"), "燒腩"))
        val optional = group(SelectionType.SINGLE, required = false)
        assertEquals(emptyList<String>(), OptionRules.toggle(optional, listOf("燒腩"), "燒腩"))
    }

    @Test
    fun `unit price adds option prices`() {
        val selections = listOf(
            SelectedOption("size", "尺寸", "大杯", 10),
            SelectedOption("topping", "加料", "珍珠", 10),
            SelectedOption("ice", "冰塊", "去冰", -5),
        )
        assertEquals(70, OptionRules.unitPrice(55, selections))
    }
}
