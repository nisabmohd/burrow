package app.burrow.money.capture

import org.junit.Test

/** Every tap on every word, for each part, must never throw (Teach crashed on a tap). */
class TeachFuzzTest {
    private val engine = RuleEngine()
    private val messages = listOf(
        "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184. Not you? Call 1860-266-2666",
        "Sent Rs.120.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 24/10/26\nRef 123456789012\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        "₹500 paid to Raju (raju@okaxis) 💸 via GPay. UPI Ref: 1234",
        "INR 1.234,56 spent on card ending 9876 at AMAZON* [Mktp] {x} | y $ ^ ok",
        "आपके खाते से ₹ 250.00 डेबिट किया गया। (\"test\") 'x' ... .. , ;",
        "AED 45.00 was spent on your Credit Card ending with 1234 at CARREFOUR. Avl Cr. Limit is AED 12,000.00",
        "Dear Customer, Rs 99 is debited  \t from a/c **55 to \\path\\ (?<amount>x)",
        "",
        "5",
    )

    @Test fun anyTapsBuildWithoutThrowing() {
        for (text in messages) {
            val tokens = (0..text.length).mapNotNull { RuleBuilder.tokenAt(text, it) }.distinct()
            for (a in tokens) {
                if (!RuleBuilder.isAmount(text.substring(a))) continue
                val others = listOf<IntRange?>(null) + tokens
                for (p in others) for (acc in others) for (d in listOf<IntRange?>(null) + tokens.take(6)) {
                    try {
                        val rule = RuleBuilder.build("user.1", "X", "AX-HDFCBK", text, a, p, acc, d)
                        engine.parseWith(rule, "AX-HDFCBK", text)
                    } catch (e: Throwable) {
                        throw AssertionError("text=$text a=${text.substring(a)} p=${p?.let { text.substring(it) }} acc=${acc?.let { text.substring(it) }}", e)
                    }
                }
            }
        }
    }
}
