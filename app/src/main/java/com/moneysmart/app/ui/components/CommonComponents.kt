package com.moneysmart.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionSource
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.remote.AppUpdateChecker
import com.moneysmart.app.ui.theme.ApricotHero
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.EspressoInk
import com.moneysmart.app.ui.theme.ExpenseRed
import com.moneysmart.app.ui.theme.ExpenseRedDark
import com.moneysmart.app.ui.theme.HoneyGold
import com.moneysmart.app.ui.theme.IncomeGreen
import com.moneysmart.app.ui.theme.IncomeGreenDark
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.theme.PeachTint
import com.moneysmart.app.ui.theme.TrackBeige
import com.moneysmart.app.ui.theme.TransferBlue
import com.moneysmart.app.ui.theme.TransferBlueDark
import com.moneysmart.app.ui.theme.WarningAmber
import com.moneysmart.app.util.FormatUtils
import java.time.YearMonth

@Composable
fun rememberIsWideScreen(): Boolean {
    return LocalConfiguration.current.screenWidthDp >= 700
}

@Composable
fun ResponsiveContentBox(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 960.dp,
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

/**
 * Bauhaus-inspired geometric vector accent inspired by the warm apricot & coral composition.
 */
@Composable
fun BauhausCornerArt(
    modifier: Modifier = Modifier,
    darkAccent: Color = EspressoInk,
    coralAccent: Color = CoralAccent,
    apricotAccent: Color = HoneyGold
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Upper-right espresso quarter circle
        drawArc(
            color = darkAccent.copy(alpha = 0.9f),
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset(w * 0.45f, -h * 0.45f),
            size = Size(w * 1.1f, h * 1.1f)
        )

        // Subtle polka-dot arc texture on the upper-left side of art
        val dotColor = darkAccent.copy(alpha = 0.22f)
        val cols = 5
        val rows = 5
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val dx = w * (0.28f + c * 0.08f)
                val dy = h * (0.14f + r * 0.11f)
                if ((dx - w * 0.45f) * (dx - w * 0.45f) + (dy - h * 0.36f) * (dy - h * 0.36f) < (w * 0.24f) * (w * 0.24f)) {
                    drawCircle(color = dotColor, radius = 1.6.dp.toPx(), center = Offset(dx, dy))
                }
            }
        }

        // Warm honey/apricot circle lower-middle
        drawCircle(
            color = apricotAccent,
            radius = w * 0.16f,
            center = Offset(w * 0.34f, h * 0.62f)
        )

        // Small crisp coral dot top-left of cluster
        drawCircle(
            color = coralAccent,
            radius = w * 0.09f,
            center = Offset(w * 0.68f, h * 0.68f)
        )

        // Delicate intersecting white/light crescent stroke
        drawCircle(
            color = Color.White.copy(alpha = 0.32f),
            radius = w * 0.13f,
            center = Offset(w * 0.50f, h * 0.48f)
        )
    }
}

/**
 * Curved warm-peach or apricot hero banner echoing the editorial job/analytics cards.
 */
@Composable
fun WarmCurvedHeroHeader(
    title: String,
    subtitle: String? = null,
    heroColor: Color = PeachSoft,
    maxWidth: Dp = 1040.dp,
    onNavigateBack: (() -> Unit)? = null,
    useLightArtOnApricot: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val effectiveHero = if (isDark) MaterialTheme.colorScheme.surfaceContainer else heroColor
    val effectiveTitleColor = if (isDark) MaterialTheme.colorScheme.onSurface else EspressoInk
    val effectiveSubtitleColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else EspressoInk.copy(alpha = 0.68f)
    val curveColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(effectiveHero)
    ) {
        // Right-aligned Bauhaus corner art inside max width frame
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
            ) {
                BauhausCornerArt(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(width = 165.dp, height = 110.dp),
                    darkAccent = when {
                        isDark -> ApricotHero.copy(alpha = 0.22f)
                        useLightArtOnApricot -> Color.White.copy(alpha = 0.26f)
                        else -> ApricotHero.copy(alpha = 0.36f)
                    },
                    coralAccent = CoralAccent.copy(alpha = if (isDark) 0.35f else 0.24f),
                    apricotAccent = if (useLightArtOnApricot) {
                        Color.White.copy(alpha = 0.36f)
                    } else {
                        HoneyGold.copy(alpha = 0.40f)
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (onNavigateBack != null) {
                                Surface(
                                    onClick = onNavigateBack,
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = effectiveTitleColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                val titleMaxLines = if (LocalDensity.current.fontScale > 1.15f) 2 else 1
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = effectiveTitleColor,
                                    maxLines = titleMaxLines,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = effectiveSubtitleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            content = actions
                        )
                    }

                    if (bottomContent != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        bottomContent()
                    }
                }
            }
        }

        // Smooth curved sheet transition at the bottom edge of the hero banner
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .align(Alignment.BottomCenter)
        ) {
            val path = Path().apply {
                moveTo(0f, size.height)
                quadraticTo(size.width * 0.04f, 0f, size.width * 0.12f, 0f)
                lineTo(size.width * 0.88f, 0f)
                quadraticTo(size.width * 0.96f, 0f, size.width, size.height)
                close()
            }
            drawPath(path = path, color = curveColor)
        }
    }
}

