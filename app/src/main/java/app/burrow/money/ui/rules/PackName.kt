package app.burrow.money.ui.rules

import app.burrow.money.capture.BUILT_IN_PACKS
import java.util.Locale

/**
 * A built-in pack's name: its country in the phone's language ("India", "Vereinigtes Königreich").
 * Takes a pack id ("ae") or one of its rule ids ("ae.enbd.card").
 */
fun packName(id: String): String {
    val pack = BUILT_IN_PACKS.firstOrNull { it.id == id.substringBefore('.') } ?: return id
    return Locale.Builder().setRegion(pack.country).build().displayCountry
}
