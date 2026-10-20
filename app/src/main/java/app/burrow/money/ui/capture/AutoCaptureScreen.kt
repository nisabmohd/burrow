package app.burrow.money.ui.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.setSms(CaptureAccess.smsGranted(context)) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), onBack, title = stringResource(R.string.auto_capture))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            CenterTitleHeader(
                painterResource(R.drawable.ic_sms_regular),
                pluralStringResource(R.plurals.payments_recorded, s.recordedThisMonth, s.recordedThisMonth),
                stringResource(R.string.recorded_line),
            )
            val switches = buildList<@Composable (Int, Int) -> Unit> {
                if (CaptureAccess.smsInThisBuild) add { i, n ->
                    ListRow(
                        stringResource(R.string.bank_sms), i, n,
                        supporting = if (s.smsBanks.isNotEmpty()) s.smsBanks.joinToString(", ") else stringResource(R.string.bank_sms_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_sms_duotone)) },
                        trailing = {
                            BurrowSwitch(s.sms, { on ->
                                if (on && !CaptureAccess.smsGranted(context)) askSms.launch(CaptureAccess.permissionsToAsk()) else vm.setSms(on)
                            })
                        },
                    )
                }
                add { i, n ->
                    ListRow(
                        stringResource(R.string.payment_apps), i, n, supporting = stringResource(R.string.payment_apps_line),
                        lead = { IconCircle(painterResource(R.drawable.ic_notifications_duotone)) },
                        trailing = {
                            BurrowSwitch(s.apps, { on ->
                                if (on && !CaptureAccess.listenerEnabled(context)) runCatching { context.startActivity(CaptureAccess.listenerSettings()) }
                                vm.setApps(on)
                            })
                        },
                    )
                }
            }
            ListGroup { switches.forEachIndexed { i, r -> r(i, switches.size) } }
            if (s.notUnderstood > 0) {
                ListGroup {
                    ListRow(
                        pluralStringResource(R.plurals.not_understood, s.notUnderstood, s.notUnderstood), 0, 1,
                        supporting = stringResource(R.string.tap_to_teach),
                        lead = { IconCircle(painterResource(R.drawable.ic_help_duotone)) },
                        trailing = { Chevron() },
                        onClick = { scope.launch { vm.firstNotUnderstood()?.let(onTeach) } },
                    )
                }
            }
            ListGroup {
                ListRow(
                    stringResource(R.string.when_payment_found), 0, 1, supporting = stringResource(app.burrow.money.ui.more.paymentFoundLabel(paymentFound)),
                    lead = { IconCircle(painterResource(R.drawable.ic_edit_note_duotone)) }, onClick = onPaymentFound,
                )
            }
            ListGroup {
                ListRow(
                    stringResource(R.string.message_rules), 0, 1, supporting = stringResource(R.string.rules_line, s.yourRules),
                    lead = { IconCircle(painterResource(R.drawable.ic_rule_duotone)) }, trailing = { Chevron() }, onClick = onRules,
                )
            }
            ListGroup {
                ListRow(
                    stringResource(R.string.try_it), 0, 2, supporting = stringResource(R.string.try_it_line),
                    lead = { IconCircle(painterResource(R.drawable.ic_play_arrow_duotone)) },
                    onClick = { vm.tryIt(); snackbar.show(context.getString(R.string.sample_sent)) },
                )
                ListRow(
                    stringResource(R.string.how_it_works), 1, 2,
                    lead = { IconCircle(painterResource(R.drawable.ic_info_duotone)) }, onClick = onHowItWorks,
                )
            }
        }
    }
}
