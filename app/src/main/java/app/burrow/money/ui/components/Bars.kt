package app.burrow.money.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.NavLabelMinSize
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/*
 * App chrome (docs/COMPONENTS.md § Top bar, § Bottom nav): the same on every tab, so titles,
 * buttons and the nav sit in exactly the same place everywhere.
 */

/**
 * Top bar of a tab: 64 dp, the `headline` title 24 dp from the start, up to two
 * [TonalIconButton]s at the end, 8 dp apart.
 */
@Composable
fun TabTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Size.topBar).padding(start = Space.barStart, end = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.inline),
    ) {
        Text(
            title,
            style = BurrowType.headline,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/**
 * The frame of every tab screen: status bar space, [TabTopBar], then [content] filling the rest.
 * [content] gets the screen margin to use as its content padding, so lists scroll edge to edge
 * while their items keep the 16 dp margin. The bottom nav lives outside, in the nav host.
 */
@Composable
fun TabScreen(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        TabTopBar(title, actions = actions)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            content(PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.screen))
        }
    }
}

/** One bottom-nav destination: the regular icon, and the fill icon shown while selected. */
@Immutable
data class NavItem(val label: String, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int)

/**
 * Bottom nav: 80 dp on `surfaceContainerLow`, five items; the selected one gets its fill icon in a
 * `secondaryContainer` pill. Labels shrink to fit long translations (never below 10 sp) and are
 * capped at 1.3× font scale, since the bar can't grow.
 */
@Composable
fun BurrowNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    NavigationBar(modifier, containerColor = c.surfaceContainerLow) {
        items.forEachIndexed { i, item ->
            val isSelected = i == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(i) },
                icon = {
                    Icon(
                        painterResource(if (isSelected) item.selectedIcon else item.icon),
                        contentDescription = null,
                        modifier = Modifier.size(Size.icon),
                    )
                },
                label = {
                    CappedFontScale {
                        BasicText(
                            item.label,
                            style = BurrowType.label.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = NavLabelMinSize, maxFontSize = BurrowType.label.fontSize),
                        )
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.onSecondaryContainer,
                    selectedTextColor = c.onSurface,
                    indicatorColor = c.secondaryContainer,
                    unselectedIconColor = c.onSurfaceVariant,
                    unselectedTextColor = c.onSurfaceVariant,
                ),
            )
        }
    }
}
