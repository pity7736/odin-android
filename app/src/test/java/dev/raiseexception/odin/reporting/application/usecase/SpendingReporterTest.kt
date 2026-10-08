package dev.raiseexception.odin.reporting.application.usecase

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.SpendingLister
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import dev.raiseexception.odin.testutil.ExpenseBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@Suppress("LargeClass", "MagicNumber")
class SpendingReporterTest {

    private val spendingLister = mockk<SpendingLister>()
    private val categoryLister = mockk<CategoryLister>()
    private val accountLister = mockk<AccountLister>()
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-07T15:00:00Z")
    }
    private val spendingReporter = SpendingReporter(
        spendingLister = spendingLister,
        categoryLister = categoryLister,
        accountLister = accountLister,
        clock = fixedClock,
        timeZone = TimeZone.of("America/Bogota"),
    )
    private val october = ReportPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"))
    private val rentCategory = CategoryBuilder().id("cat-rent").name("Arriendo").color("#3B82F6").build()
    private val marketCategory = CategoryBuilder().id("cat-market").name("Mercado").color("#F97316").build()
    private val leisureCategory = CategoryBuilder().id("cat-leisure").name("Ocio").color("#22C55E").build()
    private val travelCategory = CategoryBuilder().id("cat-travel").name("Viajes").color("#A855F7").build()
    private val pesosAccount = AccountBuilder().id("acc-cop").build()
    private val dollarsAccount = AccountBuilder().id("acc-usd").initialBalance(dollars("100.00")).build()
    private val eurosAccount = AccountBuilder().id("acc-eur").initialBalance(euros("100.00")).build()

    @Test
    fun `given today is October 7, when getting the starting period, then it runs from October 1 to October 7`() {
        assertEquals(this.october, this.spendingReporter.startingPeriod())
    }

    @Test
    fun `given an end earlier than the start, when validating the period, then returns null`() {
        assertNull(this.spendingReporter.validPeriod(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-09")))
    }

    @Test
    fun `given an end after today, when validating the period, then returns null`() {
        assertNull(this.spendingReporter.validPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-08")))
    }

    @Test
    fun `given the same start and end, when validating the period, then returns that period`() {
        val day = LocalDate.parse("2026-09-10")
        assertEquals(ReportPeriod(day, day), this.spendingReporter.validPeriod(day, day))
    }

    @Test
    fun `given a period far in the past, when validating the period, then returns that period`() {
        val start = LocalDate.parse("1990-01-01")
        val end = LocalDate.parse("1990-12-31")
        assertEquals(ReportPeriod(start, end), this.spendingReporter.validPeriod(start, end))
    }

    @Test
    fun `given spending in the current month, when reporting, then totals shares and order are by category`() =
        runTest {
            stub(
                expenses = listOf(
                    pesosExpense("cat-rent", "1800000.00"),
                    pesosExpense("cat-market", "800000.00"),
                    pesosExpense("cat-market", "450000.00"),
                ),
            )
            val report = reportOf(october, null)
            assertEquals(pesos("3050000.00"), report.total)
            assertEquals(listOf("Arriendo", "Mercado"), report.categories.map { it.name })
            assertEquals(listOf(pesos("1800000.00"), pesos("1250000.00")), report.categories.map { it.total })
            assertEquals(
                listOf(DisplayedShare.Whole(59), DisplayedShare.Whole(41)),
                report.categories.map { it.displayedShare }
            )
        }

    @Test
    fun `given a period, when reporting, then asks the spending lister for exactly that period`() = runTest {
        stub(expenses = listOf(pesosExpense("cat-market", "100000.00")))
        reportOf(october, null)
        verify {
            spendingLister.list(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"))
        }
    }

    @Test
    fun `given expenses in one category from every account, when reporting, then they are added together`() =
        runTest {
            val visaCard = AccountBuilder().id("card-visa").creditCard(pesos("5000000.00"), pesos("0.00")).build()
            stub(
                expenses = listOf(
                    ExpenseBuilder().accountId("acc-cash").categoryId("cat-market").amount("40000.00").build(),
                    ExpenseBuilder().accountId("acc-cop").categoryId("cat-market").amount("60000.00").build(),
                    ExpenseBuilder().accountId("card-visa").categoryId("cat-market").amount("100000.00").build(),
                ),
                accounts = listOf(pesosAccount, visaCard),
            )
            val report = reportOf(october, null)
            assertEquals(listOf(pesos("200000.00")), report.categories.map { it.total })
        }

    @Test
    fun `given expenses in COP and USD, when reporting COP, then lists only the COP expenses`() = runTest {
        stubTwoCurrencies()
        val report = reportOf(october, Currency.COP)
        assertEquals(pesos("1250000.00"), report.total)
        assertEquals(listOf("Mercado"), report.categories.map { it.name })
    }

    @Test
    fun `given expenses in COP and USD, when reporting USD, then lists only the USD expenses`() = runTest {
        stubTwoCurrencies()
        val report = reportOf(october, Currency.USD)
        assertEquals(dollars("120.00"), report.total)
        assertEquals(listOf("Viajes"), report.categories.map { it.name })
    }

    @Test
    fun `given accounts in EUR, USD and COP, when reporting, then currencies are in COP, USD, EUR order`() = runTest {
        stub(
            expenses = emptyList(),
            accounts = listOf(
                eurosAccount,
                dollarsAccount,
                pesosAccount,
            ),
        )
        val report = reportOf(october, null)
        assertEquals(listOf(Currency.COP, Currency.USD, Currency.EUR), report.currencies)
    }

    @Test
    fun `given accounts all in COP, when reporting, then currencies is only COP`() = runTest {
        val cashAccount = AccountBuilder().id("acc-cash").build()
        stub(
            expenses = emptyList(),
            accounts = listOf(pesosAccount, cashAccount),
        )
        val report = reportOf(october, null)
        assertEquals(listOf(Currency.COP), report.currencies)
    }

    @Test
    fun `given a COP account among others, when reporting with no chosen currency, then COP is the currency`() =
        runTest {
            stub(
                expenses = emptyList(),
                accounts = listOf(dollarsAccount, pesosAccount),
            )
            val report = reportOf(october, null)
            assertEquals(Currency.COP, report.currency)
        }

    @Test
    fun `given accounts in USD and EUR only, when reporting with no chosen currency, then USD is the currency`() =
        runTest {
            stub(
                expenses = emptyList(),
                accounts = listOf(eurosAccount, dollarsAccount),
            )
            val report = reportOf(october, null)
            assertEquals(listOf(Currency.USD, Currency.EUR), report.currencies)
            assertEquals(Currency.USD, report.currency)
        }

    @Test
    fun `given no accounts, when reporting with no chosen currency, then COP is the currency`() = runTest {
        stub(expenses = emptyList(), accounts = emptyList())
        val report = reportOf(october, null)
        assertEquals(Currency.COP, report.currency)
        assertTrue(report.currencies.isEmpty())
    }

    @Test
    fun `given USD chosen and no USD spending, when reporting, then keeps USD with no categories and zero total`() =
        runTest {
            stub(
                expenses = listOf(pesosExpense("cat-market", "100000.00")),
                accounts = listOf(pesosAccount, dollarsAccount),
            )
            val report = reportOf(october, Currency.USD)
            assertEquals(Currency.USD, report.currency)
            assertTrue(report.categories.isEmpty())
            assertEquals(dollars("0"), report.total)
        }

    @Test
    fun `given a USD account with no USD spending, when reporting, then USD is still offered`() = runTest {
        stub(
            expenses = listOf(pesosExpense("cat-market", "100000.00")),
            accounts = listOf(pesosAccount, dollarsAccount),
        )
        val report = reportOf(october, null)
        assertEquals(listOf(Currency.COP, Currency.USD), report.currencies)
    }

    @Test
    fun `given a category with no spending, when reporting, then it is not listed`() = runTest {
        stub(expenses = listOf(pesosExpense("cat-market", "100000.00")))
        val report = reportOf(october, null)
        assertEquals(listOf("Mercado"), report.categories.map { it.name })
    }

    @Test
    fun `given equal totals with case and accents, when reporting, then they are ordered alphabetically`() =
        runTest {
            val transportCategory = CategoryBuilder().id("cat-transport").name("Transporte").build()
            val poplarCategory = CategoryBuilder().id("cat-poplar").name("Álamo").build()
            val foodCategory = CategoryBuilder().id("cat-food").name("comida").build()
            stub(
                expenses = listOf(
                    pesosExpense("cat-transport", "100000.00"),
                    pesosExpense("cat-poplar", "100000.00"),
                    pesosExpense("cat-food", "100000.00"),
                ),
                categories = listOf(transportCategory, poplarCategory, foodCategory),
            )
            val report = reportOf(october, null)
            assertEquals(listOf("Álamo", "comida", "Transporte"), report.categories.map { it.name })
        }

    @Test
    fun `given equal totals with ñ, when reporting, then ñ is ordered right after n`() = runTest {
        val shoesCategory = CategoryBuilder().id("cat-shoes").name("Zapatos").build()
        val yamCategory = CategoryBuilder().id("cat-yam").name("Ñame").build()
        val fridgeCategory = CategoryBuilder().id("cat-fridge").name("Nevera").build()
        stub(
            expenses = listOf(
                pesosExpense("cat-shoes", "100000.00"),
                pesosExpense("cat-yam", "100000.00"),
                pesosExpense("cat-fridge", "100000.00"),
            ),
            categories = listOf(shoesCategory, yamCategory, fridgeCategory),
        )
        val report = reportOf(october, null)
        assertEquals(listOf("Nevera", "Ñame", "Zapatos"), report.categories.map { it.name })
    }

    @Test
    fun `given spending in two categories, when reporting, then each category carries its own color`() = runTest {
        stub(
            expenses = listOf(pesosExpense("cat-rent", "200000.00"), pesosExpense("cat-market", "100000.00")),
        )
        val report = reportOf(october, null)
        assertEquals(listOf("#3B82F6", "#F97316"), report.categories.map { it.color })
        assertEquals(listOf("cat-rent", "cat-market"), report.categories.map { it.categoryId })
    }

    @Test
    fun `given shares halfway between whole percentages, when reporting, then each rounds up on its own`() =
        runTest {
            stubRoundingSpending()
            val report = reportOf(october, null)
            assertEquals(
                listOf(DisplayedShare.Whole(67), DisplayedShare.Whole(17), DisplayedShare.Whole(17)),
                report.categories.map { it.displayedShare }
            )
        }

    @Test
    fun `given a share below one percent, when reporting, then it is shown as below one percent`() = runTest {
        val sweetsCategory = CategoryBuilder().id("cat-sweets").name("Dulces").build()
        stub(
            expenses = listOf(pesosExpense("cat-rent", "1000000.00"), pesosExpense("cat-sweets", "3000.00")),
            categories = listOf(rentCategory, sweetsCategory),
        )
        val report = reportOf(october, null)
        val sweetsSpending = report.categories.single { it.name == "Dulces" }
        assertEquals(pesos("3000.00"), sweetsSpending.total)
        assertEquals(DisplayedShare.BelowOnePercent, sweetsSpending.displayedShare)
    }

    @Test
    fun `given spending in several categories, when reporting, then the exact shares add up to one`() = runTest {
        stubRoundingSpending()
        val report = reportOf(october, null)
        val shares = report.categories.map { it.share }
        assertEquals(0, BigDecimal("0.165").compareTo(shares[1]))
        assertEquals(0, BigDecimal.ONE.compareTo(shares.fold(BigDecimal.ZERO) { sum, share -> sum.add(share) }))
    }

    @Test
    fun `given the expenses cannot be read, when reporting, then fails`() = runTest {
        val failure = Outcome.Failure(StorageError("expenses failed"))
        stub(expenses = emptyList())
        every { spendingLister.list(any(), any()) } returns flowOf(failure)
        val result = spendingReporter.report(october, null).first()
        assertEquals(failure, result)
    }

    @Test
    fun `given the categories cannot be read, when reporting, then fails`() = runTest {
        val failure = Outcome.Failure(StorageError("categories failed"))
        stub(expenses = emptyList())
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(failure)
        val result = spendingReporter.report(october, null).first()
        assertEquals(failure, result)
    }

    @Test
    fun `given the accounts cannot be read, when reporting, then fails`() = runTest {
        val failure = Outcome.Failure(StorageError("accounts failed"))
        stub(expenses = emptyList())
        every { accountLister.list() } returns flowOf(failure)
        val result = spendingReporter.report(october, null).first()
        assertEquals(failure, result)
    }

    @Test
    fun `given an expense whose category is missing, when reporting, then fails with a storage error`() = runTest {
        stub(expenses = listOf(pesosExpense("cat-unknown", "100000.00")))
        val result = spendingReporter.report(october, null).first()
        assertTrue((result as Outcome.Failure).error is StorageError)
    }

    @Test
    fun `given the report being observed, when the expenses change, then emits again`() = runTest {
        val expensesFlow = MutableStateFlow<Outcome<List<Expense>>>(
            Outcome.Success(listOf(pesosExpense("cat-market", "100000.00")))
        )
        stub(expenses = emptyList())
        every { spendingLister.list(any(), any()) } returns expensesFlow
        spendingReporter.report(october, null).test {
            assertEquals(pesos("100000.00"), (awaitItem() as Outcome.Success).value.total)
            expensesFlow.value = Outcome.Success(
                listOf(pesosExpense("cat-market", "100000.00"), pesosExpense("cat-market", "50000.00"))
            )
            assertEquals(pesos("150000.00"), (awaitItem() as Outcome.Success).value.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun stub(
        expenses: List<Expense>,
        categories: List<Category> = listOf(
            this.rentCategory,
            this.marketCategory,
            this.leisureCategory,
            this.travelCategory,
        ),
        accounts: List<Account> = listOf(this.pesosAccount),
    ) {
        every { spendingLister.list(any(), any()) } returns flowOf(Outcome.Success(expenses))
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(Outcome.Success(categories))
        every { accountLister.list() } returns flowOf(Outcome.Success(accounts))
    }

    private fun stubTwoCurrencies() {
        this.stub(
            expenses = listOf(
                this.pesosExpense("cat-market", "1250000.00"),
                ExpenseBuilder().accountId("acc-usd").categoryId("cat-travel").amount("120.00", Currency.USD).build(),
            ),
            accounts = listOf(this.pesosAccount, this.dollarsAccount),
        )
    }

    private fun stubRoundingSpending() {
        this.stub(
            expenses = listOf(
                this.pesosExpense("cat-rent", "134000.00"),
                this.pesosExpense("cat-market", "33000.00"),
                this.pesosExpense("cat-leisure", "33000.00"),
            ),
        )
    }

    private suspend fun reportOf(period: ReportPeriod, chosenCurrency: Currency?): SpendingReport =
        (this.spendingReporter.report(period, chosenCurrency).first() as Outcome.Success).value

    private fun pesosExpense(categoryId: String, amount: String): Expense =
        ExpenseBuilder().accountId("acc-cop").categoryId(categoryId).amount(amount).build()

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)

    private fun dollars(amount: String): Money = Money.of(BigDecimal(amount), Currency.USD)

    private fun euros(amount: String): Money = Money.of(BigDecimal(amount), Currency.EUR)
}
