package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.ui.theme.Slate600
import java.math.BigDecimal

fun gmfTaxLineOf(gmfTax: BigDecimal, currency: Currency): String? =
    if (gmfTax.signum() > 0) "4x1000: ${formatMoney(Money.of(gmfTax, currency))}" else null

fun gmfTaxLineFor(account: Account?, amountInput: String): String? =
    account?.let { gmfTaxLineOf(it.gmfTaxOn(amountInputToRaw(amountInput)), it.currency) }

fun amountInputToNumber(amountInput: String): BigDecimal? =
    amountInputToRaw(amountInput).toBigDecimalOrNull()

@Composable
fun GmfTaxLine(gmfTaxLine: String?) {
    if (gmfTaxLine != null) {
        Text(
            text = gmfTaxLine,
            style = MaterialTheme.typography.bodySmall,
            color = Slate600,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("gmf_tax_line"),
        )
    }
}
