package app.burrow.money.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.burrow.money.R
import app.burrow.money.ui.theme.standardSpec
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.NavLabelMinSize
import app.burrow.money.ui.theme.Shapes
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
 * The frame of every tab screen: status bar space, [TabTopBar], then [content] filling the rest,
 * and an optional [fab] at the bottom end. [content] gets the padding to use for its list: the
 * 16 dp screen margin, plus room under the last item so the FAB never covers it.
 * The bottom nav lives outside, in the nav host.
 */
@Composable
fun TabScreen(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        TabTopBar(title, actions = actions)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            content(PaddingValues(start = Space.screen, end = Space.screen, bottom = if (fab != null) Size.fabClearance else Space.screen))
            if (fab != null) Box(Modifier.align(Alignment.BottomEnd).padding(Space.screen)) { fab() }
        }
    }
}

/**
 * Top bar of a pushed screen (detail or form): 64 dp, a round back or close button, an optional
 * `title`, and one action at the end (Save, Edit, Duplicate).
 */
@Composable
fun ScreenTopBar(
    navIcon: Painter,
    navLabel: String,
    onNav: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** Set: the title is a button with ▾ ("New expense ▾" opens the type sheet, 111). */
    onTitle: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Size.topBar).padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.inline),
    ) {
        TonalIconButton(navIcon, navLabel, onNav)
        Box(Modifier.weight(1f).padding(start = Space.inline)) {
            if (title != null && onTitle != null) {
                Row(
                    Modifier.heightIn(min = Size.button).clip(Shapes.pill).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onTitle),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.grid),
                ) {
                    Text(title, style = BurrowType.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
                    androidx.compose.material3.Icon(painterResource(app.burrow.money.R.drawable.ic_expand_more_regular), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Size.icon))
                }
            } else if (title != null) {
                Text(title, style = BurrowType.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
        }
        actions()
    }
}

/**
 * The extended FAB ("Add expense", "Add bill"): 56 dp primaryContainer pill with an icon. It moves
 * up while the Undo snackbar shows, so the two never overlap.
 */
@Composable
fun AddFab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lift by animateDpAsState(if (LocalSnackbar.current.visible) Space.fabLift else 0.dp, standardSpec(), label = "fabLift")
    ExtendedFloatingActionButton(
        onClick = onClick,
        // Material's extended FAB hides its text from accessibility, so the label goes on the button.
        modifier = modifier.offset { IntOffset(0, -lift.roundToPx()) }.height(Size.fab).semantics { contentDescription = label },
        shape = Shapes.pill,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = { Icon(painterResource(R.drawable.ic_add_regular), null, modifier = Modifier.size(Size.icon)) },
        text = { Text(label, style = BurrowType.button) },
    )
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
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    NavigationBar(modifier, containerColor = c.surfaceContainerLow) {
        items.forEachIndexed { i, item ->
            val isSelected = i == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) haptics.tick(); onSelect(i) },
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

/**
 * Wide screens (≥ 840 dp, docs/PERFORMANCE.md § Responsive): the same five items as a rail on the
 * start side, selected = fill icon in a `secondaryContainer` pill.
 */
@Composable
fun BurrowNavRail(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.NavigationRail(modifier, containerColor = c.surfaceContainerLow) {
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        items.forEachIndexed { i, item ->
            val isSelected = i == selected
            androidx.compose.material3.NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(i) },
                icon = { Icon(painterResource(if (isSelected) item.selectedIcon else item.icon), contentDescription = null, modifier = Modifier.size(Size.icon)) },
                label = { CappedFontScale { Text(item.label, style = BurrowType.label, maxLines = 1) } },
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = c.onSecondaryContainer,
                    selectedTextColor = c.onSurface,
                    indicatorColor = c.secondaryContainer,
                    unselectedIconColor = c.onSurfaceVariant,
                    unselectedTextColor = c.onSurfaceVariant,
                ),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    }
}
