package dev.raiseexception.odin.accounting.presentation.categorydetail

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.CategoryFinder
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.formatFullSpanishDate
import dev.raiseexception.odin.shared.presentation.launchObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CategoryDetailViewModel(
    private val categoryId: String,
    private val categoryFinder: CategoryFinder,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CategoryDetailUiState>(CategoryDetailUiState.Loading)
    val uiState: StateFlow<CategoryDetailUiState> = this.mutableUiState.asStateFlow()

    private val restartableLoad = RestartableLoad { this.observeCategory() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = CategoryDetailUiState.Loading
        this.restartableLoad.start()
    }

    private fun observeCategory(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = this.categoryFinder.find(
                this.categoryId
            ).map { outcome -> this.toUiState(outcome) }.flowOn(this.ioDispatcher),
            onEach = { state -> this.mutableUiState.value = state },
            onUnexpectedFailure = { this.mutableUiState.value = CategoryDetailUiState.UnexpectedFailure }
        )

    private fun toUiState(outcome: Outcome<Category>): CategoryDetailUiState = when (outcome) {
        is Outcome.Success -> {
            val category = outcome.value
            val localDate = category.createdAt.toLocalDateTime(this.timeZone).date
            CategoryDetailUiState.Content(
                name = category.name,
                type = category.type,
                description = category.description,
                color = category.color,
                formattedCreatedAt = formatFullSpanishDate(localDate)
            )
        }
        is Outcome.Failure -> when (outcome.error) {
            is CategoryLookupError.NotFound -> CategoryDetailUiState.NotFound
            else -> CategoryDetailUiState.Error(outcome.error.externalMessage)
        }
    }

    private companion object {
        const val LOAD_OPERATION = "category-detail.load"
    }
}
