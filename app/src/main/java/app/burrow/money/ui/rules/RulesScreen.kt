package app.burrow.money.ui.rules

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.capture.Rule
import app.burrow.money.ui.components.AddFab
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.HeaderIcon
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.launch

private enum class RulesSheet { ADD, PASTE }

/**
 * Message rules (49): centred header (how many are yours, how many on), Yours with switches,
 * Built-in packs, Import / Share. FAB Add rule → Add a rule sheet (51).
 */
@Composable
fun RulesScreen(vm: RulesViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onTeach: (Long) -> Unit, onTeachText: (String, String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf<RulesSheet?>(null) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val n = text?.let { vm.import(it) }
            snackbar.show(if (n != null) context.resources.getQuantityString(R.plurals.rules_imported, n, n) else context.getString(R.string.rules_bad_file))
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.export(context.getString(R.string.my_rule))
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } }
                .onSuccess { snackbar.show(context.getString(R.string.rules_shared)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Size.fabClearance)) {
                val on = s.mine.count { it.enabled }
                CenterHeader(
                    icon = { HeaderIcon(painterResource(R.drawable.ic_rule_regular)) },
                    title = stringResource(R.string.message_rules),
                    value = s.mine.size.toString(),
                    line = stringResource(R.string.rules_header_line, on),
                )
                SectionHeader(stringResource(R.string.yours))
                ListGroup {
                    if (s.mine.isEmpty()) {
                        ListRow(stringResource(R.string.no_rules_yet), 0, 1, lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) })
                    }
                    s.mine.forEachIndexed { i, r ->
                        ListRow(
                            r.name, i, s.mine.size,
                            supporting = stringResource(R.string.rule_line, senderLabel(r), s.uses[r.id] ?: 0),
                            lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
                            trailing = { BurrowSwitch(r.enabled, { vm.setEnabled(r.id, it) }) },
                            onClick = { onOpen(r.id) },
                        )
                    }
                }
                SectionHeader(stringResource(R.string.built_in_packs))
                ListGroup {
                    s.packs.forEachIndexed { i, p ->
                        ListRow(
                            stringResource(R.string.pack_india), i, s.packs.size,
                            supporting = pluralStringResource(R.plurals.pack_rules, p.ruleCount, p.ruleCount),
                            lead = { IconCircle(painterResource(R.drawable.ic_public_duotone)) },
                            trailing = { BurrowSwitch(p.on, { vm.setPack(p.id, it) }) },
                        )
                    }
                }
                SectionHeader(stringResource(R.string.more))
                ListGroup {
                    ListRow(
                        stringResource(R.string.import_rules), 0, 2, supporting = stringResource(R.string.import_rules_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_upload_file_duotone)) },
                        onClick = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                    ListRow(
                        stringResource(R.string.share_rules), 1, 2, supporting = stringResource(R.string.share_rules_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_ios_share_duotone)) },
                        onClick = { saveFile.launch("my-rules.burrow-rules.json") },
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.screen)) {
            AddFab(stringResource(R.string.add_rule), { sheet = RulesSheet.ADD })
        }
    }

    when (sheet) {
        RulesSheet.ADD -> BurrowSheet({ sheet = null }) {
            SheetHeader(stringResource(R.string.add_a_rule), { sheet = null }, stringResource(R.string.add_a_rule_line))
            SheetBody {
                ListGroup {
                    ListRow(
                        stringResource(R.string.paste_message), 0, 3, supporting = stringResource(R.string.paste_message_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_content_paste_duotone)) }, onClick = { sheet = RulesSheet.PASTE },
                    )
                    ListRow(
                        stringResource(R.string.from_not_understood), 1, 3,
                        supporting = if (s.waiting > 0) pluralStringResource(R.plurals.n_waiting, s.waiting, s.waiting) else stringResource(R.string.none_waiting),
                        lead = { IconCircle(painterResource(R.drawable.ic_help_duotone)) },
                        onClick = if (s.waiting > 0) ({ sheet = null; scope.launch { vm.firstWaiting()?.let(onTeach) } }) else null,
                    )
                    ListRow(
                        stringResource(R.string.import_rules), 2, 3, supporting = stringResource(R.string.import_rules_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_upload_file_duotone)) },
                        onClick = { sheet = null; openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                }
            }
        }
        RulesSheet.PASTE -> PasteSheet(onDismiss = { sheet = null }, onContinue = { sender, text -> sheet = null; onTeachText(sender, text) })
        null -> Unit
    }
}

/** Paste a message and who sent it, then teach it. */
@Composable
private fun PasteSheet(onDismiss: () -> Unit, onContinue: (String, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf("") }
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.paste_message), onDismiss, stringResource(R.string.paste_message_line))
        SheetBody {
            PillTextField(text, { text = it }, stringResource(R.string.message_hint), clearLabel = stringResource(R.string.clear))
            PillTextField(sender, { sender = it }, stringResource(R.string.sender_hint), clearLabel = stringResource(R.string.clear))
        }
        SheetFooter {
            BurrowButton(
                stringResource(R.string.continue_), { if (text.isNotBlank()) onContinue(sender.trim(), text.trim()) },
                Modifier.fillMaxWidth(), kind = if (text.isNotBlank()) app.burrow.money.ui.components.ButtonKind.FILLED else app.burrow.money.ui.components.ButtonKind.OFF,
                height = Size.buttonBottom,
            )
        }
    }
}

/** "AX-KOTAKB" from "(?i)KOTAKB$"-style patterns: the readable part of a sender rule. */
@Composable
fun senderLabel(rule: Rule): String {
    val readable = rule.sender.removePrefix("(?i)").removePrefix("^").removeSuffix("$").replace("\\", "").substringBefore('|')
    return if (readable.isBlank() || readable.contains(".*")) stringResource(R.string.any_sender) else readable
}

