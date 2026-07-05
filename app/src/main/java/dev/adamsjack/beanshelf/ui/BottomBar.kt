package dev.adamsjack.beanshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.adamsjack.beanshelf.Tab

private data class NavItem(val tab: Tab, val icon: ImageVector, val label: String)

private val ITEMS = listOf(
    NavItem(Tab.Feed, Icons.Default.DynamicFeed, "Feed"),
    NavItem(Tab.Shelf, Icons.Default.Storefront, "Shelf"),
    NavItem(Tab.Profile, Icons.Default.Person, "Profile"),
    NavItem(Tab.Settings, Icons.Default.Settings, "Settings"),
)

/** Simple persistent bottom bar: Feed · Shelf · Profile · Settings. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Surface2)) {
        // hairline divider in the accent's muted tone
        Row(Modifier.fillMaxWidth().height(1.dp).background(Dim.copy(alpha = 0.18f))) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ITEMS.forEach { item ->
                val selected = item.tab == current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(item.tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) Crema else Dim,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Crema else Dim,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
