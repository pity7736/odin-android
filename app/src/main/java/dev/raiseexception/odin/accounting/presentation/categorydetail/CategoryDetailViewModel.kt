package dev.raiseexception.odin.accounting.presentation.categorydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.CategoryFinder
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CategoryDetailViewModel(
    private val categoryId: String,
    private val categoryFinder: CategoryFinder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CategoryDetailUiState>(CategoryDetailUiState.Loading)
    val uiState: StateFlow<CategoryDetailUiState> = this.mutableUiState.asStateFlow()

    init {
        this.observeCategory()
    }

    private fun observeCategory() {
        this.viewModelScope.launch {
            this@CategoryDetailViewModel.categoryFinder.find(
                this@CategoryDetailViewModel.categoryId
            ).flowOn(this@CategoryDetailViewModel.ioDispatcher).collect { outcome ->
                this@CategoryDetailViewModel.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> {
                        val category = outcome.value
                        val localDate = category.createdAt.toLocalDateTime(TimeZone.UTC).date
                        CategoryDetailUiState.Content(
                            name = category.name,
                            type = category.type,
                            description = category.description,
                            color = category.color,
                            formattedCreatedAt = this@CategoryDetailViewModel.formatDate(
                                localDate.dayOfMonth,
                                localDate.monthNumber,
                                localDate.year
                            )
                        )
                    }
                    is Outcome.Failure -> when (outcome.error) {
                        is CategoryLookupError.NotFound -> CategoryDetailUiState.NotFound
                        else -> CategoryDetailUiState.Error(outcome.error.externalMessage)
                    }
                }
            }
        }
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        val spanishMonths = arrayOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
        return "$day de ${spanishMonths[month - 1]} de $year"
    }
}
