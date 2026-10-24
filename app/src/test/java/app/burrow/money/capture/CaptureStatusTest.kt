package app.burrow.money.capture

import app.burrow.money.capture.CaptureStatus.Blocked
import app.burrow.money.capture.CaptureStatus.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every screen reads auto-capture from CaptureStatus: these pin down what each one shows. */
class CaptureStatusTest {
    private fun s(smsWanted: Boolean = false, appsWanted: Boolean = false, smsAllowed: Boolean = false, appsAllowed: Boolean = false, smsInBuild: Boolean = true) =
        CaptureStatus(smsWanted, appsWanted, smsAllowed, appsAllowed, smsInBuild)

    @Test fun nothingTurnedOnIsOffNotPaused() {
        val st = s(smsAllowed = true, appsAllowed = true)
        assertFalse(st.anyOn); assertFalse(st.wanted); assertNull(st.blocked); assertEquals(Tile.OFF, st.tile)
    }

    @Test fun turnedOnButNoAccessIsPausedNeverOn() {
        // The 2026-10-24 bug: the More tile said On while Android blocked notification access.
        val st = s(appsWanted = true, appsAllowed = false)
        assertFalse(st.appsOn); assertFalse(st.anyOn); assertEquals(Tile.PAUSED, st.tile); assertEquals(Blocked.NOTIFICATIONS, st.blocked)
    }

    @Test fun smsTurnedOnWithoutPermissionIsPaused() {
        val st = s(smsWanted = true, smsAllowed = false)
        assertFalse(st.smsOn); assertEquals(Tile.PAUSED, st.tile); assertEquals(Blocked.SMS, st.blocked)
    }

    @Test fun accessWithoutTheSwitchIsStillOff() {
        val st = s(smsAllowed = true, appsAllowed = true)
        assertFalse(st.smsOn); assertFalse(st.appsOn)
    }

    @Test fun oneSourceWorkingIsOnEvenIfTheOtherIsBlocked() {
        val st = s(smsWanted = true, smsAllowed = true, appsWanted = true, appsAllowed = false)
        assertTrue(st.anyOn); assertEquals(Tile.ON, st.tile)
        assertEquals("the note still says what is blocked", Blocked.NOTIFICATIONS, st.blocked)
    }

    @Test fun notificationAccessIsReportedBeforeSms() {
        assertEquals(Blocked.NOTIFICATIONS, s(smsWanted = true, appsWanted = true).blocked)
    }

    @Test fun buildWithoutSmsIgnoresTheSmsSwitch() {
        val st = s(smsWanted = true, smsAllowed = true, smsInBuild = false)
        assertFalse(st.smsOn); assertFalse(st.wanted); assertNull(st.blocked); assertEquals(Tile.OFF, st.tile)
    }

    @Test fun everyCombinationIsConsistent() {
        val b = listOf(false, true)
        for (sw in b) for (aw in b) for (sa in b) for (aa in b) for (inBuild in b) {
            val st = s(sw, aw, sa, aa, inBuild)
            val label = "sw=$sw aw=$aw sa=$sa aa=$aa build=$inBuild"
            assertEquals(label, st.smsOn || st.appsOn, st.anyOn)
            // On only when something both wanted and allowed.
            assertEquals(label, (inBuild && sw && sa) || (aw && aa), st.anyOn)
            // A tile never says On with nothing recording, never Off while the user wants it and it records.
            if (st.tile == Tile.ON) assertTrue(label, st.anyOn)
            if (st.tile == Tile.OFF) assertFalse(label, st.wanted)
            if (st.tile == Tile.PAUSED) { assertTrue(label, st.wanted); assertFalse(label, st.anyOn) }
            // Blocked only names something the user turned on.
            when (st.blocked) {
                Blocked.NOTIFICATIONS -> { assertTrue(label, aw); assertFalse(label, aa) }
                Blocked.SMS -> { assertTrue(label, sw && inBuild); assertFalse(label, sa) }
                null -> assertFalse(label, (aw && !aa) || (inBuild && sw && !sa))
            }
        }
    }
}
