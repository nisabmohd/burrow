package app.burrow.money.ui.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.burrow.money.R
import app.burrow.money.capture.CaptureAccess
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.Mark
import app.burrow.money.ui.components.MarkStyle
import app.burrow.money.ui.components.MessageText
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.fadeThrough

private const val STEPS = 3

/**
 * Turn on auto-capture (45–47): what it does, what stays private, and the two permissions.
 * Allow asks Android for SMS (where this build reads SMS) and notifications, then opens the
 * notification-access settings. [onAllowed] runs with what was granted.
 */
@Composable
fun CaptureIntroScreen(onClose: () -> Unit, onAllowed: (sms: Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!CaptureAccess.listenerEnabled(context)) runCatching { context.startActivity(CaptureAccess.listenerSettings()) }
        onAllowed(CaptureAccess.smsGranted(context))
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (step == 0) ScreenTopBar(painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose)
        else ScreenTopBar(painterResource(R.drawable.ic_arrow_back_regular), stringResource(R.string.back), { step-- })
        Dots(step, Modifier.align(Alignment.CenterHorizontally).padding(vertical = Space.block))
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeThrough() }, label = "introStep") { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Panel {
                    when (s) {
                        0 -> PanelRecords()
                        1 -> PanelPrivate()
                        else -> PanelAllow()
                    }
                }
                Spacer(Modifier.height(Space.sheet * 2))
                Text(stringResource(TITLES[s]), style = BurrowType.intro, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.block))
                Text(stringResource(LINES[s]), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.sheet))
            }
        }
        Row(Modifier.fillMaxWidth().padding(Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.inline)) {
            BurrowButton(stringResource(R.string.not_now), onClose, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            if (step < STEPS - 1) BurrowButton(stringResource(R.string.next), { step++ }, Modifier.weight(1f), height = Size.buttonBottom)
            else BurrowButton(stringResource(R.string.allow), { ask.launch(CaptureAccess.permissionsToAsk()) }, Modifier.weight(1f), height = Size.buttonBottom)
        }
    }
}

private val TITLES = listOf(R.string.intro1_title, R.string.intro2_title, R.string.intro3_title)
private val LINES = listOf(R.string.intro1_line, R.string.intro2_line, R.string.intro3_line)

/** Step dots: the current one is a 24 dp pill. */
@Composable
private fun Dots(step: Int, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val label = stringResource(R.string.step_of, step + 1, STEPS)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
        repeat(STEPS) { i ->
            Box(Modifier.size(width = if (i == step) Size.dotActive else Size.dot, height = Size.dot).clip(Shapes.pill).background(if (i == step) c.primary else c.outline))
        }
    }
}

/** The rose panel that illustrates a step. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Shapes.surface).background(MaterialTheme.colorScheme.primaryContainer).padding(Space.hero),
        verticalArrangement = Arrangement.spacedBy(Space.block),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** A white card inside the panel, slightly tilted like the design. */
@Composable
private fun PanelCard(modifier: Modifier = Modifier, tilt: Float = 0f, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.rotate(tilt).clip(Shapes.surface).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = Space.card, vertical = Space.block),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.block),
        content = content,
    )
}

private const val SAMPLE_SMS = "Rs.240.00 debited from A/c XX1234 to thirdwave@okicici"

@Composable
private fun SmsCard(marks: List<Mark>) {
    val c = MaterialTheme.colorScheme
    PanelCard(Modifier.fillMaxWidth(), tilt = -1.5f) {
        IconCircle(painterResource(R.drawable.ic_sms_duotone))
        Column {
            Text(stringResource(R.string.intro_sms_from), style = BurrowType.label, color = c.onSurfaceVariant)
            MessageText(SAMPLE_SMS, marks)
        }
    }
}

@Composable
private fun PanelRecords() {
    val c = MaterialTheme.colorScheme
    val money = LocalMoney.current
    SmsCard(emptyList())
    Icon(painterResource(R.drawable.ic_arrow_forward_regular), null, tint = c.onPrimaryContainer, modifier = Modifier.rotate(90f).size(Size.iconButton20))
    PanelCard(Modifier.fillMaxWidth(), tilt = 1f) {
        CategoryCircle("restaurant", 28f)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.intro_saved_title, money.plain(240_00)), style = BurrowType.titleSmall, color = c.onSurface)
            Text(stringResource(R.string.intro_saved_line), style = BurrowType.body, color = c.onSurfaceVariant)
        }
        IconCircle(painterResource(R.drawable.ic_check_regular), container = c.primary, content = c.onPrimary)
    }
}

@Composable
private fun PanelPrivate() {
    val amount = SAMPLE_SMS.indexOf("240.00")
    val payee = SAMPLE_SMS.indexOf("thirdwave")
    SmsCard(listOf(Mark(amount until amount + 6, MarkStyle.SOFT), Mark(payee until SAMPLE_SMS.length, MarkStyle.SOFT)))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
        SmallPanelCard(R.drawable.ic_wifi_off_duotone, R.string.intro_no_internet, Modifier.weight(1f), -1f)
        SmallPanelCard(R.drawable.ic_lock_duotone, R.string.intro_otps_skipped, Modifier.weight(1f), 1.5f)
    }
}

@Composable
private fun SmallPanelCard(icon: Int, text: Int, modifier: Modifier, tilt: Float) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.rotate(tilt).clip(Shapes.surface).background(c.surfaceContainerLowest).padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Icon(painterResource(icon), null, tint = c.primary, modifier = Modifier.size(Size.icon))
        Text(stringResource(text), style = BurrowType.titleSmall, color = c.onSurface)
    }
}

@Composable
private fun PanelAllow() {
    val c = MaterialTheme.colorScheme
    val rows = buildList {
        if (CaptureAccess.smsInThisBuild) add(R.drawable.ic_sms_duotone to R.string.intro_read_sms)
        add(R.drawable.ic_notifications_duotone to R.string.intro_notifications)
    }
    rows.forEach { (icon, text) ->
        PanelCard(Modifier.fillMaxWidth()) {
            IconCircle(painterResource(icon), content = c.primary)
            Text(stringResource(text), style = BurrowType.titleSmall, color = c.onSurface)
        }
    }
}
