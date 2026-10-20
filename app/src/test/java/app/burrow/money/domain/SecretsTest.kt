package app.burrow.money.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretsTest {
    @Test fun pinHashMatchesOnlyItsPin() {
        val stored = PinHash.make("1234")
        assertTrue(PinHash.matches("1234", stored))
        assertFalse(PinHash.matches("1235", stored))
        assertFalse(stored.contains("1234"))
        assertTrue(PinHash.make("1234") != stored) // salted
    }

    @Test fun backupRoundTrip() {
        val json = """{"txns":[{"amount":24000,"payee":"Third Wave Coffee"}]}""".toByteArray()
        val sealed = BackupCrypto.seal(json, "correct horse".toCharArray(), iterations = 1_000)
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("Third Wave"))
        assertArrayEquals(json, BackupCrypto.open(sealed, "correct horse".toCharArray(), iterations = 1_000))
    }

    @Test(expected = WrongPassword::class)
    fun wrongPassword() {
        val sealed = BackupCrypto.seal("x".toByteArray(), "right".toCharArray(), iterations = 1_000)
        BackupCrypto.open(sealed, "wrong".toCharArray(), iterations = 1_000)
    }

    @Test(expected = NotABackup::class)
    fun notABackup() {
        BackupCrypto.open("hello, this is a text file".toByteArray(), "x".toCharArray())
    }

    @Test fun csvRoundTrip() {
        val rows = listOf(
            listOf("2026-10-19 09:12", "EXPENSE", "Third Wave Coffee", "240.00", "INR", "Food & drinks", "HDFC Savings", ""),
            listOf("2026-10-18", "EXPENSE", "Café \"Blue\", Indiranagar", "1,240.50", "INR", "Food & drinks", "Cash", "with\nfriends"),
        )
        val back = Csv.read(Csv.write(rows))
        assertEquals(2, back.size)
        assertEquals("Café \"Blue\", Indiranagar", back[1]["payee"])
        assertEquals("with\nfriends", back[1]["note"])
        assertEquals("240.00", back[0]["amount"])
    }

    @Test fun csvFromSpreadsheet() {
        val text = "﻿Date,Payee,Amount\r\n19/10/2026,Uber,-238\r\n\r\n"
        val r = Csv.read(text)
        assertEquals(1, r.size)
        assertEquals("-238", r[0]["amount"])
    }
}
