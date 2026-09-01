package dev.raiseexception.odin.accounting.domain.model

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal

@Suppress("LongParameterList")
class Account private constructor(
    val id: String,
    val name: String,
    val initialBalance: Money,
    val type: AccountType,
    val description: String,
    val createdAt: Instant,
    incomes: List<Income> = emptyList(),
    expenses: List<Expense> = emptyList()
) {

    private val _incomes: MutableList<Income> = incomes.toMutableList()
    val incomes: List<Income> get() = this._incomes.toList()

    private val _expenses: MutableList<Expense> = expenses.toMutableList()
    val expenses: List<Expense> get() = this._expenses.toList()

    val currency: Currency get() = this.initialBalance.currency

    val balance: Money get() {
        val incomeSum = this._incomes.fold(BigDecimal.ZERO) { acc, income -> acc.add(income.amount.amount) }
        val expenseSum = this._expenses.fold(BigDecimal.ZERO) { acc, expense -> acc.add(expense.amount.amount) }
        return Money.of(this.initialBalance.amount.add(incomeSum).subtract(expenseSum), this.initialBalance.currency)
    }

    fun createIncome(
        amount: String,
        date: String,
        categoryId: String,
        description: String,
        clock: Clock = Clock.System
    ): Outcome<Income> {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val parsedAmount = parseAmount(amount)
        val amountError = validateAmount(amount, parsedAmount)
        val (parsedDate, dateError) = parseAndValidateDate(date, today)
        val categoryError = if (categoryId.isBlank()) "La categoría es obligatoria." else null
        if (anyError(amountError, dateError, categoryError)) {
            return Outcome.Failure(
                IncomeCreationError.InvalidInput(
                    amountError = amountError,
                    dateError = dateError,
                    categoryError = categoryError
                )
            )
        }
        val income = Income(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            accountId = this.id,
            amount = Money.of(parsedAmount!!, this.currency),
            date = parsedDate!!,
            categoryId = categoryId,
            description = description.trim(),
            createdAt = clock.now()
        )
        this._incomes.add(income)
        return Outcome.Success(income)
    }

    fun createExpense(
        amount: String,
        date: String,
        categoryId: String,
        description: String,
        clock: Clock = Clock.System
    ): Outcome<Expense> {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val parsedAmount = parseAmount(amount)
        val amountError = validateAmount(amount, parsedAmount)
        val (parsedDate, dateError) = parseAndValidateDate(date, today)
        val categoryError = if (categoryId.isBlank()) "La categoría es obligatoria." else null
        if (anyError(amountError, dateError, categoryError)) {
            return Outcome.Failure(
                ExpenseCreationError.InvalidInput(
                    amountError = amountError,
                    dateError = dateError,
                    categoryError = categoryError
                )
            )
        }
        val expense = Expense(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            accountId = this.id,
            amount = Money.of(parsedAmount!!, this.currency),
            date = parsedDate!!,
            categoryId = categoryId,
            description = description.trim(),
            createdAt = clock.now()
        )
        this._expenses.add(expense)
        return Outcome.Success(expense)
    }

    private fun parseAmount(rawAmount: String): BigDecimal? = try {
        val parsed = BigDecimal(rawAmount.trim())
        if (parsed.scale() > MAX_DECIMAL_PLACES) null else parsed
    } catch (@Suppress("SwallowedException") exception: NumberFormatException) {
        null
    }

    private fun validateAmount(rawAmount: String, parsed: BigDecimal?): String? = when {
        rawAmount.isBlank() -> "El monto es obligatorio."
        parsed == null -> "El monto no es un número válido."
        parsed.signum() <= 0 -> "El monto debe ser mayor que cero."
        else -> null
    }

    private fun parseAndValidateDate(rawDate: String, today: LocalDate): Pair<LocalDate?, String?> {
        if (rawDate.isBlank()) return Pair(null, "La fecha es obligatoria.")
        val trimmed = rawDate.trim()
        val matchesFormat = trimmed.matches(Regex("""\d{4}-\d{2}-\d{2}"""))
        val parsed = try {
            LocalDate.parse(trimmed)
        } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
            val message = if (matchesFormat) "La fecha no es válida." else "Formato de fecha inválido. Usa AAAA-MM-DD."
            return Pair(null, message)
        }
        if (parsed > today) return Pair(null, "La fecha debe ser hoy o en el pasado.")
        return Pair(parsed, null)
    }

    private fun anyError(vararg errors: String?): Boolean = errors.any { it != null }

    companion object {
        private const val MAX_NAME_LENGTH = 200
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_DECIMAL_PLACES = 2

        @Suppress("LongParameterList")
        fun restore(
            id: String,
            name: String,
            initialBalance: Money,
            type: AccountType,
            description: String,
            createdAt: Instant,
            incomes: List<Income> = emptyList(),
            expenses: List<Expense> = emptyList()
        ): Account = Account(
            id = id,
            name = name,
            initialBalance = initialBalance,
            type = type,
            description = description,
            createdAt = createdAt,
            incomes = incomes,
            expenses = expenses
        )

        @Suppress("LongParameterList")
        fun create(
            name: String,
            initialBalance: String,
            currency: Currency?,
            type: AccountType?,
            description: String,
            clock: Clock = Clock.System
        ): Outcome<Account> {
            val trimmedName = name.trim()
            val trimmedDescription = description.trim()
            val amount = parseAmount(initialBalance)
            val nameError = validateName(trimmedName)
            val balanceError = validateBalance(initialBalance, amount)
            val currencyError = if (currency == null) "La moneda es obligatoria." else null
            val typeError = if (type == null) "El tipo de cuenta es obligatorio." else null
            val descriptionError = validateDescription(trimmedDescription)
            if (anyError(nameError, balanceError, currencyError, typeError, descriptionError)) {
                return Outcome.Failure(
                    AccountCreationError.InvalidInput(
                        nameError = nameError,
                        balanceError = balanceError,
                        currencyError = currencyError,
                        typeError = typeError,
                        descriptionError = descriptionError
                    )
                )
            }
            return Outcome.Success(
                Account(
                    id = UuidCreator.getTimeOrderedEpoch().toString(),
                    name = trimmedName,
                    initialBalance = Money.of(amount!!, currency!!),
                    type = type!!,
                    description = trimmedDescription,
                    createdAt = clock.now()
                )
            )
        }

        private fun parseAmount(rawBalance: String): BigDecimal? = try {
            BigDecimal(rawBalance.trim())
        } catch (@Suppress("SwallowedException") exception: NumberFormatException) {
            null
        }

        private fun validateName(trimmedName: String): String? = when {
            trimmedName.isBlank() -> "El nombre es obligatorio."
            trimmedName.length > MAX_NAME_LENGTH -> "El nombre no puede superar los $MAX_NAME_LENGTH caracteres."
            else -> null
        }

        private fun validateBalance(rawBalance: String, amount: BigDecimal?): String? = when {
            rawBalance.isBlank() -> "El saldo inicial es obligatorio."
            amount == null -> "El saldo inicial no es un número válido."
            amount.signum() < 0 -> "El saldo inicial no puede ser negativo."
            amount.scale() > MAX_DECIMAL_PLACES ->
                "El saldo inicial admite máximo $MAX_DECIMAL_PLACES decimales."
            else -> null
        }

        private fun validateDescription(trimmedDescription: String): String? = when {
            trimmedDescription.length > MAX_DESCRIPTION_LENGTH ->
                "La descripción no puede superar los $MAX_DESCRIPTION_LENGTH caracteres."
            else -> null
        }

        private fun anyError(vararg errors: String?): Boolean = errors.any { it != null }
    }
}
