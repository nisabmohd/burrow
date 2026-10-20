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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                (s.knownTags + STARTER_TAGS + f.tags).distinctBy { it.lowercase() }.forEach { t -> Chip("#$t", t in f.tags, { vm.toggleTag(t) }) }
            }
        }
        DoneFooter(onDismiss)
    }
}

/** Repeat (75): never, weekly, monthly, yearly; a subscription; a reminder. Creates a bill on save. */
@Composable
fun RepeatSheet(vm: AddViewModel, s: AddUiState, onDismiss: () -> Unit) {
    val f = s.form
    val options = listOf(Every.NEVER to R.string.never, Every.WEEK to R.string.every_week, Every.MONTH to R.string.every_month, Every.YEAR to R.string.every_year)
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.extra_repeat), onDismiss, stringResource(R.string.repeat_line))
        SheetBody {
            ListGroup {
                options.forEachIndexed { i, (key, label) ->
                    ListRow(
                        stringResource(label), i, options.size, selected = f.repeat == key,
                        lead = { IconCircle(painterResource(if (key == Every.NEVER) R.drawable.ic_block_duotone else R.drawable.ic_event_repeat_duotone)) },
                        trailing = if (f.repeat == key) ({ androidx.compose.material3.Icon(painterResource(R.drawable.ic_check_regular), null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { vm.setRepeat(key) },
                    )
                }
            }
            if (f.repeat != Every.NEVER) {
                ListGroup {
                    ListRow(
                        stringResource(R.string.is_subscription), 0, 2, supporting = stringResource(R.string.is_subscription_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_autorenew_duotone)) },
                        trailing = { BurrowSwitch(f.subscription, vm::setSubscription) },
                    )
                    ListRow(
                        stringResource(R.string.remind_day_before), 1, 2,
                        lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                        trailing = { BurrowSwitch(f.repeatRemind, vm::setRepeatRemind) },
                    )
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

/** Lend or borrow (77): none, I lent, I borrowed; who; a reminder to settle in 2 weeks. */
@Composable
fun LendSheet(vm: AddViewModel, s: AddUiState, onDismiss: () -> Unit) {
    val f = s.form
    val kinds = listOf<String?>(null, Lend.LENT, Lend.BORROWED)
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.lend_or_borrow), onDismiss, stringResource(R.string.lend_line))
        SheetBody {
            ButtonGroup(kinds, f.lend, { stringResource(when (it) { Lend.LENT -> R.string.i_lent; Lend.BORROWED -> R.string.i_borrowed; else -> R.string.none }) }, vm::setLend, Modifier.fillMaxWidth())
            if (f.lend != null) {
                LabeledField(stringResource(if (f.lend == Lend.LENT) R.string.lent_to else R.string.borrowed_from)) {
                    PillTextField(f.person, vm::setPerson, stringResource(R.string.name), leading = painterResource(R.drawable.ic_person_regular), clearLabel = stringResource(R.string.clear), capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                }
                ListGroup {
                    ListRow(
                        stringResource(R.string.remind_to_settle), 0, 1, supporting = stringResource(R.string.in_two_weeks),
                        lead = { IconCircle(painterResource(R.drawable.ic_event_available_duotone)) },
                        trailing = { BurrowSwitch(f.settleRemind, vm::setSettleRemind) },
                    )
                }
            }
        }
        DoneFooter(onDismiss)
    }
}

/**
 * Date (78): yesterday, today, tomorrow, next Monday (same time) and "Pick a date and time". A
 * future date makes it upcoming: added on that day.
 */
@Composable
fun WhenSheet(at: Long, withTime: Boolean, onPick: (LocalDate) -> Unit, onPickOther: () -> Unit, onDismiss: () -> Unit) {
    val dates = rememberDateText()
    val zone = ZoneId.systemDefault()
    val picked = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val time = Instant.ofEpochMilli(at).atZone(zone).toLocalTime()
    val today = LocalDate.now()
    val days = listOf(today.minusDays(1), today, today.plusDays(1), today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))).distinct()
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.date), onDismiss, stringResource(R.string.date_sheet_line))
        SheetBody {
            ListGroup {
                val n = days.size + 1
                days.forEachIndexed { i, d ->
                    val millis = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    val label = when (d) {
                        today.plusDays(1) -> stringResource(R.string.tomorrow)
                        today, today.minusDays(1) -> dates.day(d)
                        else -> stringResource(R.string.next_weekday, dates.weekdayName(d))
                    }
                    val future = d.isAfter(today)
                    ListRow(
                        if (withTime) stringResource(R.string.day_time, label, dates.time(millis)) else label, i, n,
                        supporting = if (future) stringResource(R.string.upcoming_added_that_day) else null,
                        selected = d == picked,
                        lead = { IconCircle(painterResource(if (future) R.drawable.ic_event_upcoming_duotone else R.drawable.ic_schedule_duotone)) },
                        trailing = if (d == picked) ({ androidx.compose.material3.Icon(painterResource(R.drawable.ic_check_regular), null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { onPick(d) },
                    )
                }
                ListRow(
                    stringResource(if (withTime) R.string.pick_date_and_time else R.string.pick_a_date), days.size, n,
                    lead = { IconCircle(painterResource(R.drawable.ic_calendar_month_duotone)) }, onClick = onPickOther,
                )
            }
        }
    }
}

/** The time of a transaction (with Time on in Customise › Expense). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context))
    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.time), onDismiss)
        SheetBody { TimePicker(state, Modifier.fillMaxWidth()) }
        SheetFooter {
            BurrowButton(stringResource(R.string.done), { onPick(LocalTime.of(state.hour, state.minute)) }, Modifier.fillMaxWidth(), height = Size.buttonBottom)
        }
    }
}

fun payeeLabel(type: String) = if (type == TxnType.INCOME) R.string.received_from else R.string.paid_to
