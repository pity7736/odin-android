@file:Suppress("TooManyFunctions")

package dev.raiseexception.odin.reporting.presentation.spendingreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.reporting.application.usecase.CategorySpending
import dev.raiseexception.odin.reporting.application.usecase.DisplayedShare
import dev.raiseexception.odin.reporting.application.usecase.ReportPeriod
import dev.raiseexception.odin.reporting.application.usecase.SpendingReport
import dev.raiseexception.odin.reporting.presentation.DonutChart
import dev.raiseexception.odin.reporting.presentation.DonutSlice
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.DateRangePickerField
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800
import kotlinx.datetime.LocalDate

private const val MINIMUM_CURRENCIES_FOR_CHOICE = 2
private const val DONUT_SIZE_DP = 180

@Suppress("LongParameterList")
@Composable
fun SpendingReportScreen(
    uiState: SpendingReportUiState,
    onPeriodSelected: (LocalDate, LocalDate) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToReports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate50,
        bottomBar = {
            OdinBottomBar(
                selectedTab = BottomBarTab.REPORTS,
                onNavigateToHome = onNavigateToHome,
                onNavigateToAccounts = onNavigateToAccounts,
                onNavigateToCategories = onNavigateToCategories,
                onNavigateToReports = onNavigateToReports,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Gastos por categoría",
                style = MaterialTheme.typography.headlineLarge,
                color = Slate800,
                modifier = Modifier.testTag("report_title"),
            )
            Spacer(modifier = Modifier.height(16.dp))
            when (uiState) {
                is SpendingReportUiState.Loading -> LoadingContent()
                is SpendingReportUiState.Error -> ErrorContent(uiState.message)
                is SpendingReportUiState.Content -> ReportContent(
                    report = uiState.report,
                    onPeriodSelected = onPeriodSelected,
                    onCurrencySelected = onCurrencySelected,
                )
                is SpendingReportUiState.Empty -> EmptyContent(
                    uiState = uiState,
                    onPeriodSelected = onPeriodSelected,
                    onCurrencySelected = onCurrencySelected,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportContent(
    report: SpendingReport,
    onPeriodSelected: (LocalDate, LocalDate) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
) {
    ReportFilters(
        period = report.period,
        currencies = report.currencies,
        currency = report.currency,
        onPeriodSelected = onPeriodSelected,
        onCurrencySelected = onCurrencySelected,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        DonutChart(
            slices = report.categories.map { DonutSlice(it.share, parseColor(it.color)) },
            modifier = Modifier
                .size(DONUT_SIZE_DP.dp)
                .align(Alignment.CenterHorizontally)
                .testTag("donut_chart"),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TOTAL GASTADO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.testTag("total_spent_label"),
                )
                Text(
                    text = formatMoney(report.total),
                    style = MaterialTheme.typography.titleLarge,
                    color = Slate800,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("total_spent"),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        report.categories.forEachIndexed { index, categorySpending ->
            if (index > 0) HorizontalDivider(color = Slate100)
            CategoryLine(categorySpending)
        }
    }
}

@Composable
private fun EmptyContent(
    uiState: SpendingReportUiState.Empty,
    onPeriodSelected: (LocalDate, LocalDate) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
) {
    ReportFilters(
        period = uiState.period,
        currencies = uiState.currencies,
        currency = uiState.currency,
        onPeriodSelected = onPeriodSelected,
        onCurrencySelected = onCurrencySelected,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sin gastos en ${uiState.currency.name} en estas fechas.",
            style = MaterialTheme.typography.titleMedium,
            color = Slate800,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("empty_report_message"),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Cambia el rango de fechas para ver tus gastos por categoría.",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("empty_report_hint"),
        )
    }
}

@Composable
private fun ReportFilters(
    period: ReportPeriod,
    currencies: List<Currency>,
    currency: Currency,
    onPeriodSelected: (LocalDate, LocalDate) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
) {
    DateRangePickerField(start = period.start, end = period.end, onRangeSelected = onPeriodSelected)
    if (currencies.size >= MINIMUM_CURRENCIES_FOR_CHOICE) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            currencies.forEach { offeredCurrency ->
                CurrencyChip(
                    currency = offeredCurrency,
                    selected = offeredCurrency == currency,
                    onClick = { onCurrencySelected(offeredCurrency) },
                )
            }
        }
    }
}

@Composable
private fun CurrencyChip(currency: Currency, selected: Boolean, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Slate800 else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Slate200),
        modifier = Modifier.testTag("currency_chip_${currency.name}"),
    ) {
        Text(
            text = currency.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Slate50 else Slate500,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CategoryLine(categorySpending: CategorySpending) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("category_line_${categorySpending.categoryId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parseColor(categorySpending.color))
                .testTag("category_color_${categorySpending.categoryId}"),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = capitalizeFirst(categorySpending.name),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatMoney(categorySpending.total),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Slate800,
                modifier = Modifier.testTag("category_total_${categorySpending.categoryId}"),
            )
            Text(
                text = shareText(categorySpending.displayedShare),
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
                modifier = Modifier.testTag("category_share_${categorySpending.categoryId}"),
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("report_loading"),
        )
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = ExpenseRed,
            modifier = Modifier.testTag("report_error"),
        )
    }
}

private fun shareText(displayedShare: DisplayedShare): String = when (displayedShare) {
    is DisplayedShare.Whole -> "${displayedShare.percent}%"
    is DisplayedShare.BelowOnePercent -> "<1%"
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
    Color.Gray
}
