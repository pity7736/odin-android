package dev.raiseexception.odin.accounting.presentation.categorieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesListViewModel(
    private val categoryLister: CategoryLister,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CategoriesListUiState>(CategoriesListUiState.Loading)
    val uiState: StateFlow<CategoriesListUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<CategoriesListNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<CategoriesListNavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val activeFilter = MutableStateFlow<CategoryType?>(null)
    private val searchQuery = MutableStateFlow("")

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            combine(
                this@CategoriesListViewModel.activeFilter,
                this@CategoriesListViewModel.searchQuery
            ) { filter, name -> Pair(filter, name) }
                .flatMapLatest { (filter, name) ->
                    this@CategoriesListViewModel.categoryLister.list(filter, name)
                        .map { outcome -> Triple(filter, name, outcome) }
                }
                .collect { (filter, name, outcome) ->
                    this@CategoriesListViewModel.mutableUiState.value = when (outcome) {
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
                }
        }
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
}
