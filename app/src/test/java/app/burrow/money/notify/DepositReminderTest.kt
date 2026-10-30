package app.burrow.money.notify

import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Deposit reminder (166): one note 7 days before maturity, at the bill reminder time. */
class DepositReminderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val maturity = LocalDate.of(2026, 10, 26)
    private val nineAm = 9 * 60

    private fun deposit(
        id: Long = 1,
        type: String = AccountType.SAVINGS,
        remind: Boolean = true,
        archived: Boolean = false,
        sample: Boolean = false,
        matures: LocalDate? = maturity,
    ) = AccountEntity(
        id = id, name = "Fixed deposit", type = type, currency = "INR", openingBalance = 0, colorHue = 0f, icon = "savings",
        createdAt = 0, remind = remind, archived = archived, isSample = sample,
        maturityDate = matures?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
    )

    private fun local(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    @Test fun firesAWeekBeforeAtTheReminderTime() {
        val events = depositEvents(listOf(deposit()), zone, nineAm)
        assertEquals(1, events.size)
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), local(events.single().at))
    }

    @Test fun followsTheReminderMinutes() {
        val at = depositEvents(listOf(deposit()), zone, 18 * 60 + 30).single().at
        assertEquals(LocalDateTime.of(2026, 10, 19, 18, 30), local(at))
    }

    @Test fun onlyDepositsThatAskForIt() {
        val accounts = listOf(
            deposit(id = 1),
            deposit(id = 2, remind = false),
            deposit(id = 3, archived = true),
            deposit(id = 4, sample = true),
            deposit(id = 5, matures = null),
            deposit(id = 6, type = AccountType.BANK),
            deposit(id = 7, type = AccountType.CARD),
        )
        assertEquals(listOf(1L), depositEvents(accounts, zone, nineAm).map { it.deposit.id })
    }

    @Test fun aDepositMadeLessThanAWeekBeforeMaturityIsAlreadyPast() {
        // Opened on the 22nd, it matures on the 26th: its reminder time (19th) is before it existed, so
        // the run that checks "since the last run" never sees it in its window.
        val opened = LocalDate.of(2026, 10, 22).atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(depositEvents(listOf(deposit()), zone, nineAm).single().at < opened)
    }

    @Test fun eachDepositGetsItsOwnEvent() {
        val other = deposit(id = 2, matures = LocalDate.of(2027, 1, 9))
        val events = depositEvents(listOf(deposit(), other), zone, nineAm).sortedBy { it.at }
        assertEquals(listOf(1L, 2L), events.map { it.deposit.id })
        assertEquals(LocalDate.of(2027, 1, 2), local(events[1].at).toLocalDate())
    }
}
