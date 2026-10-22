package app.burrow.money.ui.importer

import app.burrow.money.ui.components.CenterTitleHeader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.burrow.money.AppContainer
import app.burrow.money.R
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.ImportPlan
import app.burrow.money.data.minorDigits
import app.burrow.money.domain.ImportField
import app.burrow.money.domain.BudgetPeriod
import app.burrow.money.domain.ImportSource
import app.burrow.money.domain.Importer
import app.burrow.money.domain.ParsedImport
import app.burrow.money.ui.components.BurrowButton
import app.burrow.money.ui.components.BurrowSheet
import app.burrow.money.ui.components.BurrowSwitch
import app.burrow.money.ui.components.ButtonKind
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.ChipWrap
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.LetterCircle
import app.burrow.money.ui.components.ListGroup
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.SectionHeader
import app.burrow.money.ui.components.SheetBody
import app.burrow.money.ui.components.SheetHeader
import app.burrow.money.ui.more.rememberRestoreFlow
import app.burrow.money.ui.pickers.AccountSheet
import app.burrow.money.ui.pickers.accountTypeLabel
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.pickers.CategorySheet
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The name an import source goes by on screen. */
@Composable
fun sourceName(s: ImportSource) = stringResource(
    when (s) {
        ImportSource.CASHEW -> R.string.app_cashew
        ImportSource.WALNUT -> R.string.app_walnut
        ImportSource.MONEY_MANAGER -> R.string.app_money_manager
        ImportSource.CSV -> R.string.a_spreadsheet
    },
)

/**
 * Import or export (10): bring history from Cashew, Walnut, Money Manager or any spreadsheet; restore
 * a Burrow backup; export everything to CSV. Files are read on the phone.
 */
@Composable
fun ImportScreen(app: AppContainer, onBack: () -> Unit, onPicked: (ImportSource, Uri) -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val source = pending?.let { runCatching { ImportSource.valueOf(it) }.getOrNull() }
        if (uri != null && source != null) onPicked(source, uri)
        pending = null
    }
    val restore = rememberRestoreFlow(app) { snackbar.show(context.getString(R.string.restored)) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(restore) }
    val exported = stringResource(R.string.csv_exported)
    val failed = stringResource(R.string.csv_failed)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch {
            val ok = runCatching {
                val text = app.csv.export()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream") }
            }.isSuccess
            snackbar.show(if (ok) exported else failed)
        }
    }
    fun choose(s: ImportSource) {
        pending = s.name
        pick.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
    }
    DetailFrame(onBack, stringResource(R.string.import_export)) {
        CenterTitleHeader(painterResource(R.drawable.ic_import_duotone), stringResource(R.string.bring_history), line = stringResource(R.string.bring_history_line))
        SectionHeader(stringResource(R.string.from_another_app))
        ListGroup {
            ListRow(stringResource(R.string.app_cashew), 0, 4, supporting = stringResource(R.string.cashew_how), lead = { LetterCircle("C") }, onClick = { choose(ImportSource.CASHEW) })
            ListRow(stringResource(R.string.app_walnut), 1, 4, supporting = stringResource(R.string.walnut_how), lead = { LetterCircle("W") }, onClick = { choose(ImportSource.WALNUT) })
            ListRow(stringResource(R.string.app_money_manager), 2, 4, supporting = stringResource(R.string.money_manager_how), lead = { LetterCircle("M") }, onClick = { choose(ImportSource.MONEY_MANAGER) })
            ListRow(stringResource(R.string.any_spreadsheet), 3, 4, supporting = stringResource(R.string.any_spreadsheet_line), lead = { IconCircle(painterResource(R.drawable.ic_table_duotone)) }, onClick = { choose(ImportSource.CSV) })
        }
        SectionHeader(stringResource(R.string.from_burrow))
        ListGroup {
            ListRow(stringResource(R.string.restore_backup), 0, 2, supporting = stringResource(R.string.replaces_phone), lead = { IconCircle(painterResource(R.drawable.ic_history_duotone)) }, onClick = { pickBackup.launch(arrayOf("*/*")) })
            ListRow(stringResource(R.string.csv_export), 1, 2, supporting = stringResource(R.string.csv_export_line), lead = { IconCircle(painterResource(R.drawable.ic_export_duotone)) }, onClick = { export.launch("burrow-${LocalDate.now()}.csv") })
        }
    }
}

