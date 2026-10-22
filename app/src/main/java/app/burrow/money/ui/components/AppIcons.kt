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
    "fastfood" -> R.drawable.ic_fastfood_duotone
    "local_bar" -> R.drawable.ic_local_bar_duotone
    "vegetables" -> R.drawable.ic_vegetables_duotone
    "delivery" -> R.drawable.ic_delivery_duotone
    "two_wheeler" -> R.drawable.ic_two_wheeler_duotone
    "taxi" -> R.drawable.ic_taxi_duotone
    "train" -> R.drawable.ic_train_duotone
    "bus" -> R.drawable.ic_bus_duotone
    "hotel" -> R.drawable.ic_hotel_duotone
    "apartment" -> R.drawable.ic_apartment_duotone
    "water" -> R.drawable.ic_water_duotone
    "cooking_gas" -> R.drawable.ic_cooking_gas_duotone
    "smartphone" -> R.drawable.ic_smartphone_duotone
    "wifi" -> R.drawable.ic_wifi_duotone
    "tv" -> R.drawable.ic_tv_duotone
    "repairs" -> R.drawable.ic_repairs_duotone
    "house_help" -> R.drawable.ic_house_help_duotone
    "shoes" -> R.drawable.ic_shoes_duotone
    "jewellery" -> R.drawable.ic_jewellery_duotone
    "salon" -> R.drawable.ic_salon_duotone
    "medicine" -> R.drawable.ic_medicine_duotone
    "hospital" -> R.drawable.ic_hospital_duotone
    "games" -> R.drawable.ic_games_duotone
    "tickets" -> R.drawable.ic_tickets_duotone
    "books" -> R.drawable.ic_books_duotone
    "toys" -> R.drawable.ic_toys_duotone
    "volunteer_activism" -> R.drawable.ic_volunteer_activism_duotone
    "loan_emi" -> R.drawable.ic_loan_emi_duotone
    "insurance" -> R.drawable.ic_insurance_duotone
    "tax" -> R.drawable.ic_tax_duotone
    "investment" -> R.drawable.ic_investment_duotone
    "gold" -> R.drawable.ic_gold_duotone
    "autorenew" -> R.drawable.ic_autorenew_duotone
    "gadgets" -> R.drawable.ic_gadgets_duotone
    "doctor" -> R.drawable.ic_doctor_duotone
    "plants" -> R.drawable.ic_plants_duotone
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

/**
 * Icons a category can have (Edit category, 62), in picker order: grouped by where money goes, so a
 * row reads as one kind of spending.
 */
val CATEGORY_ICONS = listOf(
    // Food and drink
    "restaurant", "coffee", "fastfood", "local_bar", "shopping_cart", "vegetables", "delivery",
    // Getting around
    "directions_car", "two_wheeler", "local_gas_station", "taxi", "train", "bus", "flight", "hotel",
    // Home and bills
    "home", "apartment", "bolt", "water", "cooking_gas", "smartphone", "wifi", "tv", "repairs", "house_help",
    // Shopping and care
    "local_mall", "checkroom", "shoes", "jewellery", "salon", "laptop_mac", "gadgets",
    // Health
    "favorite", "medicine", "hospital", "doctor", "fitness_center",
    // Fun and learning
    "movie", "games", "tickets", "books", "school",
    // Family and giving
    "child_care", "toys", "pets", "plants", "redeem", "volunteer_activism",
    // Money
    "payments", "account_balance", "credit_card", "loan_emi", "insurance", "tax", "investment", "gold", "savings", "percent", "autorenew",
    "category",
)
