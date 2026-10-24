package app.burrow.money.ui.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.burrow.money.ui.components.Note
import app.burrow.money.ui.components.NoteKind
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.R
import app.burrow.money.capture.CaptureAccess
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.CenterTitleHeader
import app.burrow.money.ui.components.Chevron
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Auto-capture (48): how many payments it recorded, the Bank SMS and Payment apps switches,
 * messages not understood (→ Teach), Message rules, Try it, How it works. A settings screen: plain
 * lists, no explanations beyond one line.
 */
@Composable
fun AutoCaptureScreen(
    vm: AutoCaptureViewModel,
    onBack: () -> Unit,
    onTeach: (Long) -> Unit,
    onRules: () -> Unit,
    onHowItWorks: () -> Unit,
    /** Customise › Transactions, and what it is set to now. */
    paymentFound: String = app.burrow.money.data.PaymentFound.ASK,
    onPaymentFound: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    // The same answer as the More tile and Today's note: saved switches against what Android allows.
    val status = rememberCaptureStatus(s.sms, s.apps)
    val smsAccess = status.smsAllowed
    val appsAccess = status.appsAllowed
    // Set when a switch was turned on but Android still says no: on a downloaded APK that is
    // Restricted settings, and the note below shows the way out.
    var smsRefused by rememberSaveable { mutableStateOf(false) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = CaptureAccess.smsGranted(context)
        smsRefused = !granted
        vm.setSms(granted)
    }
    // A switch shows on only when it is on AND Android lets it read: never "on" with nothing recorded.
    val smsOn = status.smsOn
    val appsOn = status.appsOn
    val anyOn = status.anyOn
    val restricted = remember { CaptureAccess.restrictedSettingsLikely(context) }
    val blocked = restricted && ((smsRefused && !smsAccess) || (s.apps && !appsAccess))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = stringResource(R.string.auto_capture))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            val percent = s.trust.percent
            if (blocked) {
                Note(
                    NoteKind.WARN, painterResource(R.drawable.ic_info_regular), stringResource(R.string.restricted_title),
                    line = stringResource(R.string.restricted_line),
                    action = stringResource(R.string.app_info), onAction = { runCatching { context.startActivity(CaptureAccess.appInfo(context)) } },
                )
            } else if (!anyOn) {
                Note(
                    NoteKind.INFO, painterResource(R.drawable.ic_info_regular), stringResource(R.string.capture_off_title),
                    line = stringResource(if (CaptureAccess.smsInThisBuild) R.string.capture_off_line else R.string.capture_off_line_apps),
                )
            }
            if (percent != null && s.trust.found > 0) {
                // Trust in capture (15): how much of this month it recorded by itself.
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(vertical = Space.small),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    app.burrow.money.ui.components.RingPercent(percent / 100f, androidx.compose.material3.MaterialTheme.colorScheme.primary, androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer, app.burrow.money.ui.theme.Size.ringTrust)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(Space.block))
                    androidx.compose.material3.Text(stringResource(R.string.recorded_by_itself), style = app.burrow.money.ui.theme.BurrowType.title, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    androidx.compose.material3.Text(
                        pluralStringResource(R.plurals.trust_line, s.trust.total, s.trust.found, s.trust.total, s.trust.typed),
                        style = app.burrow.money.ui.theme.BurrowType.body, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                CenterTitleHeader(
                    painterResource(R.drawable.ic_sms_regular),
                    pluralStringResource(R.plurals.payments_recorded, s.recordedThisMonth, s.recordedThisMonth),
                    line = stringResource(R.string.recorded_line),
                )
            }
            val switches = buildList<@Composable (Int, Int) -> Unit> {
                if (CaptureAccess.smsInThisBuild) add { i, n ->
                    ListRow(
                        stringResource(R.string.bank_sms), i, n,
                        supporting = if (s.smsBanks.isNotEmpty()) s.smsBanks.joinToString(", ") else stringResource(R.string.bank_sms_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
                        trailing = {
                            BurrowSwitch(smsOn, { on ->
                                if (on && !smsAccess) askSms.launch(CaptureAccess.permissionsToAsk()) else vm.setSms(on)
                            })
                        },
                    )
                }
                add { i, n ->
                    ListRow(
                        stringResource(R.string.payment_apps), i, n, supporting = stringResource(R.string.payment_apps_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                        trailing = {
                            BurrowSwitch(appsOn, { on ->
                                // Saved as wanted; it shows on once access is given in system settings.
                                if (on && !appsAccess) runCatching { context.startActivity(CaptureAccess.listenerSettings()) }
                                vm.setApps(on)
                            })
                        },
                    )
                }
            }
            ListGroup { switches.forEachIndexed { i, r -> r(i, switches.size) } }
            if (anyOn && s.notUnderstood > 0) {
                ListGroup {
                    ListRow(
                        pluralStringResource(R.plurals.teach_missed, s.notUnderstood, s.notUnderstood), 0, 1,
                        supporting = if (s.unreadFrom.isNotEmpty()) stringResource(R.string.from_senders, s.unreadFrom.joinToString(", ")) else stringResource(R.string.tap_to_teach),
                        lead = { IconCircle(painterResource(R.drawable.ic_help_duotone)) },
                        trailing = { Chevron() },
                        onClick = { onTeach(0L) }, // the list of messages not understood
                    )
                }
            }
            if (anyOn) {
                ListGroup {
                    ListRow(
                        stringResource(R.string.when_payment_found), 0, 1, supporting = stringResource(app.burrow.money.ui.more.paymentFoundLabel(paymentFound)),
                        lead = { IconCircle(painterResource(R.drawable.ic_edit_note_duotone)) }, onClick = onPaymentFound,
                    )
                }
                ListGroup {
                    ListRow(
                        stringResource(R.string.message_rules), 0, 1, supporting = if (s.indiaPack) stringResource(R.string.rules_line, s.yourRules) else pluralStringResource(R.plurals.rules_line_yours, s.yourRules, s.yourRules),
                        lead = { IconCircle(painterResource(R.drawable.ic_rule_duotone)) }, trailing = { Chevron() }, onClick = onRules,
                    )
                }
            }
            ListGroup {
                val n = if (anyOn) 2 else 1
                if (anyOn) ListRow(
                    stringResource(R.string.try_it), 0, n, supporting = stringResource(R.string.try_it_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_play_arrow_duotone)) },
                    onClick = { vm.tryIt(); snackbar.show(context.getString(R.string.sample_sent)) },
                )
                ListRow(
                    stringResource(R.string.how_it_works), n - 1, n,
                    lead = { IconCircle(painterResource(R.drawable.ic_info_duotone)) }, onClick = onHowItWorks,
                )
            }
        }
    }
}
