package app.burrow.money.ui.more

import kotlinx.coroutines.launch
import androidx.core.net.toUri
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.burrow.money.BuildConfig
import app.burrow.money.R
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CATEGORY_ICONS
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ConfirmDialog
import app.burrow.money.ui.components.HeaderIcon
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LabeledField
import app.burrow.money.ui.components.LinkButton
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.PillTextField
import app.burrow.money.ui.components.ScreenTopBar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.CategoryHues
import app.burrow.money.ui.theme.Shapes
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import app.burrow.money.ui.theme.categoryColors

/** Categories (60) and the grid (61): sorted by this month's spending; the New category FAB; tap to edit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(vm: CategoriesViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val money = LocalMoney.current
    var grid by rememberSaveable { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    DetailFrame(onBack, actions = {
        TonalIconButton(
            painterResource(if (grid) R.drawable.ic_format_list_bulleted_regular else R.drawable.ic_grid_view_regular),
            stringResource(if (grid) R.string.show_list else R.string.show_grid), { grid = !grid },
        )
    }) {
        CenterHeader(
            icon = { HeaderIcon(painterResource(R.drawable.ic_category_regular)) },
            title = stringResource(R.string.categories),
            value = s.count.toString(),
            line = stringResource(R.string.spent_this_month, money.plain(s.total)),
        )
        listOf(R.string.spending to s.spending, R.string.type_income to s.income).forEach { (title, lines) ->
            SectionHeader(stringResource(title), meta = lines.size.toString())
            if (grid) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(Space.small), maxItemsInEachRow = 3) {
                    lines.forEach { l ->
                        Column(
                            Modifier.weight(1f).clip(Shapes.surface).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { onEdit(l.category.id) }.padding(vertical = Space.card),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.small),
                        ) {
                            CategoryCircle(l.category.icon, l.category.colorHue, Size.categoryTile)
                            Text(l.category.name.substringBefore(" &").substringBefore(" "), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                    }
                    repeat((3 - lines.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
                }
            } else {
                ListGroup {
                    lines.forEachIndexed { i, l ->
                        ListRow(
                            l.category.name, i, lines.size,
                            supporting = if (l.spent > 0) stringResource(R.string.spent_this_month, money.plain(l.spent)) else stringResource(R.string.nothing_this_month),
                            lead = { CategoryCircle(l.category.icon, l.category.colorHue) },
                            trailing = { if (l.spent > 0) Text("${l.percent}%", style = BurrowType.label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { onEdit(l.category.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Size.fabClearance))
    }
    app.burrow.money.ui.components.AddFab(
        stringResource(R.string.new_category), { onEdit(0) },
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Space.screen),
    )
    }
}

/** Edit category (62): name, colour, icon, Delete (its transactions move to Other). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCategoryScreen(vm: EditCategoryViewModel, onClose: () -> Unit) {
    val formState by vm.form.collectAsStateWithLifecycle()
    val f = formState ?: return
    val c = MaterialTheme.colorScheme
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            painterResource(R.drawable.ic_close_regular), stringResource(R.string.close), onClose,
            title = stringResource(if (vm.id == 0L) R.string.new_category else R.string.edit_category),
        ) {
            BurrowButton(stringResource(R.string.save), { vm.save(onClose) }, kind = if (f.name.isNotBlank()) ButtonKind.FILLED else ButtonKind.OFF)
        }
        FormBody {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryCircle(f.icon, f.hue, Size.iconCircleBig)
            Spacer(Modifier.height(Space.block))
            Text(f.name.ifBlank { stringResource(R.string.new_category) }, style = BurrowType.titleSmall, color = c.onSurface)
        }
        LabeledField(stringResource(R.string.name)) {
            PillTextField(f.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(R.string.category_name_hint), leading = painterResource(R.drawable.ic_edit_note_regular), clearLabel = stringResource(R.string.clear))
        }
        SectionHeader(stringResource(R.string.colour))
        app.burrow.money.ui.components.HuePicker(f.hue, stringResource(R.string.colour), { hue -> vm.edit { it.copy(hue = hue) } })
        SectionHeader(stringResource(R.string.icon))
        app.burrow.money.ui.components.IconPicker(CATEGORY_ICONS, f.icon, f.hue, { icon -> vm.edit { it.copy(icon = icon) } })
        if (vm.id != 0L) {
            BurrowButton(stringResource(R.string.delete_category), { confirm = true }, Modifier.fillMaxWidth().padding(top = Space.block), ButtonKind.DANGER, painterResource(R.drawable.ic_delete_regular))
        }
        }
    }
    if (confirm) {
        ConfirmDialog(
            painterResource(R.drawable.ic_delete_regular), stringResource(R.string.delete_category_title), stringResource(R.string.delete_category_text),
            stringResource(R.string.delete), stringResource(R.string.cancel),
            onConfirm = { confirm = false; vm.delete(onClose) }, onDismiss = { confirm = false },
        )
    }
}

/** The scrolling body under a form's top bar. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FormBody(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    app.burrow.money.ui.components.BlockColumn(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.screen),
        content = content,
    )
}

/** Main currency (67): search, the region's first, every other currency on search. */
@Composable
fun CurrencyScreen(
    current: String,
    onPick: (code: String, moveAccounts: Boolean) -> Unit,
    onBack: () -> Unit,
    /** How many of the user's accounts are in a currency (the old main one gets asked about). */
    accountsIn: suspend (String) -> Int = { 0 },
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // A new main currency while accounts are still in the old one: move them too, or keep them.
    var ask by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    val region = app.burrow.money.data.defaultCurrency(locale)
    val common = remember(region) { (listOf(region, "INR", "USD", "EUR", "GBP", "AED", "SGD")).distinct() }
    val all = remember { java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
    val list = if (query.isBlank()) common else all.filter { code ->
        code.contains(query, true) || runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault("").contains(query, true)
    }
    DetailFrame(onBack, stringResource(R.string.main_currency)) {
        PillTextField(query, { query = it }, stringResource(R.string.search_currencies), leading = painterResource(R.drawable.ic_search_regular), clearLabel = stringResource(R.string.clear))
        Spacer(Modifier.height(Space.block))
        ListGroup {
            list.forEachIndexed { i, code ->
                val name = runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code).replaceFirstChar { it.uppercase() }
                val sym = app.burrow.money.domain.MoneyFormat.symbolFor(code, locale)
                ListRow(
                    name, i, list.size,
                    supporting = listOfNotNull(code, sym.takeIf { it != code }, if (code == region) stringResource(R.string.your_region) else null).joinToString(" · "),
                    selected = code == current,
                    lead = { IconCircle(painterResource(app.burrow.money.ui.components.currencyIcon(code))) },
                    trailing = { if (code == current) Icon(painterResource(R.drawable.ic_check_regular), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Size.icon)) },
                    onClick = {
                        if (code == current) onBack() else scope.launch {
                            val n = accountsIn(current)
                            if (n > 0) ask = code to n else onPick(code, false)
                        }
                    },
                )
            }
        }
        Text(stringResource(R.string.currency_note), style = BurrowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.grid).padding(top = Space.block))
    }
    ask?.let { (code, n) ->
        val from = app.burrow.money.domain.MoneyFormat.symbolFor(current, locale)
        val to = app.burrow.money.domain.MoneyFormat.symbolFor(code, locale)
        app.burrow.money.ui.components.BurrowSheet({ ask = null }) {
            app.burrow.money.ui.components.SheetHeader(
                stringResource(R.string.move_accounts_title, to), { ask = null },
                androidx.compose.ui.res.pluralStringResource(R.plurals.move_accounts_line, n, n, from, to),
            )
            app.burrow.money.ui.components.SheetFooter {
                app.burrow.money.ui.components.BurrowButton(stringResource(R.string.keep_in, from), { ask = null; onPick(code, false) }, Modifier.weight(1f), app.burrow.money.ui.components.ButtonKind.TONAL, height = Size.buttonBottom)
                app.burrow.money.ui.components.BurrowButton(stringResource(R.string.move_to, to), { ask = null; onPick(code, true) }, Modifier.weight(1f), height = Size.buttonBottom)
            }
        }
    }
}

