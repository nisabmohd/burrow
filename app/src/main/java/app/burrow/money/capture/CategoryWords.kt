package app.burrow.money.capture

/*
 * Words in a payment message that say what it was for (docs/CAPTURE.md § Category words): "medicines",
 * "petrol", "Swiggy". Pure and unit-tested. A group points at a default category by its icon name, so
 * it still finds the category after the user renames it.
 */

/** One kind of spending: the default category's icon, plain words (also used as the expense name) and brands (category only). */
data class WordGroup(val icon: String, val words: List<String>, val brands: List<String> = emptyList())

/** What the words in a message said: the category's icon and, for a plain word, the word as written ("Petrol"). */
data class WordMatch(val icon: String, val name: String?)

val CATEGORY_WORDS: List<WordGroup> = listOf(
    WordGroup(
        "directions_car",
        listOf("petrol", "diesel", "fuel", "cng", "parking", "toll", "fastag", "taxi", "cab", "metro"),
        listOf("indian oil", "iocl", "bpcl", "bharat petroleum", "hpcl", "hindustan petroleum", "shell", "uber", "ola", "rapido"),
    ),
    WordGroup(
        "favorite",
        listOf("medicine", "medicines", "medical", "medicals", "pharmacy", "chemist", "hospital", "clinic", "doctor", "diagnostics", "dental"),
        listOf("apollo", "pharmeasy", "1mg", "netmeds", "medplus"),
    ),
    WordGroup(
        "shopping_cart",
        listOf("grocery", "groceries", "supermarket", "kirana", "vegetables", "fruits", "dairy", "milk"),
        listOf("bigbasket", "blinkit", "zepto", "dmart", "instamart", "jiomart", "more retail", "reliance fresh"),
    ),
    WordGroup(
        "restaurant",
        listOf("food", "restaurant", "cafe", "coffee", "bakery", "dhaba", "pizza", "burger", "dining", "lunch", "dinner", "breakfast"),
        listOf("swiggy", "zomato", "starbucks", "dominos", "domino's", "mcdonalds", "mcdonald's", "kfc", "eatsure"),
    ),
    WordGroup(
        "bolt",
        listOf("electricity", "recharge", "broadband", "wifi", "postpaid", "prepaid", "dth", "water bill", "gas bill", "lpg"),
        listOf("airtel", "jio", "vodafone", "bescom", "tata power", "adani electricity", "act fibernet", "tata play"),
    ),
    WordGroup("home", listOf("rent", "house rent", "maintenance")),
    WordGroup(
        "movie",
        listOf("movie", "movies", "cinema", "concert"),
        listOf("bookmyshow", "pvr", "inox", "netflix", "spotify", "hotstar", "prime video", "youtube premium"),
    ),
    WordGroup(
        "flight",
        listOf("flight", "airline", "airport", "train", "railway", "hotel", "bus ticket"),
        listOf("irctc", "indigo", "air india", "akasa", "makemytrip", "goibibo", "cleartrip", "redbus", "oyo", "airbnb"),
    ),
    WordGroup(
        "school",
        listOf("school", "college", "tuition", "course", "books", "university", "exam fee"),
        listOf("udemy", "coursera", "byjus", "unacademy"),
    ),
    WordGroup(
        "local_mall",
        listOf("shopping", "clothing", "apparel", "fashion", "footwear", "electronics"),
        listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "decathlon"),
    ),
    WordGroup("percent", listOf("bank charges", "late fee", "penalty", "annual fee", "convenience fee", "processing fee")),
    // Money in.
    WordGroup("payments", listOf("salary", "payroll")),
)

private val PATTERNS: List<Pair<WordGroup, List<Pair<Regex, Boolean>>>> = CATEGORY_WORDS.map { g ->
    g to (g.words.map { it to true } + g.brands.map { it to false }).map { (w, plain) ->
        // Whole words only: "cab" must not match "vocabulary", "ola" not "cola".
        Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(w).replace(" ", "\\E\\s+\\Q") + "(?![\\p{L}\\p{N}])") to plain
    }
}

/**
 * The first group with a word in [merchant], else in [body] (the merchant says more than the bank's
 * wording). The name is the plain word as the message wrote it, title-cased; brands give no name.
 */
fun categoryWords(merchant: String?, body: String): WordMatch? {
    for (text in listOfNotNull(merchant, body)) {
        var brandHit: WordGroup? = null
        for ((group, patterns) in PATTERNS) {
            for ((re, plain) in patterns) {
                val m = re.find(text) ?: continue
                if (plain) return WordMatch(group.icon, titleCase(m.value))
                if (brandHit == null) brandHit = group
            }
        }
        // A plain word anywhere beats a brand ("Amazon pharmacy" → Health, "Medicines").
        brandHit?.let { return WordMatch(it.icon, null) }
    }
    return null
}

private fun titleCase(s: String) = s.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