/** What Check before import shows. */
@Immutable
sealed interface ImportState {
    data object Loading : ImportState
    data object Failed : ImportState
    data class Ready(
        val parsed: ParsedImport,
        /** Their category → Burrow category (null = nothing like it here: made as a new category). */
        val categoryFor: Map<String, Long?>,
        /** Their category → is it used for money in (so the sheet offers income categories). */
        val incomeNames: Set<String>,
        /** Their account → Burrow account (null = made as a new account). */
        val accountFor: Map<String, Long?>,
        /** Where rows without an account go; only asked when some rows have none. */
        val fallbackAccount: Long?,
        val skip: Boolean,
        val duplicates: Int,
        val categories: List<CategoryEntity>,
        val accounts: List<AccountWithBalance>,
        /** Spent this month by category, for the category sheet. */
        val spent: Map<Long, Long> = emptyMap(),
    ) : ImportState {
        val toImport get() = parsed.rows.size - if (skip) duplicates else 0
        val matched get() = categoryFor.count { it.value != null }
        /** Their names made as new categories, most used first (the order their colours are given in). */
        val newCategoryNames get() = parsed.categories.entries.sortedByDescending { it.value }.map { it.key }.filter { categoryFor[it] == null }
        val needsFallback get() = parsed.rows.any { it.account.isNullOrBlank() }
        val canRun get() = toImport > 0 && (!needsFallback || fallbackAccount != null)
    }
}

/** Check before import (11): reads the file, matches names, remembers the choices for next time. */
class ImportCheckViewModel(private val app: AppContainer, val source: ImportSource, private val uri: Uri) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Loading)
    val state: StateFlow<ImportState> = _state
    private var text: String = ""
    private var main = "INR"

    init {
        viewModelScope.launch {
            val ok = runCatching {
                text = withContext(Dispatchers.IO) {
                    app.context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("no stream")
                }
            }.isSuccess
            main = app.settings.settings.first().mainCurrency
            if (!ok) _state.value = ImportState.Failed else read(null)
        }
    }

    private suspend fun read(columns: Map<ImportField, Int>?) {
        val parsed = withContext(Dispatchers.Default) { Importer.parse(text, source, { code -> minorDigits(code ?: main) }, columns) }
        // Nothing readable: an empty file, or another app's file without one payment Burrow understands.
        if (parsed.headers.isEmpty() || (parsed.rows.isEmpty() && columns == null && source != ImportSource.CSV)) { _state.value = ImportState.Failed; return }
        val cats = app.money.categories.first()
        val accounts = app.money.accounts.first()
        val saved = app.next.importMaps(source.name)
        val incomeNames = parsed.incomeCategories
        val categoryFor = parsed.categories.keys.associateWith { name ->
            val kind = if (name in incomeNames) CategoryKind.INCOME else CategoryKind.EXPENSE
            val ofKind = cats.filter { it.kind == kind }
            saved.firstOrNull { it.kind == "CATEGORY" && it.theirName == name }?.targetId?.takeIf { id -> ofKind.any { it.id == id } }
                ?: ofKind.firstOrNull { it.name.equals(name.trim(), true) }?.id
                ?: Importer.guessIcon(name, kind == CategoryKind.INCOME)?.let { icon -> ofKind.firstOrNull { it.icon == icon }?.id }
        }
        // Sample accounts go with the first real entry, so nothing is matched onto them; and only the
        // same name counts ("ICICI" is not "ICICI Amazon Pay"): anything else becomes a new account.
        val real = accounts.filter { !it.account.isSample }
        val pay = real.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS }
        val accountFor = parsed.accounts.keys.associateWith { name ->
            saved.firstOrNull { it.kind == "ACCOUNT" && it.theirName == name }?.targetId?.takeIf { id -> real.any { it.account.id == id } }
                ?: real.firstOrNull { it.account.name.trim().equals(name.trim(), true) }?.account?.id
        }
        val dups = app.next.duplicates(parsed.rows).size
        val spent = app.money.spentByCategory(BudgetPeriod.containing(LocalDate.now(), app.settings.settings.first().monthStartDay)).first()
        val before = _state.value as? ImportState.Ready
        _state.value = ImportState.Ready(
            parsed, categoryFor, incomeNames, accountFor,
            before?.fallbackAccount ?: pay.firstOrNull()?.account?.id, before?.skip ?: true, dups, cats, real, spent,
        )
    }

    private fun ready(change: (ImportState.Ready) -> ImportState.Ready) = _state.update { (it as? ImportState.Ready)?.let(change) ?: it }

    fun setCategory(names: Collection<String>, id: Long) = ready { r -> r.copy(categoryFor = r.categoryFor + names.associateWith { id }) }
    fun setAccount(name: String, id: Long) = ready { r -> r.copy(accountFor = r.accountFor + (name to id)) }
    fun setFallback(id: Long) = ready { it.copy(fallbackAccount = id) }
    fun setSkip(on: Boolean) = ready { it.copy(skip = on) }

    /** Any spreadsheet: the user says which column holds what; the file is read again. */
    fun setColumn(field: ImportField, index: Int?) {
        val r = _state.value as? ImportState.Ready ?: return
        val cols = r.parsed.columns.toMutableMap().apply { if (index == null) remove(field) else { entries.removeAll { it.value == index }; put(field, index) } }
        viewModelScope.launch { read(cols) }
    }

    fun run(onDone: (Int) -> Unit) {
        val r = _state.value as? ImportState.Ready ?: return
        if (!r.canRun) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newCats = r.newCategoryNames.withIndex().associate { (i, name) -> name to newCategory(name, name in r.incomeNames, r.categories, i) }
            val newAccounts = r.accountFor.filterValues { it == null }.keys.toList().let { names ->
                names.withIndex().associate { (i, name) -> name to newAccount(name, main, now, r.accounts.size + i) }
            }
            val n = app.next.runImport(
                ImportPlan(
                    source.name, r.parsed.rows,
                    r.categoryFor.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
                    r.accountFor.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
                    r.fallbackAccount, r.skip, newCats, newAccounts,
                ),
            )
            onDone(n)
        }
    }
}

