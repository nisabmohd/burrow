package app.burrow.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.burrow.money.R
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.theme.CategoryHues
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.categoryColors

/**
 * A card of round choices in a fixed grid (Edit category 62, Edit account 106/107): six equal
 * columns, rows filled from the start, so 12 colours are two full rows and a short last row lines up
 * on the left. A circle shrinks a little when six don't fit at 48 dp (large display size).
 */
@Composable
fun <T> SwatchGrid(items: List<T>, modifier: Modifier = Modifier, columns: Int = 6, cell: @Composable (T) -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Shapes.surface).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One round choice: a ring in primary when chosen. */
@Composable
fun Swatch(on: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.widthIn(max = Size.button).fillMaxWidth().aspectRatio(1f).clip(Shapes.pill)
            .then(if (on) Modifier.border(Space.group / 2, c.primary, Shapes.pill) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The 12 category colours; a check on the chosen one. */
@Composable
fun HuePicker(selected: Float, label: String, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    SwatchGrid(CategoryHues.all, modifier) { hue ->
        val on = hue == selected
        Swatch(on, label, { onPick(hue) }) {
            Box(Modifier.fillMaxSize().padding(Space.group).clip(Shapes.pill).background(categoryColors(hue).solid), contentAlignment = Alignment.Center) {
                if (on) Icon(painterResource(R.drawable.ic_check_regular), null, tint = c.surface, modifier = Modifier.size(Size.iconSmall))
            }
        }
    }
}

/** Icons to choose from; the chosen one takes [hue]. */
@Composable
fun IconPicker(icons: List<String>, selected: String?, hue: Float, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    SwatchGrid(icons, modifier) { icon ->
        val on = icon == selected
        Swatch(on, icon.replace('_', ' '), { onPick(icon) }) { CategoryCircle(icon, if (on) hue else null) }
    }
}
