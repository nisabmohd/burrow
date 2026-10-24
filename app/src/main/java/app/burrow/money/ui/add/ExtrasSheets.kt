package app.burrow.money.ui.add

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.Lend
import app.burrow.money.data.TxnType
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonGroup
import app.burrow.money.ui.components.Chip
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.rememberDateText
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Tags offered on the Note sheet before the user has any of their own. */
private val STARTER_TAGS = listOf("Work", "Trip", "Family", "Reimbursable")

/** Done at the bottom of every extras sheet. */
@Composable
private fun DoneFooter(onDone: () -> Unit) = SheetFooter {
    BurrowButton(stringResource(R.string.done), onDone, Modifier.fillMaxWidth(), height = Size.buttonBottom)
}

/** Note (74): Paid to, the note, tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteSheet(vm: AddViewModel, s: AddUiState, onDismiss: () -> Unit) {
    val f = s.form
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.note), onDismiss, stringResource(R.string.optional))
        SheetBody {
            if (f.type != TxnType.TRANSFER) {
                LabeledField(stringResource(payeeLabel(f.type))) {
                    PillTextField(f.payee, vm::setPayee, stringResource(R.string.payee_hint_short), leading = painterResource(R.drawable.ic_storefront_regular), clearLabel = stringResource(R.string.clear), capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                }
            }
            LabeledField(stringResource(R.string.note)) {
                PillTextField(f.note, vm::setNote, stringResource(R.string.note_hint_short), leading = painterResource(R.drawable.ic_notes_regular), clearLabel = stringResource(R.string.clear))
            }
            LabeledField(stringResource(R.string.tags)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    (s.knownTags + STARTER_TAGS + f.tags).distinctBy { it.lowercase() }.forEach { t -> Chip(t, t in f.tags, { vm.toggleTag(t) }) }
                }
            }
        }
        DoneFooter(onDismiss)
    }
}

/** Attach (76): take or choose a photo, or a file; kept only on this phone. */
@Composable
fun AttachSheet(app: AppContainer, vm: AddViewModel, s: AddUiState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val raw = cameraFile ?: return@rememberLauncherForActivityResult
        scope.launch { if (ok) app.attachments.fromCamera(raw)?.let(vm::addAttachment) else raw.delete() }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { app.attachments.take(it)?.let(vm::addAttachment) } }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { app.attachments.take(it)?.let(vm::addAttachment) } }
    }
    val f = s.form
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.extra_attach), onDismiss, stringResource(R.string.attach_line))
        SheetBody {
            ListGroup {
                ListRow(stringResource(R.string.take_photo), 0, 3, lead = { IconCircle(painterResource(R.drawable.ic_photo_camera_duotone)) }, onClick = {
                    val (f0, uri) = app.attachments.cameraTarget()
                    cameraFile = f0
                    runCatching { camera.launch(uri) }
                })
                ListRow(stringResource(R.string.choose_photo), 1, 3, lead = { IconCircle(painterResource(R.drawable.ic_image_duotone)) }, onClick = {
                    photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                ListRow(stringResource(R.string.choose_file), 2, 3, supporting = stringResource(R.string.pdf_or_image), lead = { IconCircle(painterResource(R.drawable.ic_description_duotone)) }, onClick = {
                    file.launch(arrayOf("application/pdf", "image/*"))
                })
            }
            val rows = f.kept.map { Triple(it.name, it.bytes, { vm.removeKept(it) }) } + f.attachments.map { Triple(it.name, it.bytes, { vm.removePending(it) }) }
            if (rows.isNotEmpty()) {
                ListGroup {
                    rows.forEachIndexed { i, (name, bytes, remove) ->
                        ListRow(
                            name, i, rows.size, supporting = stringResource(R.string.saved_in_burrow, Formatter.formatShortFileSize(context, bytes)),
                            lead = { IconCircle(painterResource(R.drawable.ic_image_duotone)) },
                            trailing = { TonalIconButton(painterResource(R.drawable.ic_close_regular), stringResource(R.string.remove_named, name), remove) },
                        )
                    }
                }
            }
        }
        DoneFooter(onDismiss)
    }
}