/** A category made from their name: the icon its name suggests, a colour few others use. */
internal fun newCategory(name: String, income: Boolean, existing: List<CategoryEntity>, index: Int): CategoryEntity {
    val icon = Importer.guessIcon(name, income) ?: "category"
    // The [index]-th new one takes the [index]-th least used hue, so new ones differ from each other.
    val used = existing.groupingBy { it.colorHue }.eachCount()
    val hue = NEW_HUES.sortedBy { used[it] ?: 0 }[index % NEW_HUES.size]
    val kind = if (income) CategoryKind.INCOME else CategoryKind.EXPENSE
    return CategoryEntity(name = name.trim().take(40), kind = kind, icon = icon, colorHue = hue, sortOrder = existing.count { it.kind == kind } + (if (income) 100 else 0))
}

/** An account made from their name: its kind from the name ("Cash", "… card", "Paytm"), in the main currency. */
internal fun newAccount(name: String, currency: String, now: Long, order: Int): AccountEntity {
    val n = name.lowercase()
    val type = when {
        "cash" in n -> AccountType.CASH
        "card" in n || "credit" in n -> AccountType.CARD
        listOf("wallet", "paytm", "phonepe", "gpay", "google pay", "amazon pay", "mobikwik").any { it in n } -> AccountType.WALLET
        else -> AccountType.BANK
    }
    val icon = when (type) {
        AccountType.CASH -> "payments"
        AccountType.CARD -> "credit_card"
        AccountType.WALLET -> "account_balance_wallet"
        else -> "account_balance"
    }
    return AccountEntity(
        name = name.trim().take(40), type = type, currency = currency, openingBalance = 0,
        colorHue = NEW_HUES[order % NEW_HUES.size], icon = icon, sortOrder = order, createdAt = now,
        // Cards start out of net worth: their dues are already in spending (CLAUDE.md).
        includeInNetWorth = type != AccountType.CARD,
    )
}

/** Category hues new ones are drawn from (the defaults' palette). */
private val NEW_HUES = listOf(28f, 140f, 215f, 318f, 192f, 50f, 10f, 275f, 168f, 245f, 345f, 95f)

private sealed interface CheckSheet {
    data class Category(val names: List<String>) : CheckSheet
    data class Account(val name: String?) : CheckSheet
    data class Column(val field: ImportField) : CheckSheet
}

