package app.burrow.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ImportTest {
    private val two = { _: String? -> 2 }

    @Test fun cashewExport() {
        val csv = """
            account,amount,currency,title,note,date,income,type,category name,subcategory name,color,icon,emoji,budget,objective
            HDFC,-240.0,INR,Third Wave Coffee,,2026-10-19 09:12:00.000,false,null,Dining,,0xff8c4b1f,coffee.png,,,
            HDFC,85000.0,INR,Salary,October,2026-10-01 10:00:00.000,true,null,Salary,,0xff4caf50,money.png,,,
            Cash,-40,INR,Metro,,2026-10-18 08:30:00.000,false,null,Transport,,,,,,
        """.trimIndent()
        val p = Importer.parse(csv, ImportSource.CASHEW, two)
        assertEquals(3, p.rows.size)
        val coffee = p.rows[0]
        assertEquals(240_00L, coffee.amount)
        assertFalse(coffee.income)
        assertEquals("Third Wave Coffee", coffee.payee)
        assertEquals("Dining", coffee.category)
        assertEquals("HDFC", coffee.account)
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 12), coffee.date)
        assertTrue(p.rows[1].income)
        assertEquals(85_000_00L, p.rows[1].amount)
        assertEquals(mapOf("Dining" to 1, "Salary" to 1, "Transport" to 1), p.categories)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), p.first)
    }

    /** Cashew's real export (2026): transfers are two "Balance Correction" rows a second apart, with float noise. */
    @Test fun cashewBalanceCorrectionsBecomeTransfers() {
        val csv = """
            account,amount,amount unpaid,currency,title,note,date,income,type,category name,subcategory name,color,icon,emoji,budget,objective,extra
            ICICI,,-700.0,INR,Pg current bill,,2026-11-05 08:10:51.000,false,upcoming,Bills & Fees,,0XFF4CAF50,paper-bill,,,,repeat every 1 month
            Savings,24999.999999999996,,INR,Savings Transfer In,,2026-07-01 00:58:31.000,true,default,Balance Correction,,,,,,,
            ICICI,-25000.0,,INR,ICICI Transfer Out,,2026-07-01 00:58:30.000,false,default,Balance Correction,,,,,,,
            Cash,120.0,,INR,Fixed cash,,2026-07-03 10:00:00.000,true,default,Balance Correction,,,,,,,
            ICICI,-50.0,,INR,Visor back,,2026-10-18 10:00:00.000,false,default,Misc,,,,,,,
            ICICI,-20.0,,INR,Service,,2026-10-17 10:00:00.000,false,default,Misc,,,,,,,
            ICICI,10.0,,INR,Refund,,2026-10-16 10:00:00.000,true,default,Misc,,,,,,,
            ICICI,400.0,,INR,From card,,2026-10-16 11:00:00.000,true,default,Received,,,,,,,
        """.trimIndent()
        val p = Importer.parse(csv, ImportSource.CASHEW, two)
        assertEquals(1, p.skipped) // the unpaid upcoming bill has no amount yet
        assertEquals(mapOf("Misc" to 3, "Received" to 1), p.categories) // corrections aren't categories
        assertEquals(setOf("Received"), p.incomeCategories) // "Misc" is mostly spending
        val (pairs, single) = Importer.pairTransfers(p.rows)
        val t = pairs.single()
        assertEquals("ICICI", t.from.account)
        assertEquals("Savings", t.to.account)
        assertEquals(25_000_00L, t.from.amount)
        assertEquals(25_000_00L, t.to.amount)
        assertEquals("Cash", single.single().account)
    }

    @Test fun guessesIconsForCashewNames() {
        assertEquals("bolt", Importer.guessIcon("Bills & Fees", false))
        assertEquals("savings", Importer.guessIcon("Savings", false))
        assertEquals("redeem", Importer.guessIcon("Received", true))
        assertNull(Importer.guessIcon("Misc", false))
    }

    @Test fun walnutExport() {
        val csv = """
            DATE,TIME,PLACE,AMOUNT,DR/CR,ACCOUNT,EXPENSE,INCOME,CATEGORY,TAGS,NOTE
            19-10-26,09:12 AM,Swiggy,"1,240.00",DR,HDFC Bank,Yes,No,Food,,dinner
            18-10-26,06:40 PM,Refund Amazon,499.00,CR,ICICI,No,Yes,Shopping,,
        """.trimIndent()
        val p = Importer.parse(csv, ImportSource.WALNUT, two)
        assertEquals(2, p.rows.size)
        assertEquals(1_240_00L, p.rows[0].amount)
        assertFalse(p.rows[0].income)
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 12), p.rows[0].date)
        assertEquals("dinner", p.rows[0].note)
        assertTrue(p.rows[1].income)
        assertEquals(LocalDateTime.of(2026, 10, 18, 18, 40), p.rows[1].date)
    }

    @Test fun moneyManagerExportSkipsTransfers() {
        val csv = "Period\tAccounts\tCategory\tSubcategory\tNote\tINR\tIncome/Expense\tDescription\tAmount\tCurrency\n" +
            "10/19/2026 13:45:00\tCash\tFood\tLunch\tCanteen\t180\tExp.\t\t180\tINR\n" +
            "10/18/2026 10:00:00\tBank\tSalary\t\t\t50000\tIncome\t\t50000\tINR\n" +
            "10/17/2026 10:00:00\tBank\tTransfer\t\t\t500\tTransfer-Out\t\t500\tINR\n"
        val p = Importer.parse(csv, ImportSource.MONEY_MANAGER, two)
        assertEquals(2, p.rows.size)
        assertEquals(LocalDateTime.of(2026, 10, 19, 13, 45), p.rows[0].date)
        assertEquals(180_00L, p.rows[0].amount)
        assertFalse(p.rows[0].income)
        assertEquals("Cash", p.rows[0].account)
        assertTrue(p.rows[1].income)
        assertEquals(1, p.skipped)
    }

    @Test fun bankStatementWithDebitAndCredit() {
        val csv = """
            Date;Narration;Withdrawal Amt.;Deposit Amt.
            19/10/2026;UPI-SWIGGY;450,00;
            20/10/2026;NEFT SALARY;;85.000,00
        """.trimIndent()
        val p = Importer.parse(csv, ImportSource.CSV, two)
        assertEquals(2, p.rows.size)
        assertEquals(450_00L, p.rows[0].amount)
        assertFalse(p.rows[0].income)
        assertEquals(LocalDate.of(2026, 10, 19), p.rows[0].date.toLocalDate())
        assertTrue(p.rows[1].income)
        assertEquals(85_000_00L, p.rows[1].amount)
        assertEquals("NEFT SALARY", p.rows[1].payee)
    }

    @Test fun allPositiveAmountsWithoutATypeAreSpending() {
        val csv = "date,payee,amount\n2026-10-19,Chai,20\n2026-10-19,Metro,40\n"
        val p = Importer.parse(csv, ImportSource.CSV, two)
        assertTrue(p.rows.none { it.income })
        assertEquals(listOf(20_00L, 40_00L), p.rows.map { it.amount })
    }

    @Test fun badRowsAreCountedNotFatal() {
        val csv = "date,amount\nnot a date,20\n2026-10-19,\n2026-10-19,abc\n2026-10-19,0\n2026-10-19,15\n"
        val p = Importer.parse(csv, ImportSource.CSV, two)
        assertEquals(1, p.rows.size)
        assertEquals(4, p.skipped)
    }

    @Test fun emptyAndHeaderOnlyFiles() {
        assertEquals(0, Importer.parse("", ImportSource.CSV, two).rows.size)
        assertEquals(0, Importer.parse("date,amount\n", ImportSource.CSV, two).rows.size)
    }

    @Test fun quotedCellsKeepCommasAndLineBreaks() {
        val csv = "date,payee,amount,note\n2026-10-19,\"Café, Bar\",\"1,200\",\"two\nlines\"\n"
        val r = Importer.parse(csv, ImportSource.CSV, two).rows.single()
        assertEquals("Café, Bar", r.payee)
        assertEquals(1_200_00L, r.amount)
        assertEquals("two\nlines", r.note)
    }

    @Test fun columnsCanBeChosenByHand() {
        val csv = "when,what,how much\n2026-10-19,Chai,20\n"
        assertTrue(Importer.detect(listOf("when", "what", "how much")).isEmpty())
        val p = Importer.parse(csv, ImportSource.CSV, two, mapOf(ImportField.DATE to 0, ImportField.PAYEE to 1, ImportField.AMOUNT to 2))
        assertEquals("Chai", p.rows.single().payee)
    }

    @Test fun amounts() {
        assertEquals(124_050L, Importer.amount("₹1,240.50", 2))
        assertEquals(-124_050L, Importer.amount("-1240.5", 2))
        assertEquals(-124_050L, Importer.amount("(1,240.50)", 2))
        assertEquals(124_050L, Importer.amount("1.240,50", 2))
        assertEquals(124_050L, Importer.amount("1 240,50", 2))
        assertEquals(24_000L, Importer.amount("Rs. 240", 2))
        assertEquals(1_240L, Importer.amount("1,240", 0))
        assertEquals(124_000_000L, Importer.amount("1.240.000", 2))
        assertNull(Importer.amount("abc", 2))
        assertNull(Importer.amount("", 2))
    }

    @Test fun dates() {
        assertEquals(LocalDate.of(2026, 10, 19), Importer.date("19/10/2026")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 19), Importer.date("10/19/2026", ImportSource.MONEY_MANAGER)!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 9), Importer.date("09/10/2026")!!.toLocalDate()) // day first unless Money Manager
        assertEquals(LocalDate.of(2026, 10, 19), Importer.date("19 Oct 2026")!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 19), Importer.date("Oct 19, 2026")!!.toLocalDate())
        assertEquals(LocalDateTime.of(2026, 10, 19, 21, 5), Importer.date("2026-10-19T21:05:00"))
        assertNull(Importer.date("yesterday"))
    }

    @Test fun duplicatesMatchDayAmountAndPayee() {
        val row = ImportRow(LocalDateTime.of(2026, 10, 19, 9, 0), 240_00, false, "Third Wave Coffee", null, null, null, null)
        val here = setOf(Triple(LocalDate.of(2026, 10, 19), 240_00L, Importer.key("third wave  coffee")))
        assertTrue(Importer.isDuplicate(row, here))
        assertFalse(Importer.isDuplicate(row.copy(amount = 241_00), here))
        assertFalse(Importer.isDuplicate(row.copy(date = row.date.plusDays(1)), here))
    }

    @Test fun categoryGuesses() {
        assertEquals("restaurant", Importer.guessIcon("Dining", false))
        assertEquals("shopping_cart", Importer.guessIcon("Groceries", false))
        assertEquals("movie", Importer.guessIcon("Subscriptions", false))
        assertEquals("bolt", Importer.guessIcon("Bills", false))
        assertEquals("payments", Importer.guessIcon("Salary", true))
        assertNull(Importer.guessIcon("Misc", false))
        assertNull(Importer.guessIcon("Other stuff", false))
    }
}
