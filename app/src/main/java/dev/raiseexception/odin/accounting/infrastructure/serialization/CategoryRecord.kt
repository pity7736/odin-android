package dev.raiseexception.odin.accounting.infrastructure.serialization

import kotlinx.serialization.Serializable

@Serializable
data class CategoryRecord(
    val recordType: String = CATEGORY_RECORD_TYPE,
    val id: String,
    val name: String,
    val categoryType: String,
    val description: String,
    val color: String,
    val createdAt: String
) {
    companion object {
        const val CATEGORY_RECORD_TYPE = "category"
    }
}
