package com.voxsar.jinglesbarcodescannerhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTreeParserTest {
    @Test
    fun parsesBranchFloorShelfBoxHierarchy() {
        val raw = mapOf(
            "branches" to listOf(
                mapOf(
                    "id" to "branch-a",
                    "name" to "Main branch",
                    "floors" to listOf(
                        mapOf(
                            "id" to "floor-1",
                            "name" to "Floor 1",
                            "shelves" to listOf(
                                mapOf(
                                    "id" to "shelf-7",
                                    "name" to "Shelf 7",
                                    "boxes" to listOf(
                                        mapOf("id" to "box-2", "name" to "Box 2"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = LocationTreeParser.parse(raw)

        assertEquals(1, result.size)
        assertEquals(LocationType.BRANCH, result.first().type)
        assertEquals(LocationType.FLOOR, result.first().children.first().type)
        assertEquals(LocationType.SHELF, result.first().children.first().children.first().type)
        assertEquals(LocationType.BOX, result.first().children.first().children.first().children.first().type)
    }

    @Test
    fun supportsFloorAndShelfRootsWithoutBranch() {
        val raw = mapOf(
            "floors" to listOf(mapOf("id" to "f-1", "name" to "Front floor")),
            "shelves" to listOf(mapOf("id" to "s-1", "name" to "Promo shelf")),
        )

        val result = LocationTreeParser.parse(raw)

        assertEquals(2, result.size)
        assertTrue(result.any { it.type == LocationType.FLOOR })
        assertTrue(result.any { it.type == LocationType.SHELF })
    }
}
