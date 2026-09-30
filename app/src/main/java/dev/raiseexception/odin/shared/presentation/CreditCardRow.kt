package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800

@Composable
fun CreditCardRow(
    name: String,
    debt: Money,
    availableCredit: Money,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIcon(imageVector = Icons.Filled.CreditCard)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = capitalizeFirst(name),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = debtLine(debt))
            Text(
                text = "Disponible ${formatMoney(availableCredit)}",
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
            )
        }
    }
}

private fun debtLine(debt: Money): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate500)) {
        append("Deuda ")
    }
    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Slate800)) {
        append(formatMoney(debt))
    }
}
