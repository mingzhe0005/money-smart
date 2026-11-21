package com.moneysmart.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

object IconCatalog {
    val availableIcons: List<Pair<String, ImageVector>> = listOf(
        "Restaurant" to Icons.Rounded.Restaurant,
        "LocalCafe" to Icons.Rounded.LocalCafe,
        "DirectionsCar" to Icons.Rounded.DirectionsCar,
        "ShoppingBag" to Icons.Rounded.ShoppingBag,
        "ReceiptLong" to Icons.AutoMirrored.Rounded.ReceiptLong,
        "Home" to Icons.Rounded.Home,
        "Movie" to Icons.Rounded.Movie,
        "LocalHospital" to Icons.Rounded.LocalHospital,
        "Flight" to Icons.Rounded.Flight,
        "School" to Icons.Rounded.School,
        "Pets" to Icons.Rounded.Pets,
        "Payments" to Icons.Rounded.Payments,
        "Work" to Icons.Rounded.Work,
        "TrendingUp" to Icons.AutoMirrored.Rounded.TrendingUp,
        "CardGiftcard" to Icons.Rounded.CardGiftcard,
        "Savings" to Icons.Rounded.Savings,
        "AccountBalanceWallet" to Icons.Rounded.AccountBalanceWallet,
        "AccountBalance" to Icons.Rounded.AccountBalance,
        "CreditCard" to Icons.Rounded.CreditCard,
        "Smartphone" to Icons.Rounded.Smartphone,
        "AddCircle" to Icons.Rounded.AddCircle,
        "SwapHoriz" to Icons.Rounded.SwapHoriz,
        "AutoAwesome" to Icons.Rounded.AutoAwesome,
        "MoreHoriz" to Icons.Rounded.MoreHoriz,
        "Category" to Icons.Rounded.Category
    )

    private val iconMap = availableIcons.toMap()

    fun resolve(name: String?): ImageVector = iconMap[name] ?: Icons.Rounded.Category

    val presetColorHexes = listOf(
        0xFF0D6E52L,
        0xFF2563EBL,
        0xFFDC2626L,
        0xFFF59E0BL,
        0xFF7C3AEDL,
        0xFFEC4899L,
        0xFF14B8A6L,
        0xFF6366F1L,
        0xFF0EA5E9L,
        0xFF64748BL
    )
}
