package dev.raiseexception.odin.accounting.presentation.categorieslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

@Suppress("LongParameterList")
@Composable
fun CategoriesListScreen(
    uiState: CategoriesListUiState,
    navigationEvent: Flow<CategoriesListNavigationTarget>,
    onCreateCategory: () -> Unit,
    onFilterChanged: (CategoryType?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onNavigateToCategoryDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is CategoriesListNavigationTarget.CategoryDetail ->
                    onNavigateToCategoryDetail(target.categoryId)
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCategory,
                modifier = Modifier.testTag("create_category_fab")
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        when (uiState) {
            is CategoriesListUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is CategoriesListUiState.Error -> ErrorContent(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is CategoriesListUiState.Empty,
            is CategoriesListUiState.Content -> SearchableContent(
                uiState = uiState,
                onFilterChanged = onFilterChanged,
                onSearchQueryChanged = onSearchQueryChanged,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun SearchableContent(
    uiState: CategoriesListUiState,
    onFilterChanged: (CategoryType?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeFilter = when (uiState) {
        is CategoriesListUiState.Content -> uiState.activeFilter
        is CategoriesListUiState.Empty -> uiState.activeFilter
        else -> null
    }
    var localSearchQuery by remember { mutableStateOf("") }
    Column(modifier = modifier) {
        FilterRow(
            activeFilter = activeFilter,
            onFilterChanged = onFilterChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = localSearchQuery,
            onValueChange = { newValue ->
                localSearchQuery = newValue
                onSearchQueryChanged(newValue)
            },
            placeholder = { Text("Buscar categoría") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("search_field")
        )
        when (uiState) {
            is CategoriesListUiState.Empty -> EmptyMessage(
                activeFilter = uiState.activeFilter,
                modifier = Modifier.fillMaxSize()
            )
            is CategoriesListUiState.Content -> CategoryList(
                categories = uiState.categories,
                onCategorySelected = onCategorySelected,
                modifier = Modifier.fillMaxSize()
            )
            else -> Unit
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyMessage(activeFilter: CategoryType?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (activeFilter != null) "No hay categorías de ese tipo" else "No hay categorías registradas",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("empty_categories_message")
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(categories, key = { it.id }) { category ->
            CategoryRow(category = category, onClick = { onCategorySelected(category.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun FilterRow(
    activeFilter: CategoryType?,
    onFilterChanged: (CategoryType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        FilterChip(
            selected = activeFilter == null,
            onClick = { onFilterChanged(null) },
            label = { Text("Todas") },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = activeFilter == CategoryType.INCOME,
            onClick = { onFilterChanged(CategoryType.INCOME) },
            label = { Text("Ingresos") },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = activeFilter == CategoryType.EXPENSE,
            onClick = { onFilterChanged(CategoryType.EXPENSE) },
            label = { Text("Gastos") }
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = if (category.type == CategoryType.INCOME) "Ingreso" else "Gasto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}
