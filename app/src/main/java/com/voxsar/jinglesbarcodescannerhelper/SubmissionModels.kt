package com.voxsar.jinglesbarcodescannerhelper

data class SubmissionPayload(
    val barcode: String,
    val expireDate: String? = null,
    val manufactureDate: String? = null,
    val itemQuantity: Int? = null,
    val location: Map<String, String>? = null,
)

object SubmissionPayloadFactory {
    fun create(
        barcode: String,
        expireDate: String?,
        manufactureDate: String?,
        itemQuantity: String?,
        locationPath: List<LocationNode>,
    ): SubmissionPayload {
        val trimmedBarcode = barcode.trim()
        val quantityValue = itemQuantity?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val deepestLocation = locationPath.lastOrNull()
        val locationPayload = deepestLocation?.let { node ->
            buildMap {
                put("id", node.id)
                put("name", node.name)
                put("type", node.type.name.lowercase())
                put("path", locationPath.joinToString(" > ") { it.name })
            }
        }

        return SubmissionPayload(
            barcode = trimmedBarcode,
            expireDate = expireDate?.takeIf { it.isNotBlank() },
            manufactureDate = manufactureDate?.takeIf { it.isNotBlank() },
            itemQuantity = quantityValue,
            location = locationPayload,
        )
    }
}
