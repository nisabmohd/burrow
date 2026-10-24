package app.burrow.money.ui.update

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.burrow.money.BuildConfig
import app.burrow.money.R
import app.burrow.money.data.update.AppUpdate
import app.burrow.money.data.update.DownloadState
import app.burrow.money.data.update.UpdateCheck
import app.burrow.money.data.update.UpdateManager
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetFooter
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.components.WavyProgress
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.launch

/*
 * Updates (github build only, as Sprout): About's "Check for updates" row, the once-a-day check on
 * start, and the Update sheet (download with progress, then Android's installer). Play and F-Droid
 * update the app themselves; their row says so or opens the store.
 */

/** The version without the build suffix: "0.1.1" rather than "0.1.1-github". */
private val currentVersion get() = BuildConfig.VERSION_NAME.substringBefore('-')

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "Update available": the new version, up to five What's new lines, then Update → Install, or Later. */
@Composable
fun UpdateSheet(update: AppUpdate, updates: UpdateManager, onLater: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var state by remember { mutableStateOf<DownloadState?>(null) }
    // Checked again on return from "Install unknown apps", so the hint goes once it's allowed.
    var canInstall by remember { mutableStateOf(updates.canInstall()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = updates.canInstall() }

    BurrowSheet(onDismiss) {
        SheetHeader(stringResource(R.string.update_available), onDismiss, stringResource(R.string.update_version_line, update.version, currentVersion))
        SheetBody {
            if (update.notes.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(Shapes.surface).background(c.surfaceContainerLow).padding(Space.card),
                    verticalArrangement = Arrangement.spacedBy(Space.small),
                ) {
                    Text(stringResource(R.string.whats_new), style = BurrowType.titleSmall, color = c.onSurface)
                    update.notes.forEach { line ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                            Box(Modifier.padding(top = Space.small).size(Size.bulletDot).clip(Shapes.pill).background(c.primary))
                            Text(line, style = BurrowType.body, color = c.onSurface)
                        }
                    }
                }
            }
            when (val s = state) {
                is DownloadState.Progress -> Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    WavyProgress(s.fraction ?: 0f, c.primary)
                    Text(
                        s.fraction?.let { stringResource(R.string.downloading_percent, (it * 100).toInt()) } ?: stringResource(R.string.downloading),
                        style = BurrowType.body, color = c.onSurfaceVariant,
                    )
                }
                is DownloadState.Ready -> if (!canInstall) Text(stringResource(R.string.allow_installs_hint), style = BurrowType.body, color = c.onSurfaceVariant)
                DownloadState.Failed -> Text(stringResource(R.string.update_failed), style = BurrowType.body, color = c.error)
                null -> Unit
            }
        }
        SheetFooter {
            BurrowButton(stringResource(R.string.later), onLater, Modifier.weight(1f), ButtonKind.TONAL, height = Size.buttonBottom)
            when (val s = state) {
                is DownloadState.Progress -> BurrowButton(stringResource(R.string.update), {}, Modifier.weight(1f), ButtonKind.OFF, height = Size.buttonBottom)
                is DownloadState.Ready -> BurrowButton(stringResource(R.string.install), {
                    if (updates.canInstall()) updates.install(s.apk) else updates.openInstallPermission()
                }, Modifier.weight(1f), height = Size.buttonBottom)
                DownloadState.Failed -> BurrowButton(stringResource(R.string.open_release_page), { open(context, update.pageUrl); onDismiss() }, Modifier.weight(1f), height = Size.buttonBottom)
                null -> BurrowButton(stringResource(R.string.update), {
                    if (update.apkUrl == null) { open(context, update.pageUrl); onDismiss() } else scope.launch { updates.download(update).collect { state = it } }
                }, Modifier.weight(1f), height = Size.buttonBottom)
            }
        }
    }
}

private enum class CheckState { IDLE, CHECKING, LATEST, ERROR }

/**
 * About's "Check for updates" row ([index] of [count]). github: Checking… → "You're on the latest
 * version" or the Update sheet. Play: opens the store listing once Burrow is listed there. F-Droid:
 * says where its updates come from. Null [count] means this build shows no row: see [updateRowShown].
 */
@Composable
fun UpdateRow(updates: UpdateManager, index: Int, count: Int) {
    val context = LocalContext.current
    val lead: @Composable () -> Unit = { IconCircle(painterResource(R.drawable.ic_download_duotone)) }
    when {
        updates.enabled -> Unit
        BuildConfig.PLAY_LISTED -> {
            ListRow(stringResource(R.string.check_for_updates), index, count, lead = lead, onClick = { open(context, BuildConfig.PLAY_STORE_URL) })
            return
        }
        else -> {
            ListRow(stringResource(R.string.check_for_updates), index, count, supporting = stringResource(R.string.updates_from_fdroid), lead = lead)
            return
        }
    }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(CheckState.IDLE) }
    var found by remember { mutableStateOf<AppUpdate?>(null) }
    ListRow(
        stringResource(R.string.check_for_updates), index, count,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        supporting = stringResource(
            when (state) {
                CheckState.IDLE -> R.string.updates_from_github
                CheckState.CHECKING -> R.string.checking_updates
                CheckState.LATEST -> R.string.up_to_date
                CheckState.ERROR -> R.string.update_check_failed
            },
        ),
        supportingColor = if (state == CheckState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        lead = lead,
        onClick = {
            if (state == CheckState.CHECKING) return@ListRow
            state = CheckState.CHECKING
            scope.launch {
                state = when (val r = updates.check()) {
                    is UpdateCheck.Available -> { found = r.update; CheckState.IDLE }
                    UpdateCheck.UpToDate -> CheckState.LATEST
                    UpdateCheck.Failed -> CheckState.ERROR
                }
            }
        },
    )
    found?.let { update ->
        UpdateSheet(update, updates, onLater = { found = null; scope.launch { updates.later(update) } }, onDismiss = { found = null })
    }
}

/** False when this build has nothing to show in About (Play before it is listed). */
fun updateRowShown(updates: UpdateManager): Boolean = updates.enabled || BuildConfig.PLAY_LISTED || !BuildConfig.PLAY_STORE

/** The automatic check on start (github build): at most once a day, silent on failure, never for a release put off. */
@Composable
fun UpdatePrompt(updates: UpdateManager) {
    if (!updates.enabled) return
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<AppUpdate?>(null) }
    LaunchedEffect(Unit) { found = updates.checkIfDue() }
    found?.let { update ->
        UpdateSheet(update, updates, onLater = { found = null; scope.launch { updates.later(update) } }, onDismiss = { found = null })
    }
}
