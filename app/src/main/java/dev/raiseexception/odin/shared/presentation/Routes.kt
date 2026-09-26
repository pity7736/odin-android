package dev.raiseexception.odin.shared.presentation

object Routes {
    const val REGISTRATION = "registration"
    const val LOGIN = "login"
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_CREATE = "account_create"
    const val ACCOUNT_DETAIL = "account_detail/{accountId}"
    const val ACCOUNT_EDIT = "account_edit/{accountId}"
    const val INCOME_CREATE = "income_create?accountId={accountId}"
    const val EXPENSE_CREATE = "expense_create?accountId={accountId}"
    const val EXPENSE_EDIT = "expense_edit/{expenseId}"
    const val CATEGORIES = "categories"
    const val CATEGORY_CREATE = "category_create"
    const val CATEGORY_DETAIL = "category_detail/{categoryId}"
    const val TRANSACTION_DETAIL = "transaction_detail/{transactionId}"
    const val TRANSFER_CREATE = "transfer_create?accountId={accountId}"

    fun accountDetail(accountId: String) = "account_detail/$accountId"

    fun accountEdit(accountId: String) = "account_edit/$accountId"

    fun incomeCreate(accountId: String? = null): String =
        if (accountId != null) "income_create?accountId=$accountId" else "income_create"

    fun expenseCreate(accountId: String? = null): String =
        if (accountId != null) "expense_create?accountId=$accountId" else "expense_create"

    fun expenseEdit(expenseId: String) = "expense_edit/$expenseId"

    fun transferCreate(accountId: String? = null): String =
        if (accountId != null) "transfer_create?accountId=$accountId" else "transfer_create"

    fun categoryDetail(categoryId: String) = "category_detail/$categoryId"

    fun transactionDetail(transactionId: String) = "transaction_detail/$transactionId"
}
