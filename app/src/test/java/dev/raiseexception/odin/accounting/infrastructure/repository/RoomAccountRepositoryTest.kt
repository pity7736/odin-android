package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.ExpenseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class RoomAccountRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomAccountRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAccountRepository(database.accountDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given an account, when added and read back, then all fields match`() = runTest {
        val savings = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1500.00"), Currency.COP))
            .kind(MoneyAccountKind.SAVINGS)
            .description("Fondo de emergencia")
            .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build()
        repository.add(savings)
        val result = repository.findById("acc-1").first()
        assertTrue(result is Outcome.Success)
        val restored = (result as Outcome.Success).value
        assertEquals("acc-1", restored.id)
        assertEquals("Ahorros", restored.name)
        val funds = restored.funding as AccountFunding.Funds
        assertEquals(Money.of(BigDecimal("1500.00"), Currency.COP), funds.initialBalance)
        assertEquals(AccountType.SAVINGS, restored.type)
        assertEquals("Fondo de emergencia", restored.description)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), restored.createdAt)
    }

    @Test
    fun `given a credit card, when added and read back, then credit funding is restored`() = runTest {
        val card = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .creditCard(
                creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
                initialDebt = Money.of(BigDecimal("500000.00"), Currency.COP)
            )
            .description("Tarjeta principal")
            .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build()
        repository.add(card)
        val result = repository.findById("card-1").first()
        assertTrue(result is Outcome.Success)
        val restored = (result as Outcome.Success).value
        assertEquals("card-1", restored.id)
        assertEquals("Visa", restored.name)
        assertEquals(AccountType.CREDIT_CARD, restored.type)
        val credit = restored.funding as AccountFunding.Credit
        assertEquals(Money.of(BigDecimal("3000000.00"), Currency.COP), credit.creditLimit)
        assertEquals(Money.of(BigDecimal("500000.00"), Currency.COP), credit.initialDebt)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), restored.createdAt)
    }

    @Test
    fun `given an existing account, when update, then stored account reflects the new values`() = runTest {
        val original = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .kind(MoneyAccountKind.SAVINGS)
            .description("Fondo")
            .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build()
        repository.add(original)
        val editOutcome = original.edit(
            name = "Corriente",
            initialBalance = "2000.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "Gastos diarios",
            gmf = GmfTreatment.Exempt
        )
        val edited = (editOutcome as Outcome.Success).value

        val updateOutcome = repository.update(edited)

        assertTrue(updateOutcome is Outcome.Success)
        val result = repository.findById("acc-1").first()
        assertTrue(result is Outcome.Success)
        val stored = (result as Outcome.Success).value
        assertEquals("Corriente", stored.name)
        val funds = stored.funding as AccountFunding.Funds
        assertEquals(Money.of(BigDecimal("2000.00"), Currency.USD), funds.initialBalance)
        assertEquals(AccountType.CASH, stored.type)
        assertEquals("Gastos diarios", stored.description)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), stored.createdAt)
    }

    @Test
    fun `given a stored SAVINGS row, when findById, then funding is Funds with SAVINGS kind`() = runTest {
        database.accountDao().insert(
            AccountEntity(
                id = "acc-1",
                name = "Ahorros",
                initialBalanceAmount = "1500.00",
                currency = "COP",
                type = "SAVINGS",
                description = "",
                createdAt = "2026-08-01T10:00:00Z",
                creditLimitAmount = null,
                debtAmount = null
            )
        )

        val account = (repository.findById("acc-1").first() as Outcome.Success).value

        val funds = account.funding as AccountFunding.Funds
        assertEquals(MoneyAccountKind.SAVINGS, funds.kind)
        assertEquals(Money.of(BigDecimal("1500.00"), Currency.COP), funds.initialBalance)
        assertEquals(AccountType.SAVINGS, account.type)
    }

    @Test
    fun `given a stored CASH row, when findById, then funding is Funds with CASH kind`() = runTest {
        database.accountDao().insert(
            AccountEntity(
                id = "acc-2",
                name = "Efectivo",
                initialBalanceAmount = "20000.00",
                currency = "USD",
                type = "CASH",
                description = "",
                createdAt = "2026-08-01T10:00:00Z",
                creditLimitAmount = null,
                debtAmount = null
            )
        )

        val account = (repository.findById("acc-2").first() as Outcome.Success).value

        val funds = account.funding as AccountFunding.Funds
        assertEquals(MoneyAccountKind.CASH, funds.kind)
        assertEquals(Money.of(BigDecimal("20000.00"), Currency.USD), funds.initialBalance)
        assertEquals(AccountType.CASH, account.type)
    }

    @Test
    fun `given a stored CREDIT_CARD row, when findById, then funding is Credit with its limit and debt`() = runTest {
        database.accountDao().insert(
            AccountEntity(
                id = "card-1",
                name = "Visa",
                initialBalanceAmount = null,
                currency = "COP",
                type = "CREDIT_CARD",
                description = "",
                createdAt = "2026-08-01T10:00:00Z",
                creditLimitAmount = "3000000.00",
                debtAmount = "500000.00"
            )
        )

        val account = (repository.findById("card-1").first() as Outcome.Success).value

        val credit = account.funding as AccountFunding.Credit
        assertEquals(Money.of(BigDecimal("3000000.00"), Currency.COP), credit.creditLimit)
        assertEquals(Money.of(BigDecimal("500000.00"), Currency.COP), credit.initialDebt)
        assertEquals(AccountType.CREDIT_CARD, account.type)
    }

    @Test
    fun `given an existing name, when checking existsByName, then returns true`() = runTest {
        repository.add(AccountBuilder().name("Ahorros").build())
        val result = repository.existsByName("Ahorros")
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given an existing name in different case, when checking existsByName, then returns true`() = runTest {
        repository.add(AccountBuilder().name("Ahorros").build())
        val result = repository.existsByName("ahorros")
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given no matching name, when checking existsByName, then returns false`() = runTest {
        repository.add(AccountBuilder().name("Ahorros").build())
        val result = repository.existsByName("Corriente")
        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given an account without criteria, when findById, then emits account without transactions`() = runTest {
        val savings = AccountBuilder().id("acc-1").build()
        repository.add(savings)
        val result = repository.findById("acc-1").first()
        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertTrue(account.incomes.isEmpty())
        assertTrue(account.expenses.isEmpty())
    }

    @Test
    fun `given account with transactions, when findById with criteria, then splits by type`() =
        runTest {
            val savings = AccountBuilder().id("acc-1").build()
            repository.add(savings)
            val category = CategoryEntity(
                id = "cat-1",
                name = "Salario",
                type = "INCOME",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
            database.categoryDao().insert(category)
            val income = TransactionEntity(
                id = "inc-1",
                type = "INCOME",
                accountId = "acc-1",
                amount = "500.00",
                currency = "COP",
                date = "2026-08-01",
                categoryId = "cat-1",
                description = "Pago",
                createdAt = "2026-08-01T10:00:00Z"
            )
            val expense = TransactionEntity(
                id = "exp-1",
                type = "EXPENSE",
                accountId = "acc-1",
                amount = "200.00",
                currency = "COP",
                date = "2026-08-02",
                categoryId = "cat-1",
                description = "Mercado",
                createdAt = "2026-08-02T10:00:00Z"
            )
            database.transactionDao().insert(income)
            database.transactionDao().insert(expense)
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
            val result = repository.findById("acc-1", criteria).first()
            assertTrue(result is Outcome.Success)
            val account = (result as Outcome.Success).value
            assertEquals(1, account.incomes.size)
            assertEquals("inc-1", account.incomes.first().id)
            assertEquals(1, account.expenses.size)
            assertEquals("exp-1", account.expenses.first().id)
        }

    @Test
    fun `given non-existent id, when findById, then emits NotFound`() = runTest {
        val result = repository.findById("non-existent").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.NotFound)
    }

    @Test
    fun `given accounts, when getAll without criteria, then returns all without transactions`() = runTest {
        repository.add(AccountBuilder().id("acc-1").name("Ahorros").build())
        repository.add(AccountBuilder().id("acc-2").name("Efectivo").build())
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        val accounts = (result as Outcome.Success).value
        assertEquals(2, accounts.size)
        assertTrue(accounts.all { it.incomes.isEmpty() && it.expenses.isEmpty() })
    }

    @Test
    fun `given accounts with transactions, when getAll with full criteria, then returns all with transactions`() =
        runTest {
            repository.add(AccountBuilder().id("acc-1").name("Ahorros").build())
            val category = CategoryEntity(
                id = "cat-1",
                name = "Salario",
                type = "INCOME",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
            database.categoryDao().insert(category)
            val income = TransactionEntity(
                id = "inc-1",
                type = "INCOME",
                accountId = "acc-1",
                amount = "500.00",
                currency = "COP",
                date = "2026-08-01",
                categoryId = "cat-1",
                description = "",
                createdAt = "2026-08-01T10:00:00Z"
            )
            database.transactionDao().insert(income)
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
            val result = repository.getAll(criteria).first()
            assertTrue(result is Outcome.Success)
            val accounts = (result as Outcome.Success).value
            assertEquals(1, accounts.size)
            assertEquals(1, accounts.first().incomes.size)
        }

    @Test
    fun `given empty database, when getAll, then returns empty list`() = runTest {
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `given a tagged expense, when findById with full criteria, then the expense carries its tag ids`() = runTest {
        seedAccountWithTransactions()
        linkTags("exp-tagged", "tag-nala", "tag-comida")
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        val account = (repository.findById("acc-1", criteria).first() as Outcome.Success).value
        val taggedExpense = account.expenses.single { it.id == "exp-tagged" }
        assertEquals(setOf("tag-nala", "tag-comida"), taggedExpense.tagIds.toSet())
    }

    @Test
    fun `given an untagged expense, when findById with full criteria, then the expense has no tag ids`() = runTest {
        seedAccountWithTransactions()
        linkTags("exp-tagged", "tag-nala")
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        val account = (repository.findById("acc-1", criteria).first() as Outcome.Success).value
        assertTrue(account.expenses.single { it.id == "exp-untagged" }.tagIds.isEmpty())
        assertEquals(listOf("inc-1"), account.incomes.map { it.id })
    }

    @Test
    fun `given a tagged expense, when getAll with full criteria, then the expense carries its tag ids`() = runTest {
        seedAccountWithTransactions()
        linkTags("exp-tagged", "tag-nala")
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        val accounts = (repository.getAll(criteria).first() as Outcome.Success).value
        val expenses = accounts.single().expenses
        assertEquals(listOf("tag-nala"), expenses.single { it.id == "exp-tagged" }.tagIds)
        assertTrue(expenses.single { it.id == "exp-untagged" }.tagIds.isEmpty())
        assertEquals(1, accounts.single().incomes.size)
    }

    @Test
    fun `given a non-exempt account, when added and read back, then its tax category and tags are restored`() =
        runTest {
            database.categoryDao().insert(expenseCategory("cat-impuestos"))
            val treatment = GmfTreatment.Taxed("cat-impuestos", listOf("4x1000", "Impuestos"))
            repository.add(AccountBuilder().id("acc-1").gmf(treatment).build())

            val restored = (repository.findById("acc-1").first() as Outcome.Success).value

            assertEquals(treatment, (restored.funding as AccountFunding.Funds).gmf)
        }

    @Test
    fun `given an exempt account, when added and read back, then it is exempt with no tax columns`() = runTest {
        repository.add(AccountBuilder().id("acc-1").gmf(GmfTreatment.Exempt).build())

        val restored = (repository.findById("acc-1").first() as Outcome.Success).value
        val stored = database.accountDao().findById("acc-1").first()!!

        assertEquals(GmfTreatment.Exempt, (restored.funding as AccountFunding.Funds).gmf)
        assertNull(stored.gmfCategoryId)
        assertNull(stored.gmfTagNames)
    }

    @Test
    fun `given a stored savings row from before the tax existed, when findById, then it is exempt`() = runTest {
        database.accountDao().insert(
            AccountEntity(
                id = "acc-1",
                name = "Ahorros",
                initialBalanceAmount = "1500.00",
                currency = "COP",
                type = "SAVINGS",
                description = "",
                createdAt = "2026-08-01T10:00:00Z"
            )
        )

        val account = (repository.findById("acc-1").first() as Outcome.Success).value

        assertEquals(GmfTreatment.Exempt, (account.funding as AccountFunding.Funds).gmf)
    }

    @Test
    fun `given a movement with its tax, when findById with full criteria, then the tax is nested in its movement`() =
        runTest {
            seedTaxedMovement()
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

            val account = (repository.findById("acc-1", criteria).first() as Outcome.Success).value

            val mercado = account.expenses.first()
            assertEquals("exp-mercado", mercado.id)
            assertEquals("tax-mercado", mercado.gmfTax!!.id)
            assertEquals(listOf("tag-4x1000"), mercado.gmfTax!!.tagIds)
            assertEquals(listOf("exp-mercado", "tax-mercado"), account.expenses.map { it.id })
            assertEquals(Money.of(BigDecimal("99600.00"), Currency.COP), account.balance)
        }

    @Test
    fun `given a movement with its tax, when getAll with full criteria, then the tax is nested in its movement`() =
        runTest {
            seedTaxedMovement()
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

            val accounts = (repository.getAll(criteria).first() as Outcome.Success).value

            assertEquals(listOf("exp-mercado", "tax-mercado"), accounts.single().expenses.map { it.id })
            assertEquals("tax-mercado", accounts.single().expenses.first().gmfTax!!.id)
        }

    private suspend fun seedTaxedMovement() {
        val ahorros = AccountBuilder()
            .id("acc-1")
            .initialBalance(Money.of(BigDecimal("200000.00"), Currency.COP))
            .build()
        repository.add(ahorros)
        database.categoryDao().insert(this.expenseCategory("cat-1"))
        database.tagDao().insert(TagEntity("tag-4x1000", "4x1000", "4x1000", "2026-01-01T00:00:00Z"))
        val tax = ExpenseBuilder()
            .id("tax-mercado")
            .accountId("acc-1")
            .amount("400.00")
            .categoryId("cat-1")
            .description("4x1000 – Mercado")
            .tagIds(listOf("tag-4x1000"))
            .build()
        val mercado = ExpenseBuilder()
            .id("exp-mercado")
            .accountId("acc-1")
            .amount("100000.00")
            .categoryId("cat-1")
            .description("Mercado")
            .gmfTax(tax)
            .build()
        RoomExpenseRepository(database.transactionDao(), database.expenseTagDao()).add(mercado)
    }

    private fun expenseCategory(id: String) = CategoryEntity(
        id = id,
        name = id,
        type = "EXPENSE",
        description = "",
        color = "#FF0000",
        createdAt = "2026-01-01T00:00:00Z"
    )

    private suspend fun seedAccountWithTransactions() {
        repository.add(AccountBuilder().id("acc-1").build())
        database.categoryDao().insert(
            CategoryEntity(
                id = "cat-1",
                name = "Perros",
                type = "EXPENSE",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
        )
        database.transactionDao().insert(transaction("inc-1", "INCOME"))
        database.transactionDao().insert(transaction("exp-tagged", "EXPENSE"))
        database.transactionDao().insert(transaction("exp-untagged", "EXPENSE"))
    }

    private suspend fun linkTags(expenseId: String, vararg tagIds: String) {
        tagIds.forEach { tagId ->
            database.tagDao().insert(TagEntity(tagId, tagId, tagId, "2026-01-01T00:00:00Z"))
        }
        database.expenseTagDao().insertAll(tagIds.map { ExpenseTagEntity(expenseId = expenseId, tagId = it) })
    }

    private fun transaction(id: String, type: String) = TransactionEntity(
        id = id,
        type = type,
        accountId = "acc-1",
        amount = "100.00",
        currency = "COP",
        date = "2026-08-01",
        categoryId = "cat-1",
        description = "",
        createdAt = "2026-08-01T10:00:00Z"
    )
}