/** Type (111), from the title "New expense ▾": Expense · Income · Transfer. */
@Composable
fun TypeSheet(type: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple(TxnType.EXPENSE, R.string.money_out, R.drawable.ic_arrow_upward_duotone),
        Triple(TxnType.INCOME, R.string.money_in, R.drawable.ic_arrow_downward_duotone),
        Triple(TxnType.TRANSFER, R.string.type_transfer_line, R.drawable.ic_swap_horiz_duotone),
    )
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.type), onDismiss)
        SheetBody {
            ListGroup {
                options.forEachIndexed { i, (key, line, icon) ->
                    app.burrow.money.ui.components.PickRow(
                        stringResource(typeLabel(key)), i, options.size, key == type, { onPick(key) },
                        supporting = stringResource(line), lead = { IconCircle(painterResource(icon)) },
                    )
                }
            }
        }
    }
}

/**
 * Repeat or lend (112): Once · Repeat · Subscription · Lent · Borrowed, one choice; then How often
 * (Weekly / Monthly / Yearly) or who it was; Done. Repeat and Subscription make a bill on save;
 * income has no Subscription; Customise › Expense decides whether repeat and lend show.
 */
@Composable
fun RepeatOrLendSheet(vm: AddViewModel, s: AddUiState, onDismiss: () -> Unit) {
    val f = s.form
    val choice = when {
        f.lend != null -> f.lend
        f.repeat != Every.NEVER && f.subscription -> CHOICE_SUB
        f.repeat != Every.NEVER -> CHOICE_REPEAT
        else -> CHOICE_ONCE
    }
    val options = buildList {
        add(Triple(CHOICE_ONCE, R.string.once to R.string.once_line, R.drawable.ic_block_duotone))
        if (s.repeatOffered) {
            add(Triple(CHOICE_REPEAT, R.string.extra_repeat to R.string.repeat_bill_line, R.drawable.ic_event_repeat_duotone))
            if (f.type == TxnType.EXPENSE) add(Triple(CHOICE_SUB, R.string.subscription to R.string.is_subscription_line, R.drawable.ic_autorenew_duotone))
        }
        if (s.shows(app.burrow.money.data.FormExtra.LEND)) {
            add(Triple(Lend.LENT, R.string.lent to R.string.lent_line, R.drawable.ic_handshake_duotone))
            add(Triple(Lend.BORROWED, R.string.borrowed to R.string.borrowed_line, R.drawable.ic_handshake_duotone))
        }
    }
    fun pick(key: String) {
        when (key) {
            CHOICE_ONCE -> { vm.setRepeat(Every.NEVER); vm.setSubscription(false); vm.setLend(null) }
            CHOICE_REPEAT, CHOICE_SUB -> {
                vm.setLend(null); vm.setSubscription(key == CHOICE_SUB)
                if (f.repeat == Every.NEVER) vm.setRepeat(Every.MONTH)
            }
            else -> { vm.setRepeat(Every.NEVER); vm.setSubscription(false); vm.setLend(key) }
        }
    }
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.repeat_or_lend), onDismiss)
        SheetBody {
            ListGroup {
                options.forEachIndexed { i, (key, text, icon) ->
                    app.burrow.money.ui.components.PickRow(
                        stringResource(text.first), i, options.size, key == choice, { pick(key) },
                        supporting = stringResource(text.second), lead = { IconCircle(painterResource(icon)) },
                    )
                }
            }
            when (choice) {
                CHOICE_REPEAT, CHOICE_SUB -> LabeledField(stringResource(R.string.how_often)) {
                    ButtonGroup(listOf(Every.WEEK, Every.MONTH, Every.YEAR), f.repeat, {
                        stringResource(when (it) { Every.WEEK -> R.string.weekly; Every.YEAR -> R.string.yearly; else -> R.string.monthly })
                    }, vm::setRepeat, Modifier.fillMaxWidth())
                }
                Lend.LENT, Lend.BORROWED -> LabeledField(stringResource(if (choice == Lend.LENT) R.string.lent_to else R.string.borrowed_from)) {
                    PillTextField(f.person, vm::setPerson, stringResource(R.string.name), leading = painterResource(R.drawable.ic_person_regular), clearLabel = stringResource(R.string.clear), capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                }
            }
        }
        DoneFooter(onDismiss)
    }
}

private const val CHOICE_ONCE = "ONCE"
private const val CHOICE_REPEAT = "REPEAT"
private const val CHOICE_SUB = "SUBSCRIPTION"

/** Time (135): the value sheet, typed. */
@Composable
fun TimeSheet(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) = app.burrow.money.ui.components.TimeValueSheet(time, onPick, onDismiss)

fun payeeLabel(type: String) = if (type == TxnType.INCOME) R.string.received_from else R.string.paid_to
