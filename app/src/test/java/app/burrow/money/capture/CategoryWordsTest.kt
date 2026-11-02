package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryWordsTest {
    @Test fun plainWordGivesCategoryAndName() {
        assertEquals(WordMatch("directions_car", "Petrol"), categoryWords(null, "Rs 500 debited from a/c XX12 for PETROL on 02-11"))
        assertEquals(WordMatch("favorite", "Medicines"), categoryWords(null, "Paid Rs.240 for medicines. UPI Ref 123456"))
    }

    @Test fun merchantIsReadBeforeTheBody() {
        assertEquals(WordMatch("favorite", "Pharmacy"), categoryWords("Sai Pharmacy", "Rs 300 spent, food court offer inside"))
    }

    @Test fun brandGivesCategoryButNoName() {
        assertEquals(WordMatch("restaurant", null), categoryWords("SWIGGY", "Rs 240 debited to SWIGGY"))
        assertEquals(WordMatch("directions_car", null), categoryWords("Indian Oil Corp", "Rs 900 spent at Indian Oil Corp"))
    }

    @Test fun plainWordBeatsBrandInTheSameText() {
        assertEquals(WordMatch("favorite", "Medicines"), categoryWords(null, "Rs 450 paid to Amazon for medicines"))
    }

    @Test fun wholeWordsOnly() {
        assertNull(categoryWords("Vocabulary Cola", "Rs 20 paid to Vocabulary Cola"))
        assertNull(categoryWords(null, "Rs 100 sent to rentals@okaxis"))
    }

    @Test fun nothingKnown() {
        assertNull(categoryWords("Ramesh Kumar", "Rs 100 sent to Ramesh Kumar"))
    }
}
