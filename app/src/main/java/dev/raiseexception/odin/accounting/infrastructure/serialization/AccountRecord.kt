package dev.raiseexception.odin.accounting.infrastructure.serialization

import kotlinx.serialization.Serializable

@Serializable
data class AccountRecord(
    val recordType: String = ACCOUNT_RECORD_TYPE,
    val id: String,
    val name: String,
    val amount: String,
    val currency: String,
    val accountType: String,
    val description: String,
    val createdAt: String
) {
    companion object {
        const val ACCOUNT_RECORD_TYPE = "account"
    }
}
