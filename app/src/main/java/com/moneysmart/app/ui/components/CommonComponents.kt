package com.moneysmart.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.ExpenseRedDark
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.IncomeGreenDark
import com.moneysmart.app.ui.theme.TransferBlue
import com.moneysmart.app.ui.theme.TransferBlueDark
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.util.FormatUtils
import java.time.YearMonth

@Composable
fun MonthSelectorBar(
    yearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = FormatUtils.formatYearMonth(yearMonth),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    baseCurrency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val amountColor = when {
        transaction.isReimbursement -> if (isDark) TransferBlueDark else TransferBlue
        transaction.type == TransactionType.EXPENSE -> if (isDark) ExpenseRedDark else ExpenseRed
        transaction.type == TransactionType.INCOME -> if (isDark) IncomeGreenDark else IncomeGreen
        else -> if (isDark) TransferBlueDark else TransferBlue
    }
    val prefix = when {
        transaction.isReimbursement -> "+"
        transaction.type == TransactionType.EXPENSE -> "-"
        transaction.type == TransactionType.INCOME -> "+"
        else -> ""
    }

    val badgeColor = when {
        transaction.isReimbursement -> TransferBlue
        transaction.type == TransactionType.TRANSFER -> TransferBlue
        category != null -> Color(category.colorHex)
        else -> MaterialTheme.colorScheme.primary
    }

    val title = when {
        transaction.merchant.isNotBlank() -> transaction.merchant
        transaction.type == TransactionType.TRANSFER ->
            "${account?.name ?: "Account"} → ${toAccount?.name ?: "Account"}"
        category != null -> category.name
        else -> transaction.type.displayName
    }

    val subtitleParts = buildList {
        when {
            transaction.isReimbursement -> add("Reimbursement")
            transaction.type == TransactionType.TRANSFER -> add("Transfer")
            else -> category?.name?.let { add(it) }
        }
        if (account != null) {
            if (transaction.type == TransactionType.TRANSFER && toAccount != null) {
                add("${account.name} → ${toAccount.name}")
            } else {
                add(account.name)
            }
        }
        add(FormatUtils.formatTime(transaction.timestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (transaction.type == TransactionType.TRANSFER) {
                    Icons.Rounded.SwapHoriz
                } else {
                    IconCatalog.resolve(category?.iconName)
                },
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (transaction.source == TransactionSource.AI_NOTIFICATION) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Recorded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (transaction.needsReview) {
                    Surface(
                        color = WarningAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Needs review",
                                tint = WarningAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Review",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Text(
                text = subtitleParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (transaction.note.isNotBlank()) {
                Text(
                    text = transaction.note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            val displayAmount = if (transaction.type == TransactionType.EXPENSE && transaction.reimbursedAmount > 0.0) {
                (transaction.amount - transaction.reimbursedAmount).coerceAtLeast(0.0)
            } else {
                transaction.amount
            }
            Text(
                text = "$prefix${FormatUtils.formatMoney(displayAmount, transaction.currency)}",
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                fontWeight = FontWeight.SemiBold
            )
            if (transaction.type == TransactionType.EXPENSE && transaction.reimbursedAmount > 0.0) {
                Text(
                    text = "Paid ${FormatUtils.formatMoney(transaction.amount, transaction.currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!transaction.currency.equals(baseCurrency, ignoreCase = true)) {
                Text(
                    text = "≈ ${FormatUtils.formatMoney(transaction.baseAmount, baseCurrency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun rememberIsWideScreen(): Boolean {
    return LocalConfiguration.current.screenWidthDp >= 700
}

@Composable
fun ResponsiveContentBox(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 940.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun TopBarAddButton(
    label: String = "Add",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = label,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun LabeledActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    val wide = rememberIsWideScreen()
    val effectiveTint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    if (wide) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = effectiveTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = effectiveTint,
                style = MaterialTheme.typography.labelLarge
            )
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (tint == Color.Unspecified) LocalContentColor.current else tint
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onAction) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}
