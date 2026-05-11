package com.voxsar.jinglesbarcodescannerhelper

enum class LocationType {
    BRANCH,
    FLOOR,
    SHELF,
    BOX,
    UNKNOWN;

    companion object {
        fun from(raw: String?): LocationType = when (raw?.trim()?.lowercase()) {
            "branch", "branches" -> BRANCH
            "floor", "floors" -> FLOOR
            "shelf", "shelves", "shelfs" -> SHELF
            "box", "boxes" -> BOX
            else -> UNKNOWN
        }
    }
}

data class LocationNode(
    val id: String,
    val name: String,
    val type: LocationType,
    val children: List<LocationNode> = emptyList(),
)

object LocationTreeParser {
    fun parse(raw: Any?): List<LocationNode> = when (raw) {
        is List<*> -> raw.mapNotNull { parseNode(it, null) }
        is Map<*, *> -> parseRoot(raw)
        else -> emptyList()
    }

    private fun parseRoot(raw: Map<*, *>): List<LocationNode> {
        val map = raw.entries.associate { it.key.toString() to it.value }
        val collectionKeys = listOf("branches", "floors", "shelves", "shelfs", "boxes", "locations")
        val roots = collectionKeys.flatMap { key ->
            val value = map[key]
            if (value is List<*>) {
                val fallbackType = if (key == "locations") null else LocationType.from(key)
                value.mapNotNull { parseNode(it, fallbackType) }
            } else {
                emptyList()
            }
        }

        return if (roots.isNotEmpty()) {
            roots
        } else {
            listOfNotNull(parseNode(map, LocationType.from(map["type"]?.toString())))
        }
    }

    private fun parseNode(raw: Any?, fallbackType: LocationType?): LocationNode? {
        return when (raw) {
            is Map<*, *> -> {
                val map = raw.entries.associate { it.key.toString() to it.value }
                val nodeType = LocationType.from(map["type"]?.toString()).takeUnless { it == LocationType.UNKNOWN }
                    ?: fallbackType
                    ?: LocationType.UNKNOWN
                val nodeName = firstNonBlank(map["name"], map["label"], map["title"], map["code"], map["id"])
                    ?: return null
                val nodeId = firstNonBlank(map["id"], map["code"], map["key"], map["value"], nodeName).orEmpty()
                val children = buildChildren(map)
                LocationNode(id = nodeId, name = nodeName, type = nodeType, children = children)
            }
            is String -> fallbackType?.let { LocationNode(id = raw, name = raw, type = it) }
            else -> null
        }
    }

    private fun buildChildren(map: Map<String, Any?>): List<LocationNode> {
        val typedChildren = listOf("branches", "floors", "shelves", "shelfs", "boxes").flatMap { key ->
            val value = map[key]
            if (value is List<*>) {
                value.mapNotNull { parseNode(it, LocationType.from(key)) }
            } else {
                emptyList()
            }
        }

        val genericChildren = (map["children"] as? List<*>)?.mapNotNull { child ->
            parseNode(child, null)
        }.orEmpty()

        return typedChildren + genericChildren
    }

    private fun firstNonBlank(vararg values: Any?): String? {
        return values.firstNotNullOfOrNull { value ->
            value?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
