@file:Suppress("MatchingDeclarationName")

package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.raiseexception.odin.ui.theme.OrangePrimary
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800

enum class BottomBarTab { HOME, ACCOUNTS, CATEGORIES }

@Composable
fun OdinBottomBar(
    selectedTab: BottomBarTab?,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
) {
    NavigationBar(
        containerColor = Slate800,
        windowInsets = WindowInsets(0),
    ) {
        NavigationBarItem(
            selected = selectedTab == BottomBarTab.HOME,
            onClick = onNavigateToHome,
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomBarTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Inicio",
                )
            },
            label = { Text("Inicio") },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_home"),
        )
        NavigationBarItem(
            selected = selectedTab == BottomBarTab.ACCOUNTS,
            onClick = onNavigateToAccounts,
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomBarTab.ACCOUNTS) {
                        Icons.Filled.CreditCard
                    } else {
                        Icons.Outlined.CreditCard
                    },
                    contentDescription = "Cuentas",
                )
            },
            label = { Text("Cuentas") },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_accounts"),
        )
        NavigationBarItem(
            selected = selectedTab == BottomBarTab.CATEGORIES,
            onClick = onNavigateToCategories,
            icon = {
                Icon(
                    imageVector = if (selectedTab == BottomBarTab.CATEGORIES) {
                        Icons.Filled.GridView
                    } else {
                        Icons.Outlined.GridView
                    },
                    contentDescription = "Categorías",
                )
            },
            label = { Text("Categorías") },
            colors = navItemColors(),
            modifier = Modifier.testTag("nav_categories"),
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = OrangePrimary,
    selectedTextColor = OrangePrimary,
    unselectedIconColor = Slate500,
    unselectedTextColor = Slate500,
    indicatorColor = Slate800,
)
