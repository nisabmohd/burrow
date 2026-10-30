package app.burrow.money.ui.rules

import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.SheetBody
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.capture.Direction
import app.burrow.money.capture.NumberStyle
import app.burrow.money.capture.Parsed
import app.burrow.money.domain.MoneyFormat
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterTitleHeader
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.MessageBox
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.marksFor
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Space
import java.util.Locale

/**
 * A message rule (50): who it is from, an example with the parts highlighted, what it reads, its
 * language and number style, Test it (paste a message, parts highlight live), the switch and
 * Delete for your own rules. Built-in rules are read-only.
 */
@Composable
fun RuleScreen(vm: RuleViewModel, onBack: () -> Unit) {
    val ruleState by vm.rule.collectAsStateWithLifecycle()
    val test by vm.testText.collectAsStateWithLifecycle()
    val rule = ruleState ?: return
    val c = MaterialTheme.colorScheme
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val example = rule.example?.let { vm.read(rule, it) }
    var renaming by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack) {
            // Your rules can be named ("HDFC card", "Salary"); built-in ones keep the pack's names.
            if (rule.userMade) app.burrow.money.ui.components.TonalIconButton(painterResource(R.drawable.ic_edit_regular), stringResource(R.string.rename_rule), { renaming = true })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
        ) {
            CenterTitleHeader(painterResource(R.drawable.ic_sms_regular), rule.name, line = stringResource(R.string.rule_from, senderLabel(rule)))
            if (rule.example != null) {
                SectionHeader(stringResource(R.string.example))
                MessageBox(senderLabel(rule), rule.example, marksFor(example?.spans.orEmpty()))
            }
            SectionHeader(stringResource(R.string.what_it_reads))
            ReadsGroup(example, rule.direction)
            SectionHeader(stringResource(R.string.language_and_numbers))
            ListGroup {
                ListRow(
                    Locale.forLanguageTag(rule.language).let { it.getDisplayLanguage(it).replaceFirstChar { ch -> ch.uppercase() } }, 0, 2,
                    supporting = stringResource(R.string.message_language),
                    lead = { IconCircle(painterResource(R.drawable.ic_translate_duotone)) },
                )
                ListRow(
                    numberExample(rule.numberStyle), 1, 2, supporting = stringResource(R.string.number_style),
                    lead = { IconCircle(painterResource(R.drawable.ic_tag_duotone)) },
                )
            }
            SectionHeader(stringResource(R.string.test_it))
            Column(verticalArrangement = Arrangement.spacedBy(Space.block)) {
                PillTextField(test, vm::setTest, stringResource(R.string.test_hint), leading = painterResource(R.drawable.ic_content_paste_regular), clearLabel = stringResource(R.string.clear))
                if (test.isBlank()) Text(stringResource(R.string.test_help, senderLabel(rule)), style = BurrowType.body, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.grid))
                if (test.isNotBlank()) {
                    val p = vm.read(rule, test)
                    MessageBox(stringResource(R.string.message), test, marksFor(p?.spans.orEmpty()))
                    Text(
                        when {
                            p == null -> stringResource(R.string.test_no)
                            p.payee != null -> stringResource(R.string.test_reads_to, MoneyFormat.of(p.currency ?: "INR").plain(p.amountMinor), p.payee)
                            else -> stringResource(R.string.test_reads, MoneyFormat.of(p.currency ?: "INR").plain(p.amountMinor))
                        },
                        style = BurrowType.body,
                        color = if (p == null) c.error else c.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.grid),
                    )
                }
            }
            if (rule.userMade) {
                Column(Modifier.padding(top = Space.sectionTop), verticalArrangement = Arrangement.spacedBy(Space.block)) {
                    ListGroup {
                        ListRow(stringResource(R.string.use_this_rule), 0, 1, trailing = { BurrowSwitch(rule.enabled, vm::setEnabled) })
                    }
                    BurrowButton(
                        stringResource(R.string.delete_rule),
                        { vm.delete { undo -> snackbar.show(context.getString(R.string.rule_deleted), undo) }; onBack() },
                        Modifier.fillMaxWidth(), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.built_in_rule, stringResource(R.string.pack_india)),
                    style = BurrowType.body, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sectionTop),
                )
            }
        }
    }
    if (renaming) RenameRuleSheet(rule.name, onDismiss = { renaming = false }) { vm.rename(it); renaming = false }
}

/** Rename one of your rules: one field, Save (off while blank). */
@Composable
private fun RenameRuleSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val ok = name.isNotBlank()
    app.burrow.money.ui.components.BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.rename_rule), onDismiss, stringResource(R.string.rename_rule_line))
        SheetBody {
            PillTextField(
                name, { name = it.take(app.burrow.money.data.RULE_NAME_MAX) }, stringResource(R.string.rule_name_hint),
                leading = painterResource(R.drawable.ic_edit_regular), clearLabel = stringResource(R.string.clear),
                onDone = { if (ok) onSave(name) },
            )
        }
        app.burrow.money.ui.components.SheetFooter {
            BurrowButton(
                stringResource(R.string.save), { if (ok) onSave(name) }, Modifier.fillMaxWidth(),
                kind = if (ok) ButtonKind.FILLED else ButtonKind.OFF, height = app.burrow.money.ui.theme.Size.buttonBottom,
            )
        }
    }
}

/** Amount, Paid to, Account and Direction, as the example reads them. */
@Composable
private fun ReadsGroup(p: Parsed?, direction: Direction?) {
    val notRead = stringResource(R.string.not_read)
    val amount = p?.let { MoneyFormat.of(it.currency ?: "INR").plain(it.amountMinor) } ?: notRead
    ListGroup {
        ListRow(amount, 0, 4, supporting = stringResource(R.string.amount), lead = { IconCircle(painterResource(R.drawable.ic_payments_duotone)) })
        ListRow(p?.payee ?: notRead, 1, 4, supporting = stringResource(R.string.from_or_to), lead = { IconCircle(painterResource(R.drawable.ic_storefront_duotone)) })
        ListRow(p?.account ?: notRead, 2, 4, supporting = stringResource(R.string.account), lead = { IconCircle(painterResource(R.drawable.ic_account_balance_duotone)) })
        ListRow(
            stringResource(
                when (direction ?: p?.direction) {
                    Direction.IN -> R.string.money_in
                    Direction.OUT -> R.string.money_out
                    null -> R.string.decided_by_words
                },
            ),
            3, 4, supporting = stringResource(R.string.direction), lead = { IconCircle(painterResource(R.drawable.ic_swap_vert_duotone)) },
        )
    }
}

private fun numberExample(style: NumberStyle) = when (style) {
    NumberStyle.COMMA_DOT -> "1,234.56"
    NumberStyle.DOT_COMMA -> "1.234,56"
    NumberStyle.SPACE_COMMA -> "1 234,56"
    NumberStyle.PLAIN -> "1234.56"
}

