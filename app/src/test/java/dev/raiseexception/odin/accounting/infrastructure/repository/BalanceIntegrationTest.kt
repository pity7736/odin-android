package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private const val MASTER_KEY_SIZE = 32
private const val SEED_ENTRY_COUNT = 9

class BalanceIntegrationTest {

    private fun createStore() = InMemoryEncryptedRecordStore(
        vaultCrypto = BouncyCastleVaultCrypto(),
        masterKeyRepository = FakeMasterKeyRepository(ByteArray(MASTER_KEY_SIZE) { it.toByte() }),
        cpuDispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun `given seeder data, when loading ahorros via account finder, then balance is 3500000`() = runTest {
        val store = createStore()
        val accountRepository = VaultAccountRepository(store)
        val categoryRepository = VaultCategoryRepository(store)
        val incomeRepository = VaultIncomeRepository(store)
        val categoryCreator = CategoryCreator(categoryRepository)
        val incomeCreator = IncomeCreator(
            accountRepository = accountRepository,
            incomeRepository = incomeRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = VaultTransactionRunner()
        )
        val accountCreator = AccountCreator(accountRepository)
        val accountFinder = AccountFinder(accountRepository)

        val ahorros = (
            accountCreator.create(
                "Ahorros",
                "1000000",
                Currency.COP,
                AccountType.SAVINGS,
                ""
            ) as Outcome.Success
            ).value
        accountCreator.create("Efectivo", "50000", Currency.COP, AccountType.CASH, "")

        categoryCreator.create("Alimentación", CategoryType.EXPENSE, "", null)
        categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null)
        categoryCreator.create("Entretenimiento", CategoryType.EXPENSE, "", null)

        val salarioResult = incomeCreator.create(
            accountId = ahorros.id,
            amount = "2000000",
            date = "2026-08-30",
            categoryInput = CategoryInput.New("Salario"),
            description = "Pago mensual"
        )
        assertTrue("Salario income should succeed: $salarioResult", salarioResult is Outcome.Success)

        val freelanceResult = incomeCreator.create(
            accountId = ahorros.id,
            amount = "500000",
            date = "2026-08-24",
            categoryInput = CategoryInput.New("Freelance"),
            description = "Proyecto web"
        )
        assertTrue("Freelance income should succeed: $freelanceResult", freelanceResult is Outcome.Success)

        assertEquals(SEED_ENTRY_COUNT, store.entries.size)

        val loaded = accountFinder.find(
            ahorros.id,
            AccountCriteria(includeIncomes = true, includeExpenses = true)
        )
        assertTrue("AccountFinder.find should succeed: $loaded", loaded is Outcome.Success)
        val account = (loaded as Outcome.Success).value
        assertEquals(2, account.incomes.size)
        assertEquals(0, account.expenses.size)
        assertEquals(0, account.balance.amount.compareTo(BigDecimal("3500000")))
    }

    @Test
    fun `given account with income, when loading via account finder, then balance includes income`() = runTest {
        val store = createStore()
        val accountRepository = VaultAccountRepository(store)
        val categoryRepository = VaultCategoryRepository(store)
        val incomeRepository = VaultIncomeRepository(store)
        val categoryCreator = CategoryCreator(categoryRepository)
        val incomeCreator = IncomeCreator(
            accountRepository = accountRepository,
            incomeRepository = incomeRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = VaultTransactionRunner()
        )
        val accountCreator = AccountCreator(accountRepository)
        val accountFinder = AccountFinder(accountRepository)

        val account = (
            accountCreator.create(
                "Ahorros",
                "1000000",
                Currency.COP,
                AccountType.SAVINGS,
                ""
            ) as Outcome.Success
            ).value

        val incomeResult = incomeCreator.create(
            accountId = account.id,
            amount = "2000000",
            date = "2026-08-30",
            categoryInput = CategoryInput.New("Salario"),
            description = "Pago mensual"
        )
        assertTrue("Income creation should succeed: $incomeResult", incomeResult is Outcome.Success)

        val loaded = accountFinder.find(
            account.id,
            AccountCriteria(includeIncomes = true, includeExpenses = true)
        )
        assertTrue("AccountFinder.find should succeed: $loaded", loaded is Outcome.Success)
        val loadedAccount = (loaded as Outcome.Success).value
        assertEquals(1, loadedAccount.incomes.size)
        assertEquals(0, loadedAccount.balance.amount.compareTo(BigDecimal("3000000")))
    }

    @Test
    fun `given account with expense, when loading via account finder, then balance includes expense`() = runTest {
        val store = createStore()
        val accountRepository = VaultAccountRepository(store)
        val categoryRepository = VaultCategoryRepository(store)
        val expenseRepository = VaultExpenseRepository(store)
        val categoryCreator = CategoryCreator(categoryRepository)
        val expenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = VaultTransactionRunner()
        )
        val accountCreator = AccountCreator(accountRepository)
        val accountFinder = AccountFinder(accountRepository)

        val account = (
            accountCreator.create(
                "Ahorros",
                "1000000",
                Currency.COP,
                AccountType.SAVINGS,
                ""
            ) as Outcome.Success
            ).value

        val expenseResult = expenseCreator.create(
            accountId = account.id,
            amount = "200000",
            date = "2026-08-30",
            categoryInput = CategoryInput.New("Alimentación"),
            description = "Mercado"
        )
        assertTrue("Expense creation should succeed: $expenseResult", expenseResult is Outcome.Success)

        val loaded = accountFinder.find(
            account.id,
            AccountCriteria(includeIncomes = true, includeExpenses = true)
        )
        assertTrue("AccountFinder.find should succeed: $loaded", loaded is Outcome.Success)
        val loadedAccount = (loaded as Outcome.Success).value
        assertEquals(1, loadedAccount.expenses.size)
        assertEquals(0, loadedAccount.balance.amount.compareTo(BigDecimal("800000")))
    }
}
