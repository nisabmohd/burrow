package app.burrow.money.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A v3 backup (before cards were their own rows, 186–201) still opens: no "cards" list, and the
 * card account keeps its digits, which restore turns into its card (CARDS_FROM_ACCOUNTS).
 */
class BackupV3Test {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun aV3BackupOpensAndItsCardComesBackFromTheDigits() {
        val v3 = """
            {"version":3,"createdAt":1,"settings":{"mainCurrency":"INR","monthStartDay":1,"theme":"SYSTEM","accentHue":345.0,
             "dynamicColor":false,"font":"LEXEND","textSize":"DEFAULT","homeWidgets":[],"enabledPacks":["in"]},
             "accounts":[{"id":7,"name":"ICICI Amazon Pay","type":"CARD","currency":"INR","openingBalance":0,"colorHue":30.0,
             "icon":"credit_card","sortOrder":0,"createdAt":1,"number":"8890"}],
             "categories":[],"txns":[],"bills":[],"occurrences":[],"budgets":[],"goals":[],"rules":[],
             "splits":[],"favourites":[],"importMaps":[]}
        """.trimIndent()
        val data = json.decodeFromString(BackupData.serializer(), v3)
        assertEquals(3, data.version)
        assertTrue("a v3 file has no card rows", data.cards.isEmpty())
        assertEquals("8890", data.accounts.single().number)
        // Restore runs this after putting the accounts back: one card per card account with digits.
        assertTrue(CARDS_FROM_ACCOUNTS.contains("type = 'CARD'") && CARDS_FROM_ACCOUNTS.contains("number"))
    }
}
