package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class AppTab(val title: String, val icon: ImageVector) {
    NETWORK("Network", Icons.Default.Lan),
    PANIC_LAB("Panic Lab", Icons.Default.Shield),
    CRYPTO("Crypto", Icons.Default.VpnKey),
    STORAGE("Cache", Icons.Default.Storage),
    RUST_CODE("Rust Code", Icons.Default.Code)
}

@Composable
fun MainNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("main_navigation_bar"),
        containerColor = TgDarkSurface,
        contentColor = TgTextPrimary,
        windowInsets = WindowInsets.navigationBars
    ) {
        AppTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 10.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TgCyanPrimary,
                    selectedTextColor = TgCyanPrimary,
                    unselectedIconColor = TgTextMuted,
                    unselectedTextColor = TgTextMuted,
                    indicatorColor = TgCyanPrimary.copy(alpha = 0.15f)
                ),
                modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
            )
        }
    }
}
