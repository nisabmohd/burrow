package app.burrow.money.ui.favourites

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.burrow.money.R
import app.burrow.money.data.FavouriteView
import app.burrow.money.data.NextRepository
import app.burrow.money.ui.LocalMoney
import app.burrow.money.ui.components.CenterHeader
import app.burrow.money.ui.components.Cookie
import app.burrow.money.ui.components.DragColumn
import app.burrow.money.ui.components.EmptyState
import app.burrow.money.ui.components.IconCircle
import app.burrow.money.ui.components.ListRow
import app.burrow.money.ui.components.LocalSnackbar
import app.burrow.money.ui.components.TonalIconButton
import app.burrow.money.ui.pickers.CategoryCircle
import app.burrow.money.ui.plan.DetailFrame
import app.burrow.money.ui.theme.BurrowType
import app.burrow.money.ui.theme.Size
import app.burrow.money.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favourites (05): the list in order; drag to reorder, ✕ to remove (with Undo). */
class FavouritesViewModel(private val next: NextRepository) : ViewModel() {
    val state: StateFlow<List<FavouriteView>?> = next.favourites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun reorder(list: List<FavouriteView>) = viewModelScope.launch { next.reorderFavourites(list.map { it.favourite.id }) }

    fun remove(id: Long, onDone: (suspend () -> Unit) -> Unit) = viewModelScope.launch { next.removeFavourite(id)?.let(onDone) }
}

/**
 * Favourites (05): centred header with the count and "One tap on New expense saves them", a row per
 * favourite (⠿ handle, category circle, "Chai · ₹20", "Food & drinks · Cash", ✕), then how to add one.
 */
@Composable
fun FavouritesScreen(vm: FavouritesViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val list = state ?: return
    val money = LocalMoney.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    DetailFrame(onBack, stringResource(R.string.favourites)) {
        if (list.isEmpty()) {
            EmptyState(
                shape = { Cookie(painterResource(R.drawable.ic_star_regular)) },
                title = stringResource(R.string.no_favourites),
                line = stringResource(R.string.favourites_how),
            )
            return@DetailFrame
        }
        CenterHeader(
            icon = { IconCircle(painterResource(R.drawable.ic_star_duotone), size = Size.iconCircleBig) },
            title = stringResource(R.string.favourites),
            value = list.size.toString(),
            line = stringResource(R.string.favourites_line),
        )
        Spacer(Modifier.height(Space.block))
        DragColumn(
            list, key = { it.favourite.id }, onMove = vm::reorder,
            moveUpLabel = { context.getString(R.string.move_up, it.favourite.name) }, moveDownLabel = { context.getString(R.string.move_down, it.favourite.name) },
        ) { f, i, n, handle, lifted ->
            val removeLabel = stringResource(R.string.remove_account, f.favourite.name)
            ListRow(
                stringResource(R.string.dot_join, f.favourite.name, money.plain(f.favourite.amount)), i, n,
                supporting = stringResource(R.string.dot_join, f.categoryName, f.accountName),
                lifted = lifted,
                lead = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        handle()
                        CategoryCircle(f.categoryIcon, f.categoryHue)
                    }
                },
                trailing = {
                    TonalIconButton(painterResource(R.drawable.ic_close_regular), removeLabel, {
                        vm.remove(f.favourite.id) { undo -> snackbar.show(context.getString(R.string.favourite_removed), undo) }
                    })
                },
            )
        }
        Text(
            stringResource(R.string.favourites_how), style = BurrowType.body, color = c.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.block, start = Space.small, end = Space.small),
        )
    }
}
