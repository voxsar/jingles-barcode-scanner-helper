package com.voxsar.jinglesbarcodescannerhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubmissionPayloadFactoryTest {
    @Test
    fun createsPayloadWithOptionalFieldsWhenPresent() {
        val payload = SubmissionPayloadFactory.create(
            barcode = " 12345 ",
            expireDate = "2026-01-31",
            manufactureDate = "2025-12-10",
            itemQuantity = "7",
            locationPath = listOf(
                LocationNode("branch-1", "Branch", LocationType.BRANCH),
                LocationNode("floor-2", "Floor 2", LocationType.FLOOR),
                LocationNode("shelf-9", "Shelf 9", LocationType.SHELF),
            ),
        )

        assertEquals("12345", payload.barcode)
        assertEquals("2026-01-31", payload.expireDate)
        assertEquals("2025-12-10", payload.manufactureDate)
        assertEquals(7, payload.itemQuantity)
        assertEquals("shelf-9", payload.location?.get("id"))
        assertEquals("Branch > Floor 2 > Shelf 9", payload.location?.get("path"))
    }

    @Test
    fun skipsBlankOptionalFields() {
        val payload = SubmissionPayloadFactory.create(
            barcode = "ABC",
            expireDate = "",
            manufactureDate = " ",
            itemQuantity = "",
            locationPath = emptyList(),
        )

        assertNull(payload.expireDate)
        assertNull(payload.manufactureDate)
        assertNull(payload.itemQuantity)
        assertNull(payload.location)
    }
}
