package dev.raiseexception.odin.shared.presentation

object Routes {
    const val REGISTRATION = "registration"
    const val LOGIN = "login"
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_CREATE = "account_create"
    const val ACCOUNT_DETAIL = "account_detail/{accountId}"
    const val INCOME_CREATE = "income_create/{accountId}"
    const val CATEGORIES = "categories"
    const val CATEGORY_CREATE = "category_create"
    const val CATEGORY_DETAIL = "category_detail/{categoryId}"

    fun accountDetail(accountId: String) = "account_detail/$accountId"

    fun incomeCreate(accountId: String) = "income_create/$accountId"

    fun categoryDetail(categoryId: String) = "category_detail/$categoryId"
}
