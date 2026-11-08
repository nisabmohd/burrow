package app.burrow.money.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.burrow.money.R
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Space

/** A form field with its label above (New bill: "Name", "Amount"; New goal). */
@Composable
fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Text(label, style = BurrowType.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.headingInset))
        content()
    }
}

/**
 * Select field (191/192): the text field's pill with its label above, the value (or [placeholder]) and ⌄
 * on the right, no lead icon. A tap opens the sheet that picks the value (Bank, 196).
 */
@Composable
fun SelectField(label: String, value: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    LabeledField(label) {
        Row(
            modifier.fillMaxWidth().heightIn(min = Size.search).clip(Shapes.pill).background(c.surfaceContainer)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = Space.sheet),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value ?: placeholder, style = BurrowType.bodyLarge, color = if (value != null) c.onSurface else c.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_expand_more_regular), null, tint = c.onSurfaceVariant, modifier = Modifier.size(Size.icon))
        }
    }
}
