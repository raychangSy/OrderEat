package com.symmetry.ordereat.domain

import com.symmetry.ordereat.domain.model.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Test

class RestaurantMapLinkTest {

    private fun restaurant(mapUrl: String, address: String = "台北市信義路 1 號") =
        Restaurant("r1", "金大富", emptyList(), "", address, "", null, mapUrl = mapUrl)

    @Test
    fun `uses sheet link when filled`() {
        assertEquals("https://maps.app.goo.gl/abc", restaurant(" https://maps.app.goo.gl/abc ").mapLink)
    }

    @Test
    fun `falls back to search by name and address`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=%E9%87%91%E5%A4%A7%E5%AF%8C+%E5%8F%B0%E5%8C%97%E5%B8%82%E4%BF%A1%E7%BE%A9%E8%B7%AF+1+%E8%99%9F",
            restaurant("").mapLink,
        )
    }

    @Test
    fun `ignores non http text`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=%E9%87%91%E5%A4%A7%E5%AF%8C",
            restaurant("見地址", address = "").mapLink,
        )
    }

    @Test
    fun `menu link only when it is a web url`() {
        val r = restaurant("")
        assertEquals(null, r.copy(menuUrl = "").menuLink)
        assertEquals(null, r.copy(menuUrl = "看照片").menuLink)
        assertEquals("https://shop.dsorder10.com/54J4D0jS/Store", r.copy(menuUrl = " https://shop.dsorder10.com/54J4D0jS/Store").menuLink)
    }
}
