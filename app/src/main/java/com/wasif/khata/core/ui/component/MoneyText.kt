package com.wasif.khata.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.theme.AmountTextStyle

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    direction: TransactionDirection? = null,
    style: TextStyle = AmountTextStyle,
) {
    val prefix = when (direction) {
        TransactionDirection.DEBIT -> "−"
        TransactionDirection.CREDIT -> "+"
        null -> ""
    }
    val color = when (direction) {
        TransactionDirection.CREDIT -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = prefix + money.format(),
        style = style,
        color = color,
        modifier = modifier,
    )
}