/**
 * Check before import (11): how many and from when, category mapping (unmatched ones in red, they
 * go to Other unless chosen), account mapping, the columns (Any spreadsheet), "Skip ones already
 * here", and one button "Import N transactions".
 */
@Composable
fun ImportCheckScreen(vm: ImportCheckViewModel, onBack: () -> Unit, onDone: (Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = MaterialTheme.colorScheme
    var sheet by remember { mutableStateOf<CheckSheet?>(null) }
    val from = sourceName(vm.source)
    val s = state
    DetailFrame(
        onBack, stringResource(R.string.check_before_import),
        bottom = if (s is ImportState.Ready) ({
            val ok = s.canRun
            BurrowButton(
                pluralStringResource(R.plurals.import_n, s.toImport, s.toImport), { if (ok) vm.run(onDone) },
                Modifier.fillMaxWidth(), if (ok) ButtonKind.FILLED else ButtonKind.OFF, height = Size.buttonBottom,
            )
        }) else null,
    ) {
        when (s) {
            ImportState.Loading -> Unit
            ImportState.Failed -> EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_upload_file_regular)) },
                title = stringResource(R.string.nothing_to_import),
                line = stringResource(R.string.nothing_to_import_line, from),
                button = { BurrowButton(stringResource(R.string.pick_another_file), onBack, kind = ButtonKind.TONAL) },
            )
            is ImportState.Ready -> {
                val p = s.parsed
                val month = remember { DateTimeFormatter.ofPattern("MMM yyyy") }
                CenterHeader(
                    icon = { IconCircle(painterResource(R.drawable.ic_import_duotone), size = Size.iconCircleBig) },
                    title = stringResource(R.string.transactions_from, from),
                    value = "%,d".format(p.rows.size),
                    line = listOfNotNull(
                        p.first?.let { f -> p.last?.let { l -> if (f.year == l.year && f.month == l.month) month.format(f) else "${month.format(f)} – ${month.format(l)}" } },
                        p.accounts.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.n_accounts, it, it) },
                        p.skipped.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.n_lines_skipped, it, it) },
                    ).joinToString(" · ").ifEmpty { null },
                )
                if (vm.source == ImportSource.CSV) {
                    SectionHeader(stringResource(R.string.columns), meta = stringResource(R.string.tap_to_change))
                    val fields = listOf(ImportField.DATE to R.string.date, ImportField.AMOUNT to R.string.amount, ImportField.PAYEE to R.string.payee, ImportField.CATEGORY to R.string.category, ImportField.ACCOUNT to R.string.account, ImportField.TYPE to R.string.column_type)
                    ListGroup {
                        fields.forEachIndexed { i, (field, label) ->
                            val header = p.columns[field]?.let { p.headers.getOrNull(it) }
                            val needed = field == ImportField.DATE || (field == ImportField.AMOUNT && ImportField.DEBIT !in p.columns && ImportField.CREDIT !in p.columns)
                            ListRow(
                                stringResource(label), i, fields.size,
                                supporting = header?.let { "“$it”" } ?: stringResource(if (needed) R.string.choose_a_column else R.string.not_in_file),
                                supportingColor = if (header == null && needed) c.error else c.onSurfaceVariant,
                                onClick = { sheet = CheckSheet.Column(field) },
                            )
                        }
                    }
                }
                if (p.categories.isNotEmpty()) {
                    val names = p.categories.entries.sortedByDescending { it.value }.map { it.key }
                    val fresh = names.count { s.categoryFor[it] == null }
                    SectionHeader(
                        stringResource(R.string.categories),
                        meta = if (fresh == 0) stringResource(R.string.n_of_n_matched, s.matched, s.categoryFor.size) else stringResource(R.string.n_matched_n_new, s.matched, fresh),
                    )
                    // Matched first, then the ones made new; tap any to put it in another category.
                    val ordered = names.filter { s.categoryFor[it] != null } + names.filter { s.categoryFor[it] == null }
                    ListGroup {
                        ordered.forEachIndexed { i, name ->
                            val cat = s.categories.firstOrNull { it.id == s.categoryFor[name] }
                            val count = p.categories[name] ?: 0
                            if (cat != null) ListRow(
                                cat.name, i, ordered.size, supporting = stringResource(R.string.their_name_in, name, from),
                                lead = { CategoryCircle(cat.icon, cat.colorHue) }, onClick = { sheet = CheckSheet.Category(listOf(name)) },
                            ) else {
                                val made = remember(name, s.categories) { newCategory(name, name in s.incomeNames, s.categories, s.newCategoryNames.indexOf(name)) }
                                ListRow(
                                    name, i, ordered.size,
                                    supporting = stringResource(R.string.dot_join, stringResource(R.string.new_category), pluralStringResource(R.plurals.n_transactions, count, count)),
                                    lead = { CategoryCircle(made.icon, made.colorHue) }, onClick = { sheet = CheckSheet.Category(listOf(name)) },
                                )
                            }
                        }
                    }
                }
                SectionHeader(stringResource(R.string.accounts))
                ListGroup {
                    val theirs = p.accounts.entries.sortedByDescending { it.value }.map { it.key }
                    val n = theirs.size + if (s.needsFallback) 1 else 0
                    theirs.forEachIndexed { i, name ->
                        val a = s.accounts.firstOrNull { it.account.id == s.accountFor[name] }?.account
                        if (a != null) ListRow(
                            a.name, i, n, supporting = stringResource(R.string.their_name_in, name, from),
                            lead = { CategoryCircle(a.icon, a.colorHue) }, onClick = { sheet = CheckSheet.Account(name) },
                        ) else {
                            val made = remember(name) { newAccount(name, "", 0, s.accounts.size + i) }
                            ListRow(
                                name, i, n, supporting = stringResource(R.string.dot_join, stringResource(R.string.new_account), accountTypeLabel(made.type)),
                                lead = { CategoryCircle(made.icon, made.colorHue) }, onClick = { sheet = CheckSheet.Account(name) },
                            )
                        }
                    }
                    if (s.needsFallback) {
                        val fallback = s.accounts.firstOrNull { it.account.id == s.fallbackAccount }?.account
                        ListRow(
                            fallback?.name ?: stringResource(R.string.choose_account), theirs.size, n,
                            supporting = stringResource(if (theirs.isEmpty()) R.string.all_go_here else R.string.others_go_here),
                            supportingColor = if (fallback == null) c.error else c.onSurfaceVariant,
                            lead = { CategoryCircle(fallback?.icon, fallback?.colorHue) }, onClick = { sheet = CheckSheet.Account(null) },
                        )
                    }
                }
                ListGroup(Modifier.padding(top = Space.block)) {
                    ListRow(
                        stringResource(R.string.skip_already_here), 0, 1,
                        supporting = pluralStringResource(R.plurals.same_day_amount_payee, s.duplicates, s.duplicates),
                        lead = { IconCircle(painterResource(R.drawable.ic_content_copy_duotone)) },
                        trailing = { BurrowSwitch(s.skip, vm::setSkip) },
                    )
                }
            }
        }
    }
    val r = s as? ImportState.Ready ?: return
    when (val sh = sheet) {
        is CheckSheet.Category -> {
            val income = sh.names.all { it in r.incomeNames }
            CategorySheet(
                r.categories.filter { it.kind == if (income) CategoryKind.INCOME else CategoryKind.EXPENSE }, r.spent,
                sh.names.singleOrNull()?.let { r.categoryFor[it] }, { vm.setCategory(sh.names, it.id); sheet = null }, { sheet = null },
            )
        }
        is CheckSheet.Account -> AccountSheet(
            r.accounts.filter { it.account.type != AccountType.LOAN && it.account.type != AccountType.SAVINGS },
            sh.name?.let { r.accountFor[it] } ?: r.fallbackAccount,
            { if (sh.name == null) vm.setFallback(it.account.id) else vm.setAccount(sh.name, it.account.id); sheet = null }, { sheet = null },
        )
        is CheckSheet.Column -> BurrowSheet({ sheet = null }) {
            SheetHeader(stringResource(R.string.which_column), { sheet = null })
            SheetBody {
                val none = stringResource(R.string.not_in_file)
                val options = listOf<Int?>(null) + r.parsed.headers.indices
                ChipWrap(options, { i -> i?.let { r.parsed.headers[it].ifBlank { "#${it + 1}" } } ?: none }, { it == r.parsed.columns[sh.field] }) { i ->
                    vm.setColumn(sh.field, i); sheet = null
                }
            }
        }
        null -> Unit
    }
}
