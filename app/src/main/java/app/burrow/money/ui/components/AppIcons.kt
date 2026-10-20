package app.burrow.money.ui.components

import androidx.annotation.DrawableRes
import app.burrow.money.R

/*
 * Categories and accounts store an icon by its design name (docs/ICONS.md), e.g. "restaurant".
 * This maps the name to its duotone drawable for icon circles. A plain `when` keeps every drawable
 * referenced in code, so resource shrinking never drops one. Add new names here.
 */
@DrawableRes
fun duotoneIcon(name: String?): Int = when (name) {
    "restaurant" -> R.drawable.ic_restaurant_duotone
    "shopping_cart" -> R.drawable.ic_shopping_cart_duotone
    "directions_car" -> R.drawable.ic_directions_car_duotone
    "local_mall" -> R.drawable.ic_local_mall_duotone
    "bolt" -> R.drawable.ic_bolt_duotone
    "home" -> R.drawable.ic_home_duotone
    "favorite" -> R.drawable.ic_favorite_duotone
    "movie" -> R.drawable.ic_movie_duotone
    "flight" -> R.drawable.ic_flight_duotone
    "school" -> R.drawable.ic_school_duotone
    "redeem" -> R.drawable.ic_redeem_duotone
    "payments" -> R.drawable.ic_payments_duotone
    "percent" -> R.drawable.ic_percent_duotone
    "swap_horiz" -> R.drawable.ic_swap_horiz_duotone
    "account_balance" -> R.drawable.ic_account_balance_duotone
    "credit_card" -> R.drawable.ic_credit_card_duotone
    "account_balance_wallet" -> R.drawable.ic_account_balance_wallet_duotone
    "real_estate_agent" -> R.drawable.ic_real_estate_agent_duotone
    "savings" -> R.drawable.ic_savings_duotone
    "cloud" -> R.drawable.ic_cloud_duotone
    "shield" -> R.drawable.ic_shield_duotone
    "laptop_mac" -> R.drawable.ic_laptop_mac_duotone
    "flag" -> R.drawable.ic_flag_duotone
    "pets" -> R.drawable.ic_pets_duotone
    "fitness_center" -> R.drawable.ic_fitness_center_duotone
    "local_gas_station" -> R.drawable.ic_local_gas_station_duotone
    "child_care" -> R.drawable.ic_child_care_duotone
    "coffee" -> R.drawable.ic_coffee_duotone
    "checkroom" -> R.drawable.ic_checkroom_duotone
    else -> R.drawable.ic_category_duotone
}

/** The icon for a currency in amount fields (₹, $, €, £; a generic coin otherwise). */
@DrawableRes
fun currencyIcon(code: String): Int = when (code) {
    "INR" -> R.drawable.ic_currency_rupee_regular
    "EUR" -> R.drawable.ic_euro_regular
    "GBP" -> R.drawable.ic_currency_pound_regular
    "USD" -> R.drawable.ic_attach_money_regular
    else -> R.drawable.ic_payments_regular
}

/** Icons a category can have (Edit category, 62), in picker order. */
val CATEGORY_ICONS = listOf(
    "restaurant", "shopping_cart", "directions_car", "local_mall", "bolt", "home",
    "favorite", "movie", "flight", "school", "redeem", "category",
    "pets", "fitness_center", "local_gas_station", "child_care", "coffee", "checkroom",
)
