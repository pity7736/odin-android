@file:Suppress("TooManyFunctions")

package dev.raiseexception.odin.accounting.presentation.categorieslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.ui.theme.OrangePrimary
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800
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
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
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
        bottomBar = {
            OdinBottomBar(
                selectedTab = BottomBarTab.CATEGORIES,
                onNavigateToHome = onNavigateToHome,
                onNavigateToAccounts = onNavigateToAccounts,
                onNavigateToCategories = onNavigateToCategories,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCategory,
                containerColor = OrangePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_category_fab"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Crear categoría",
                )
            }
        },
    ) { innerPadding ->
        when (uiState) {
            is CategoriesListUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is CategoriesListUiState.Error -> ErrorContent(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is CategoriesListUiState.Empty,
            is CategoriesListUiState.Content -> SearchableContent(
                uiState = uiState,
                onFilterChanged = onFilterChanged,
                onSearchQueryChanged = onSearchQueryChanged,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        FilterRow(
            activeFilter = activeFilter,
            onFilterChanged = onFilterChanged,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = localSearchQuery,
            onValueChange = { newValue ->
                localSearchQuery = newValue
                onSearchQueryChanged(newValue)
            },
            placeholder = {
                Text(
                    text = "Buscar categoría",
                    color = Slate400,
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Slate200,
                focusedBorderColor = Slate200,
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field"),
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (uiState) {
            is CategoriesListUiState.Empty -> EmptyMessage(
                activeFilter = uiState.activeFilter,
                modifier = Modifier.fillMaxSize(),
            )
            is CategoriesListUiState.Content -> CategoryList(
                categories = uiState.categories,
                onCategorySelected = onCategorySelected,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Unit
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyMessage(activeFilter: CategoryType?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (activeFilter != null) "No hay categorías de ese tipo" else "No hay categorías registradas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("empty_categories_message"),
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
        item(key = "categories_card_start") {
            Spacer(modifier = Modifier)
        }
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            val rowBackground = if (index % 2 == 0) Color.White else Slate50
            CategoryRow(
                category = category,
                backgroundColor = rowBackground,
                isFirst = index == 0,
                isLast = index == categories.lastIndex,
                onClick = { onCategorySelected(category.id) },
            )
        }
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilterRow(
    activeFilter: CategoryType?,
    onFilterChanged: (CategoryType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipItem(
            label = "Todas",
            selected = activeFilter == null,
            onClick = { onFilterChanged(null) },
        )
        FilterChipItem(
            label = "Ingresos",
            selected = activeFilter == CategoryType.INCOME,
            onClick = { onFilterChanged(CategoryType.INCOME) },
        )
        FilterChipItem(
            label = "Gastos",
            selected = activeFilter == CategoryType.EXPENSE,
            onClick = { onFilterChanged(CategoryType.EXPENSE) },
        )
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Slate800 else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Slate200),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Slate50 else Slate500,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun CategoryRow(
    category: Category,
    backgroundColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(14.dp)
        isFirst -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        isLast -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        else -> RoundedCornerShape(0.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parseColor(category.color)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capitalizeFirst(category.name),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Slate800,
            )
            Text(
                text = when (category.type) {
                    CategoryType.INCOME -> "Ingreso"
                    CategoryType.EXPENSE -> "Gasto"
                    CategoryType.TRANSFER -> "Transferencia"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
    Color.Gray
}
