package app.burrow.money.ui.accounts

import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditLineTest {
    private fun card(id: Long, sharesWith: Long? = null, type: String = AccountType.CARD, archived: Boolean = false) = AccountEntity(
        id = id, name = "Card $id", type = type, currency = "INR", openingBalance = 0, colorHue = 0f, icon = "credit_card",
        createdAt = 0, sharesLimitWith = sharesWith, archived = archived,
    )

    @Test fun aCardOnItsOwnIsItsOwnLine() = assertEquals(1L, creditLine(1, listOf(card(1))))

    @Test fun cardsSharingUseTheFirstCardsLine() {
        val all = listOf(card(1), card(2, sharesWith = 1), card(3, sharesWith = 1))
        assertEquals(listOf(1L, 1L, 1L), all.map { creditLine(it.id, all) })
    }

    @Test fun aChainEndsOneStepOn() {
        val all = listOf(card(1), card(2, sharesWith = 1), card(3, sharesWith = 2))
        assertEquals(1L, creditLine(3, all))
    }

    @Test fun aLoopDoesNotHang() {
        val all = listOf(card(1, sharesWith = 2), card(2, sharesWith = 1))
        assertEquals(2L, creditLine(1, all))
    }

    @Test fun anArchivedOrNonCardOtherIsIgnored() {
        assertEquals(2L, creditLine(2, listOf(card(1, archived = true), card(2, sharesWith = 1))))
        assertEquals(2L, creditLine(2, listOf(card(1, type = AccountType.BANK), card(2, sharesWith = 1))))
    }
}
