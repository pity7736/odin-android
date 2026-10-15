package dev.raiseexception.odin.accounting.presentation.categorieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.launchObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesListViewModel(
    private val categoryLister: CategoryLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CategoriesListUiState>(CategoriesListUiState.Loading)
    val uiState: StateFlow<CategoriesListUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<CategoriesListNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<CategoriesListNavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val activeFilter = MutableStateFlow<CategoryType?>(null)
    private val searchQuery = MutableStateFlow("")

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = CategoriesListUiState.Loading
        this.restartableLoad.start()
    }

    fun onFilterChanged(filter: CategoryType?) {
        this.activeFilter.value = filter
    }

    fun onSearchQueryChanged(name: String) {
        this.searchQuery.value = name
    }

    fun onCategorySelected(categoryId: String) {
        this.viewModelScope.launch {
            this@CategoriesListViewModel.navigationChannel.send(
                CategoriesListNavigationTarget.CategoryDetail(categoryId)
            )
        }
    }

    private fun load(): Job {
        val categoriesFlow = combine(this.activeFilter, this.searchQuery) { filter, name -> Pair(filter, name) }
            .flatMapLatest { (filter, name) ->
                this.categoryLister.list(filter, name).map { outcome -> this.toUiState(filter, name, outcome) }
            }
            .flowOn(this.ioDispatcher)
        return this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = categoriesFlow,
            onEach = { state -> this.mutableUiState.value = state },
            onUnexpectedFailure = { this.mutableUiState.value = CategoriesListUiState.UnexpectedFailure }
        )
    }

    private fun toUiState(
        filter: CategoryType?,
        name: String,
        outcome: Outcome<List<Category>>
    ): CategoriesListUiState = when (outcome) {
        is Outcome.Success -> if (outcome.value.isEmpty()) {
            CategoriesListUiState.Empty(filter, name)
        } else {
            CategoriesListUiState.Content(
                categories = outcome.value,
                activeFilter = filter,
                searchQuery = name,
            )
        }
        is Outcome.Failure -> CategoriesListUiState.Error("Error al cargar las categorías")
    }

    private companion object {
        const val LOAD_OPERATION = "categories-list.load"
    }
}
