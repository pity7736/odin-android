package dev.raiseexception.odin.accounting.presentation.categorydetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.SoraFamily

private val categoryTypeLabels = mapOf(
    CategoryType.INCOME to "Ingreso",
    CategoryType.EXPENSE to "Gasto",
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
fun CategoryDetailScreen(
    uiState: CategoryDetailUiState,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Detalle de categoría",
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is CategoryDetailUiState.Loading -> CategoryDetailLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is CategoryDetailUiState.Content -> CategoryDetailContent(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is CategoryDetailUiState.NotFound -> CategoryDetailMessage(
                message = "Categoría no encontrada",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is CategoryDetailUiState.Error -> CategoryDetailMessage(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun CategoryDetailLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("loading_indicator"),
        )
    }
}

@Composable
private fun CategoryDetailContent(uiState: CategoryDetailUiState.Content, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        CategoryHeaderCard(uiState = uiState)
        Spacer(modifier = Modifier.height(16.dp))
        CategoryInfoSection(uiState = uiState)
    }
}

@Composable
private fun CategoryHeaderCard(uiState: CategoryDetailUiState.Content, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Slate800)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(parseColor(uiState.color))
                    .testTag("color_dot"),
            )
            Text(
                text = uiState.name,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = SoraFamily,
                color = Slate50,
            )
        }
        Text(
            text = categoryTypeLabels[uiState.type] ?: uiState.type.name,
            style = MaterialTheme.typography.labelLarge,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CategoryInfoSection(uiState: CategoryDetailUiState.Content, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (uiState.description.isNotEmpty()) {
            Text(
                text = "DESCRIPCIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = Slate400,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = uiState.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Slate800,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("description_text"),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = "FECHA DE CREACIÓN",
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = uiState.formattedCreatedAt,
            style = MaterialTheme.typography.bodyLarge,
            color = Slate600,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CategoryDetailMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun parseColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(hex))