/** About (72): the mark, the version, source, no internet, rate, report, licences. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLicences: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    DetailFrame(onBack, stringResource(R.string.about)) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.sheet), horizontalAlignment = Alignment.CenterHorizontally) {
            app.burrow.money.ui.components.BurrowLogo(Size.logo)
            Spacer(Modifier.height(Space.card))
            Text(stringResource(R.string.app_name), style = BurrowType.headline, color = c.onSurface)
            Text(stringResource(R.string.version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            // The developer name on the Play Store (105).
            Text(stringResource(R.string.made_by), style = BurrowType.body, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        val rows = buildList<Triple<Int, Int?, Pair<Int, (() -> Unit)?>>> {
            add(Triple(R.string.source_code, R.string.source_code_line, R.drawable.ic_code_duotone to { open(BuildConfig.REPO_URL) }))
            add(Triple(R.string.no_internet, R.string.no_internet_line, R.drawable.ic_wifi_off_duotone to null))
            if (BuildConfig.PLAY_LISTED) add(Triple(R.string.rate, null, R.drawable.ic_star_duotone to { open(BuildConfig.PLAY_STORE_URL) }))
            add(Triple(R.string.report_problem, null, R.drawable.ic_bug_report_duotone to { open(BuildConfig.REPO_URL + "/issues") }))
            add(Triple(R.string.licences, null, R.drawable.ic_description_duotone to onLicences))
        }
        ListGroup {
            rows.forEachIndexed { i, (title, line, action) ->
                ListRow(stringResource(title), i, rows.size, supporting = line?.let { stringResource(it) }, lead = { IconCircle(painterResource(action.first)) }, onClick = action.second)
            }
        }
    }
}

/** Everything bundled into the app, with its licence. Keep in step with the version catalog, res/font and the icons. */
private val LICENCES = listOf(
    "AndroidX (Compose, Material 3, Room, DataStore, Navigation, Lifecycle, Activity, Core)" to "Apache License 2.0",
    "Kotlin standard library, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "Phosphor Icons" to "MIT License",
    "Lexend font" to "SIL Open Font License 1.1",
    "Outfit font" to "SIL Open Font License 1.1",
    "Atkinson Hyperlegible font" to "SIL Open Font License 1.1",
    "Space Grotesk font" to "SIL Open Font License 1.1",
)

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    DetailFrame(onBack, stringResource(R.string.licences)) {
        ListGroup { LICENCES.forEachIndexed { i, (name, licence) -> ListRow(name, i, LICENCES.size, supporting = licence) } }
    }
}
