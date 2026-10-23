package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.application.usecase.GmfInput
import dev.raiseexception.odin.accounting.application.usecase.TagLister
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.TagSelection
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.amountInputToRaw
import dev.raiseexception.odin.shared.presentation.launchObservation
import dev.raiseexception.odin.shared.presentation.launchOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class CreateAccountViewModel(
    private val accountCreator: AccountCreator,
    private val categoryLister: CategoryLister,
    private val tagLister: TagLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateAccountUiState>(CreateAccountUiState.Loading)
    val uiState: StateFlow<CreateAccountUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = CreateAccountUiState.Loading
        this.restartableLoad.start()
    }

    fun onTypeSelected(type: AccountType) {
        this.updateForm { form -> form.copy(type = type) }
    }

    fun onCurrencySelected(currency: Currency) {
        this.updateForm { form -> form.copy(currency = currency) }
    }

    fun onGmfExemptChanged(gmfExempt: Boolean) {
        this.updateForm { form -> form.copy(gmfExempt = gmfExempt) }
    }

    fun onTagTextChange(text: String) {
        this.updateForm { form -> form.copy(tagSelection = form.tagSelection.withText(text)) }
    }

    fun addTag() {
        this.updateForm { form -> form.copy(tagSelection = form.tagSelection.withTextAdded(form.tags)) }
    }

    fun pickTag(tag: Tag) {
        this.updateForm { form -> form.copy(tagSelection = form.tagSelection.withTagPicked(tag)) }
    }

    fun removeTag(index: Int) {
        this.updateForm { form -> form.copy(tagSelection = form.tagSelection.withTagRemoved(index)) }
    }

    fun create(fields: AccountFormFields) {
        val stateBeforeSaving = this.mutableUiState.value
        val currentForm = this.currentForm() ?: return
        val chargesGmf = currentForm.showsGmfOption && !currentForm.gmfExempt
        val form = if (chargesGmf) {
            currentForm.copy(tagSelection = currentForm.tagSelection.withTextAdded(currentForm.tags))
        } else {
            currentForm
        }
        if (chargesGmf && form.tagSelection.error != null) {
            this.mutableUiState.value = CreateAccountUiState.ValidationError(form)
            return
        }
        this.mutableUiState.value = CreateAccountUiState.Saving(form)
        val command = this.command(fields, form, chargesGmf)
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = { withContext(this.ioDispatcher) { accountCreator.create(command) } },
            onResult = { outcome -> this.handle(outcome, form) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private fun command(fields: AccountFormFields, form: AccountForm, chargesGmf: Boolean): CreateAccountCommand =
        when (form.type) {
            AccountType.CREDIT_CARD -> CreateAccountCommand.CreditCard(
                name = fields.name,
                creditLimit = amountInputToRaw(fields.creditLimit),
                existingDebt = amountInputToRaw(fields.debt),
                currency = form.currency,
                description = fields.description
            )
            AccountType.SAVINGS, AccountType.CASH, null -> CreateAccountCommand.MoneyAccount(
                name = fields.name,
                balance = amountInputToRaw(fields.balance),
                currency = form.currency,
                kind = form.type?.toMoneyAccountKind(),
                description = fields.description,
                gmf = if (chargesGmf) {
                    GmfInput.Taxed(fields.gmfCategoryInput, form.tagSelection.selected.map { it.name })
                } else {
                    GmfInput.Exempt
                }
            )
        }

    private suspend fun handle(outcome: Outcome<Account>, form: AccountForm) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(NavigationTarget.AccountsList)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error, form)
        }
    }

    private fun mapError(error: DomainError, form: AccountForm): CreateAccountUiState = when (error) {
        is AccountCreationError.InvalidInput -> CreateAccountUiState.ValidationError(
            form = form.copy(tagSelection = form.tagSelection.copy(error = error.gmfTagsError)),
            nameError = error.nameError,
            balanceError = error.balanceError,
            currencyError = error.currencyError,
            typeError = error.typeError,
            descriptionError = error.descriptionError,
            creditLimitError = error.creditLimitError,
            debtError = error.debtError,
            gmfCategoryError = error.gmfCategoryError
        )
        is AccountCreationError.DuplicateName -> CreateAccountUiState.ValidationError(
            form = form,
            nameError = error.externalMessage
        )
        else -> CreateAccountUiState.SaveFailed(form, error.externalMessage)
    }

    private fun updateForm(update: (AccountForm) -> AccountForm) {
        when (val current = this.mutableUiState.value) {
            is CreateAccountUiState.Idle -> this.mutableUiState.value = current.copy(form = update(current.form))
            is CreateAccountUiState.ValidationError ->
                this.mutableUiState.value = current.copy(form = update(current.form))
            is CreateAccountUiState.SaveFailed -> this.mutableUiState.value = current.copy(form = update(current.form))
            else -> Unit
        }
    }

    private fun currentForm(): AccountForm? = when (val current = this.mutableUiState.value) {
        is CreateAccountUiState.Idle -> current.form
        is CreateAccountUiState.ValidationError -> current.form
        is CreateAccountUiState.SaveFailed -> current.form
        else -> null
    }

    private fun load(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(loadedState()) }.flowOn(this.ioDispatcher),
            onEach = { loaded -> this.mutableUiState.value = loaded },
            onUnexpectedFailure = { this.mutableUiState.value = CreateAccountUiState.UnexpectedFailure }
        )

    private suspend fun loadedState(): CreateAccountUiState {
        val categoriesOutcome = this.categoryLister.list(CategoryType.EXPENSE, "").first()
        val tagsOutcome = this.tagLister.list().first()
        return when {
            categoriesOutcome is Outcome.Failure -> CreateAccountUiState.Error(categoriesOutcome.error.externalMessage)
            tagsOutcome is Outcome.Failure -> CreateAccountUiState.Error(tagsOutcome.error.externalMessage)
            else -> CreateAccountUiState.Idle(
                AccountForm(
                    categories = (categoriesOutcome as Outcome.Success).value,
                    tags = (tagsOutcome as Outcome.Success).value,
                    tagSelection = TagSelection()
                )
            )
        }
    }

    private fun AccountType.toMoneyAccountKind(): MoneyAccountKind? = when (this) {
        AccountType.SAVINGS -> MoneyAccountKind.SAVINGS
        AccountType.CASH -> MoneyAccountKind.CASH
        AccountType.CREDIT_CARD -> null
    }

    private companion object {
        const val LOAD_OPERATION = "account-creation.load"
        const val SUBMIT_OPERATION = "account-creation.submit"
    }
}
