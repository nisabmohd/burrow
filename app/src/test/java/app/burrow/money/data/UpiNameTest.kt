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
}
