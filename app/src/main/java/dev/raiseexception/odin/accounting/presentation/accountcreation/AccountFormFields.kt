package dev.raiseexception.odin.accounting.presentation.accountcreation

import dev.raiseexception.odin.accounting.domain.model.CategoryInput

data class AccountFormFields(
    val name: String,
    val balance: String,
    val creditLimit: String,
    val debt: String,
    val description: String,
    val gmfCategoryInput: CategoryInput
)
