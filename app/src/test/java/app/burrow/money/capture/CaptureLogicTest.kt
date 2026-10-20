package app.burrow.money.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLogicTest {
    private val t0 = 1_000_000_000L
    private val sms = Seen(1, "SMS", 240_00, "XX1234", "629184773", t0)

    @Test fun sameAccountWithinTenMinutesIsOne() {
        assertNotNull(findDuplicate("SMS", 240_00, "1234", null, t0 + 5 * 60_000, listOf(sms)))
    }

    @Test fun sameReferenceIsOne() {
        assertNotNull(findDuplicate("SMS", 240_00, "9999", "629184773", t0 + 60_000, listOf(sms)))
    }

    @Test fun appNotificationWithoutAccountMatchesTheSms() {
        assertNotNull(findDuplicate("NOTIFICATION", 240_00, null, null, t0 + 30_000, listOf(sms)))
    }

    @Test fun differentAmountOrLateIsNew() {
        assertNull(findDuplicate("SMS", 241_00, "1234", null, t0, listOf(sms)))
        assertNull(findDuplicate("SMS", 240_00, "1234", null, t0 + 11 * 60_000, listOf(sms)))
        assertNull(findDuplicate("SMS", 240_00, "5678", null, t0 + 60_000, listOf(sms)))
    }

    @Test fun twoCoffeesFromTheSameAppAreTwo() {
        val first = Seen(2, "NOTIFICATION", 240_00, null, null, t0)
        assertNull(findDuplicate("NOTIFICATION", 240_00, null, null, t0 + 60_000, listOf(first)))
    }

    @Test fun references() {
        assertEquals("629184773", referenceIn("... to VPA a@b. UPI Ref 629184773."))
        assertEquals("629184773", referenceIn("trf to X Refno 629184773. If not u"))
        assertEquals("7731229018", referenceIn("from Paytm Wallet. Txn ID 7731229018."))
        assertNull(referenceIn("You paid ₹240 to Third Wave Coffee"))
    }

    @Test fun onlyBusinessMoneyMessagesAreKept() {
        assertTrue(looksLikePayment("AX-KOTAKB", "Rs.2,350.00 debited from A/c XX4471"))
        assertFalse(looksLikePayment("+919812345678", "Rs.500 sent, check pls"))
        assertFalse(looksLikePayment("AX-KOTAKB", "Your statement is ready"))
    }

    @Test fun accountMatching() {
        val accounts = listOf(
            AccountRef(1, "HDFC Savings", "1234", "BANK"),
            AccountRef(2, "ICICI Amazon Pay", "8890", "CARD"),
            AccountRef(3, "Paytm wallet", null, "WALLET"),
            AccountRef(4, "Home loan", null, "LOAN"),
        )
        assertEquals(2L, matchAccount("XX8890", "ICICI Bank", accounts))
        assertEquals(1L, matchAccount("XX9999", "HDFC Bank", accounts))
        assertEquals(3L, matchAccount("Paytm Wallet", "Paytm", accounts))
        assertEquals(1L, matchAccount(null, "Google Pay", accounts))
    }
}
