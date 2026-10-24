package app.burrow.money.ui.more

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.burrow.money.R
import app.burrow.money.data.Settings
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.CappedFontScale
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.components.confirm
import app.burrow.money.ui.components.tick
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fingerprint or face, through the platform prompt (no library). Android 9+ and only when set up. */
object Biometrics {
    fun available(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        Build.VERSION.SDK_INT >= 29 -> @Suppress("DEPRECATION") (context.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
        else -> false
    }

    fun ask(context: Context, title: String, usePin: String, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < 28) return
        val executor = context.mainExecutor
        BiometricPrompt.Builder(context)
            .setTitle(title)
            .setNegativeButton(usePin, executor) { _, _ -> }
            .build()
            .authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
            })
    }
}

/** What the PIN screen is for. */
enum class PinMode { UNLOCK, SET }

/**
 * The PIN pad (71): the lock in a cookie, the title, four dots, a 3 × 4 pad of round keys with
 * fingerprint (when unlocking) and delete. [check] says whether a PIN is right; [onPin] gets a new one.
 */
@Composable
fun PinScreen(
    mode: PinMode,
    check: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onPin: (String) -> Unit,
    onClose: (() -> Unit)?,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var entered by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var wrongTries by rememberSaveable { mutableIntStateOf(0) }
    var waitSeconds by remember { mutableIntStateOf(0) }
    val canBio = mode == PinMode.UNLOCK && Biometrics.available(context)
    val unlockTitle = stringResource(R.string.unlock_burrow)
    val usePin = stringResource(R.string.use_pin)
    // Back on the lock leaves the app (never past the lock); elsewhere it closes the pad.
    androidx.activity.compose.BackHandler { onClose?.invoke() ?: (context as? android.app.Activity)?.moveTaskToBack(true) }
    LaunchedEffect(Unit) { if (canBio) Biometrics.ask(context, unlockTitle, usePin, onUnlocked) }
    LaunchedEffect(waitSeconds) { if (waitSeconds > 0) { delay(1_000); waitSeconds-- } }
    val scope = rememberCoroutineScope()
    // Runs on the tap that completes the PIN, so a quick next tap is never lost.
    fun submit(pin: String) {
        entered = ""
        when {
            mode == PinMode.UNLOCK -> scope.launch {
                if (check(pin)) { haptics.confirm(); onUnlocked() } else {
                    wrongTries++
                    error = R.string.wrong_pin
                    if (wrongTries % 5 == 0) waitSeconds = 30
                }
            }
            first == null -> { first = pin; error = null }
            first == pin -> { haptics.confirm(); onPin(pin) }
            else -> { first = null; error = R.string.pins_differ }
        }
    }
    val title = when {
        mode == PinMode.UNLOCK -> R.string.enter_pin
        first == null -> R.string.choose_pin
        else -> R.string.pin_again
    }
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(Size.topBar).padding(horizontal = Space.screen), contentAlignment = Alignment.CenterStart) {
            if (onClose != null) TonalIconButton(painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose)
        }
        Cookie(painterResource(R.drawable.ic_lock_regular), Modifier.size(Size.pinCookie))
        Spacer(Modifier.height(Space.card))
        Text(stringResource(title), style = BurrowType.title, color = c.onSurface)
        Text(
            when {
                waitSeconds > 0 -> stringResource(R.string.wait_seconds, waitSeconds)
                error != null -> stringResource(error!!)
                else -> " "
            },
            style = BurrowType.body, color = if (error != null || waitSeconds > 0) c.error else c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.card))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.card), modifier = Modifier.semantics { contentDescription = "${entered.length} / $PIN_LENGTH" }) {
            repeat(PIN_LENGTH) { i ->
                Box(Modifier.size(Space.card).clip(Shapes.pill).then(if (i < entered.length) Modifier.background(c.primary) else Modifier.border(Space.group / 2, c.outline, Shapes.pill)))
            }
        }
        Spacer(Modifier.height(Space.sheet + Space.small))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (canBio) "bio" else "", "0", "del")
        Column(verticalArrangement = Arrangement.spacedBy(Space.card)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sheet + Space.small)) {
                    row.forEach { k ->
                        val enabled = waitSeconds == 0 && k.isNotEmpty()
                        Box(
                            Modifier.size(Size.pinKey).clip(Shapes.pill).background(if (k.isEmpty()) androidx.compose.ui.graphics.Color.Transparent else c.surfaceContainerLow)
                                .then(if (enabled) Modifier.clickable(role = Role.Button) {
                                    haptics.tick()
                                    when (k) {
                                        "del" -> entered = entered.dropLast(1)
                                        "bio" -> Biometrics.ask(context, unlockTitle, usePin, onUnlocked)
                                        else -> if (entered.length < PIN_LENGTH) { entered += k; if (entered.length == PIN_LENGTH) submit(entered) }
                                    }
                                } else Modifier)
                                .then(when (k) {
                                    "del" -> Modifier.semantics { contentDescription = context.getString(R.string.key_delete) }
                                    "bio" -> Modifier.semantics { contentDescription = context.getString(R.string.use_fingerprint) }
                                    else -> Modifier
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(painterResource(R.drawable.ic_backspace_regular), null, tint = c.onSurface)
                                "bio" -> Icon(painterResource(R.drawable.ic_fingerprint_regular), null, tint = c.onSurface)
                                "" -> Unit
                                else -> CappedFontScale { Text(k, style = BurrowType.keypad, color = c.onSurface) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PIN_LENGTH = 4

/** App lock (70): the switch, Change PIN, Lock after, Hide amounts in notifications. */
@Composable
fun AppLockScreen(
    settings: Settings,
    onBack: () -> Unit,
    onSetPin: () -> Unit,
    onLock: (Boolean) -> Unit,
    onLockAfter: (Int) -> Unit,
    onHideInNotifications: (Boolean) -> Unit,
) {
    var lockAfter by rememberSaveable { mutableStateOf(false) }
    DetailFrame(onBack, stringResource(R.string.app_lock)) {
        ListGroup {
            ListRow(
                stringResource(R.string.app_lock), 0, 1, supporting = stringResource(R.string.ask_pin_or_fingerprint),
                lead = { IconCircle(painterResource(R.drawable.ic_fingerprint_duotone)) },
                trailing = { BurrowSwitch(settings.appLock, { on -> if (on && !settings.hasPin) onSetPin() else onLock(on) }) },
            )
        }
        Spacer(Modifier.height(Space.block))
        ListGroup {
            ListRow(stringResource(if (settings.hasPin) R.string.change_pin else R.string.set_pin), 0, 3, lead = { IconCircle(painterResource(R.drawable.ic_pin_duotone)) }, onClick = onSetPin)
            ListRow(stringResource(R.string.lock_after), 1, 3, supporting = lockAfterLabel(settings.lockAfterSeconds), lead = { IconCircle(painterResource(R.drawable.ic_timer_duotone)) }, onClick = { lockAfter = true })
            ListRow(
                stringResource(R.string.hide_in_notifications), 2, 3, lead = { IconCircle(painterResource(R.drawable.ic_notifications_off_duotone)) },
                trailing = { BurrowSwitch(settings.hideInNotifications, onHideInNotifications) },
            )
        }
    }
    if (lockAfter) {
        BurrowSheet({ lockAfter = false }) {
            SheetHeader(stringResource(R.string.lock_after), { lockAfter = false })
            SheetBody {
                val opts = listOf(0, 60, 300, 900)
                ListGroup {
                    opts.forEachIndexed { i, sec ->
                        app.burrow.money.ui.components.PickRow(lockAfterLabel(sec), i, opts.size, sec == settings.lockAfterSeconds, { onLockAfter(sec); lockAfter = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun lockAfterLabel(seconds: Int) = when (seconds) {
    0 -> stringResource(R.string.lock_at_once)
    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.lock_after_minutes, seconds / 60, seconds / 60)
}

/** Notifications (66): a switch per kind and a live preview of the capture notification. */
@Composable
fun NotificationSettingsScreen(settings: Settings, onBack: () -> Unit, onSundayReview: () -> Unit = {}, onSet: (String, Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    DetailFrame(onBack, stringResource(R.string.notifications)) {
        val rows = listOf(
            listOf("capture", R.string.n_payment_found, R.string.n_payment_found_line, R.drawable.ic_sms_duotone),
            listOf("bills", R.string.n_bill_reminders, R.string.n_bill_reminders_line, R.drawable.ic_event_available_duotone),
            listOf("budgets", R.string.n_budget_alerts, R.string.n_budget_alerts_line, R.drawable.ic_donut_large_duotone),
            listOf("weekly", R.string.sunday_review, R.string.n_sunday_review_line, R.drawable.ic_event_available_duotone),
            listOf("summary", R.string.n_monthly_summary, R.string.n_monthly_summary_line, R.drawable.ic_summarize_duotone),
        )
        val on = mapOf("capture" to settings.notifyCapture, "bills" to settings.notifyBills, "budgets" to settings.notifyBudgets, "weekly" to settings.notifyWeekly, "summary" to settings.notifySummary)
        ListGroup {
            rows.forEachIndexed { i, r ->
                val key = r[0] as String
                ListRow(
                    stringResource(r[1] as Int), i, rows.size, supporting = stringResource(r[2] as Int),
                    lead = { IconCircle(painterResource(r[3] as Int)) },
                    trailing = { BurrowSwitch(on.getValue(key), { onSet(key, it) }) },
                    // The Sunday review row also opens this week's review.
                    onClick = if (key == "weekly") onSundayReview else null,
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(Space.sectionTop))
        app.burrow.money.ui.components.SectionHeader(stringResource(R.string.preview))
        // A drawing of the capture notification as it looks in the shade.
        Column(Modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainer).padding(Space.card), verticalArrangement = Arrangement.spacedBy(Space.group)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                IconCircle(painterResource(R.drawable.ic_notification), size = Size.iconSmall + Space.small, container = c.primary, content = c.onPrimary)
                Text(stringResource(R.string.burrow_now), style = BurrowType.label, color = c.onSurfaceVariant)
            }
            Text(stringResource(R.string.preview_notification_title, app.burrow.money.ui.LocalMoney.current.plain(240_00)), style = BurrowType.titleSmall, color = c.onSurface)
            Text(stringResource(R.string.preview_notification_line), style = BurrowType.body, color = c.onSurfaceVariant)
            Row(Modifier.padding(top = Space.small), horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                listOf(R.string.cat_food, R.string.cat_groceries).forEach {
                    Box(Modifier.clip(Shapes.pill).background(c.secondaryContainer).padding(horizontal = Space.card, vertical = Space.small)) {
                        Text(stringResource(it), style = BurrowType.button, color = c.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
