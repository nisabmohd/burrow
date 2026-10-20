package app.burrow.money.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.burrow.money.R
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.WavyProgress
import app.burrow.money.ui.components.rememberCountUp
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space

/**
 * The one hero in the app (docs/DESIGN.md rule 1): primaryContainer, "October budget · 13 days
 * left", what is left counting up once, "left of ₹30,000 · ₹1,050 a day", the wavy progress.
 * Over budget it turns red-toned and says "₹6,300 over your ₹30,000 budget". Without a budget it
 * shows what was spent this month.
 */
@Composable
fun BudgetHero(hero: HeroState, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    val dates = rememberDateText()
    val (bg, fg) = if (hero.over) c.errorContainer to c.onErrorContainer else c.primaryContainer to c.onPrimaryContainer
    val month = dates.month(hero.period.start)
    val days = pluralStringResource(R.plurals.days_left, hero.daysLeft, hero.daysLeft)
    val label = if (hero.budget != null) stringResource(R.string.hero_label, month, days) else stringResource(R.string.hero_label_no_budget, month)
    val amount = when {
        hero.budget == null -> hero.spent
        hero.over -> -hero.left
        else -> hero.left
    }
    val line = when {
        hero.budget == null -> stringResource(R.string.hero_no_budget)
        hero.over -> stringResource(R.string.hero_over, money.plain(hero.budget))
        else -> stringResource(R.string.hero_left, money.plain(hero.budget), money.plain(hero.perDay))
    }
    val shown = rememberCountUp(amount)
    Column(
        modifier.fillMaxWidth().clip(Shapes.surface).background(bg).padding(Space.hero)
            .clearAndSetSemantics { contentDescription = "$label. ${money.plain(amount)}, $line" },
        verticalArrangement = Arrangement.spacedBy(Space.group),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (hero.over) R.drawable.ic_error_duotone else R.drawable.ic_donut_large_duotone),
                null, tint = fg, modifier = Modifier.size(Size.iconButton20),
            )
            Text(label, style = BurrowType.button, color = fg, modifier = Modifier.padding(start = Space.small))
        }
        Text(money.plain(shown), style = BurrowType.display, color = fg, maxLines = 1)
        Text(line, style = BurrowType.body, color = fg.copy(alpha = 0.85f))
        if (hero.budget != null) WavyProgress(hero.progress, fg, Modifier.padding(top = Space.block))
    }
}
