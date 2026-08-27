package dev.raiseexception.odin.shared.presentation

object Routes {
    const val REGISTRATION = "registration"
    const val LOGIN = "login"
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_CREATE = "account_create"
    const val ACCOUNT_DETAIL = "account_detail/{accountId}"

    fun accountDetail(accountId: String) = "account_detail/$accountId"
}
