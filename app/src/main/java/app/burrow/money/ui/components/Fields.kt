package app.burrow.money.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
