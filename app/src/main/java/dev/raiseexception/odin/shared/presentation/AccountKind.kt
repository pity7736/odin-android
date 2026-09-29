package dev.raiseexception.odin.shared.presentation

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding

fun isMoneyAccount(account: Account): Boolean = when (account.funding) {
    is AccountFunding.Funds -> true
    is AccountFunding.Credit -> false
}