/**
 * Rounded-square Coral primary action button with warm elevation shadow.
 */
@Composable
fun TopBarAddButton(
    label: String = "Add",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = CoralAccent,
        contentColor = Color.White,
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            if (label.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Soft porcelain/white icon+text pill button designed to sit cleanly on top hero headers.
 */
@Composable
fun LabeledActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    val wide = rememberIsWideScreen()
    val isDark = isSystemInDarkTheme()
    val effectiveTint = when {
        tint != Color.Unspecified -> tint
        isDark -> MaterialTheme.colorScheme.onSurface
        else -> EspressoInk
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (wide) 11.dp else 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = effectiveTint,
                modifier = Modifier.size(18.dp)
            )
            if (wide) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    color = effectiveTint,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * High-contrast deep espresso pill CTA button inspired by Screen 3 ("Apply for job").
 */
@Composable
fun EspressoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) CoralAccent else EspressoInk,
            contentColor = Color.White,
            disabledContainerColor = TrackBeige,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sleek rounded pill segment switcher: beige track + sliding dark espresso active pill.
 */
@Composable
fun WarmSegmentedPillBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null
) {
    val isDark = isSystemInDarkTheme()
    val trackColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else TrackBeige
    val selectedColor = if (isDark) CoralAccent else EspressoInk
    val hideIconsForCompactFont = LocalConfiguration.current.screenWidthDp < 400 &&
        LocalDensity.current.fontScale > 1.15f &&
        options.size >= 3

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = trackColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                Surface(
                    onClick = { onSelect(idx) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) selectedColor else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 8.dp,
                                horizontal = if (hideIconsForCompactFont) 4.dp else 6.dp
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ic = if (hideIconsForCompactFont) null else icons?.getOrNull(idx)
                        if (ic != null) {
                            Icon(
                                imageVector = ic,
                                contentDescription = null,
                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SquircleIconBadge(
    icon: ImageVector,
    tint: Color,
    containerColor: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
    cornerRadius: Dp = 14.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SoftElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val resolvedColor = if (containerColor.alpha < 1f) {
        containerColor.compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        containerColor
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = resolvedColor,
            shadowElevation = 1.dp,
            border = border
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = resolvedColor,
            shadowElevation = 1.dp,
            border = border
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun MonthSelectorBar(
    yearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = FormatUtils.formatYearMonth(yearMonth),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
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
    onLongClick: (() -> Unit)? = null,
    grouped: Boolean = false
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
        else -> CoralAccent
    }

    val hasNote = transaction.note.isNotBlank()
    val title = when {
        hasNote -> transaction.note
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

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (grouped) 12.dp else 14.dp,
                    vertical = if (grouped) 9.dp else 11.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            SquircleIconBadge(
                icon = if (transaction.type == TransactionType.TRANSFER) {
                    Icons.Rounded.SwapHoriz
                } else {
                    IconCatalog.resolve(category?.iconName)
                },
                tint = badgeColor,
                containerColor = badgeColor.copy(alpha = 0.13f),
                size = if (grouped) 38.dp else 44.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (transaction.source == TransactionSource.AI_NOTIFICATION) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Recorded",
                            tint = CoralAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (transaction.needsReview) {
                        Surface(
                            color = WarningAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasNote &&
                    transaction.merchant.isNotBlank() &&
                    !transaction.merchant.equals(transaction.note, ignoreCase = true)
                ) {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
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
                    fontWeight = FontWeight.Bold
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

    if (grouped) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            rowContent()
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(18.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CoralAccent else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            rowContent()
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BauhausCornerArt(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 110.dp, height = 80.dp),
                darkAccent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f),
                coralAccent = CoralAccent.copy(alpha = 0.35f),
                apricotAccent = ApricotHero.copy(alpha = 0.45f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                    Spacer(modifier = Modifier.height(16.dp))
                    EspressoPillButton(
                        text = actionLabel,
                        icon = Icons.Rounded.Add,
                        onClick = onAction
                    )
                }
            }
        }
    }
}

@Composable
fun GeminiApiKeyGuideCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else PeachTint,
        border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = CoralAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "How to get a free Gemini API key (1 min)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "1. Open Google AI Studio and sign in with Google\n" +
                    "2. Tap \"Create API key\" (no credit card needed)\n" +
                    "3. Copy the key and paste it into Settings\n" +
                    "4. Enable Notification Access & pick your bank apps\n" +
                    "5. Turn on push alerts in your bank apps and set the minimum alert threshold (buffer) to $0.01 so small daily expenses are captured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CoralAccent.copy(alpha = 0.4f)),
                onClick = { AppUpdateChecker.openUrl(context, "https://aistudio.google.com/apikey") }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = CoralAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open Google AI Studio",
                    color = CoralAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
