package app.burrow.money.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpiNameTest {
    @Test fun readableIds() {
        assertEquals("Third Wave", upiName("third.wave@okicici"))
        assertEquals("Thirdwave", upiName("thirdwave@okicici"))
        assertEquals("Ramesh Kumar", upiName("ramesh_kumar@ybl"))
    }

    @Test fun phonesAndMachineIdsStay() {
        assertNull(upiName("9876543210@ybl"))
        assertNull(upiName("paytmqr2810050501010@paytm"))
        assertNull(upiName("Third Wave Coffee"))
        assertNull(upiName("@ybl"))
    }

    @Test fun aReadablePayeeIsAName() {
        // Its name comes from the payee, never from a category word inside it ("swiggy.food@icici" is not "Food").
        assertEquals("Swiggy Food", upiName("swiggy.food@icici"))
        org.junit.Assert.assertTrue(readablePayee("Sai Pharmacy"))
        org.junit.Assert.assertFalse(readablePayee("PAYTMQR28100505"))
        org.junit.Assert.assertFalse(readablePayee("9876543210@ybl"))
    }
}
