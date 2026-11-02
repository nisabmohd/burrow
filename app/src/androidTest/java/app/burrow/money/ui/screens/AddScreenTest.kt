package app.burrow.money.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.burrow.money.R
import app.burrow.money.data.AccountEntity
import app.burrow.money.data.AccountType
import app.burrow.money.data.AccountWithBalance
import app.burrow.money.data.AttachmentEntity
import app.burrow.money.data.CategoryEntity
import app.burrow.money.data.CategoryKind
import app.burrow.money.data.FormExtra
import app.burrow.money.data.TxnType
import app.burrow.money.domain.AmountInput
import app.burrow.money.ui.add.AddActions
import app.burrow.money.ui.add.AddContent
import app.burrow.money.ui.add.AddForm
import app.burrow.money.ui.add.AddUiState
import app.burrow.money.data.Attachments
import app.burrow.money.ui.add.PayeeSuggestion
import app.burrow.money.ui.add.RecentSuggestion
import app.burrow.money.ui.add.Saved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** New expense (17, 111, 112) from a made-up form: what each tap asks of the form. */
class AddScreenTest {
    @get:Rule val rule = createComposeRule()

    /** Writes down every call, as "name:argument". */
    private class Fake : AddActions {
        val calls = mutableListOf<String>()
        override fun setType(type: String) { calls += "type:$type" }
        override fun setInput(input: AmountInput) { calls += "input:${input.text}" }
        override fun evaluate() { calls += "evaluate" }
        override fun setCategory(id: Long) { calls += "category:$id" }
        override fun setAccount(id: Long) { calls += "account:$id" }
        override fun setToAccount(id: Long) { calls += "to:$id" }
        override fun setNote(text: String) { calls += "note:$text" }
        override fun setPayee(text: String) { calls += "payee:$text" }
        override fun pickPayee(s: PayeeSuggestion) { calls += "pickPayee:${s.name}" }
        override fun pickRecent(r: RecentSuggestion, accounts: List<AccountWithBalance>) { calls += "recent:${r.payee}" }
        override fun toggleTag(tag: String) { calls += "tag:$tag" }
        override fun setRepeat(every: String) { calls += "repeat:$every" }
        override fun setSubscription(on: Boolean) { calls += "subscription:$on" }
        override fun setLend(kind: String?) { calls += "lend:$kind" }
        override fun setPerson(name: String) { calls += "person:$name" }
        override fun spendFavourite(f: app.burrow.money.data.FavouriteView, label: String, onSaved: (Saved) -> Unit) { calls += "favourite" }
        override fun addAttachment(p: Attachments.Pending) { calls += "attach" }
        override fun removePending(p: Attachments.Pending) { calls += "removePending" }
        override fun removeKept(a: AttachmentEntity) { calls += "removeKept" }
        override fun setDate(date: LocalDate) { calls += "date:$date" }
        override fun setTime(time: LocalTime) { calls += "time:$time" }
        override fun save(onSaved: (Saved) -> Unit) { calls += "save" }
    }

    private val fake = Fake()
    private val now = System.currentTimeMillis()
    private val food = CategoryEntity(id = 1, name = "Food & drinks", kind = CategoryKind.EXPENSE, icon = "restaurant", colorHue = 30f)
    private val travel = CategoryEntity(id = 2, name = "Travel", kind = CategoryKind.EXPENSE, icon = "flight", colorHue = 215f)
    private val salary = CategoryEntity(id = 3, name = "Salary", kind = CategoryKind.INCOME, icon = "payments", colorHue = 145f)
    private val hdfc = AccountWithBalance(AccountEntity(id = 1, name = "HDFC Savings", type = AccountType.BANK, currency = "INR", openingBalance = 0, colorHue = 215f, icon = "account_balance", createdAt = now), 50_000_00)

    private fun state(form: AddForm = AddForm(accountId = 1), recent: List<RecentSuggestion> = emptyList(), suggestions: List<PayeeSuggestion> = emptyList()) = AddUiState(
        form = form, isEdit = false, categories = listOf(food, travel, salary), accounts = listOf(hdfc), spentByCategory = emptyMap(),
        extras = setOf(FormExtra.NOTE, FormExtra.REPEAT, FormExtra.RECENT), suggestions = suggestions, knownTags = emptyList(), recent = recent,
    )

    private fun show(s: AddUiState?) = rule.showScreen { AddContent(s, fake, onClose = {}, onSaved = {}, attach = { _, _ -> }) }

    @Test fun nothingDrawnUntilTheFormHasLoaded() {
        show(null)
        rule.onNodeWithText(text(R.string.save)).assertDoesNotExist()
    }

    @Test fun keypadTypesIntoTheAmount() {
        show(state())
        rule.onNodeWithText("5").performClick()
        assertEquals(listOf("input:5"), fake.calls)
    }

    @Test fun categoryChipPicksTheCategoryAndIncomeOnesStayHidden() {
        show(state())
        rule.onNodeWithText("Travel").performClick()
        assertEquals(listOf("category:2"), fake.calls)
        rule.onNodeWithText("Salary").assertDoesNotExist()
    }

    @Test fun titleOpensTheTypeSheet() {
        show(state())
        rule.onNodeWithText(text(R.string.new_expense)).performClick()
        rule.onNodeWithText(text(R.string.money_in)).performClick()
        assertEquals(listOf("type:${TxnType.INCOME}"), fake.calls)
    }

    @Test fun typingPaidToSendsEachChange() {
        show(state())
        rule.onNodeWithText(text(R.string.paid_to)).performTextInput("Chai")
        assertTrue(fake.calls.toString(), "payee:Chai" in fake.calls)
    }

    @Test fun recentChipOnAnEmptyFormFillsItIn() {
        show(state(recent = listOf(RecentSuggestion("Uber", 180_00, categoryId = 2, accountId = 1))))
        rule.onNodeWithText("Uber").performClick()
        assertEquals(listOf("recent:Uber"), fake.calls)
    }

    @Test fun payeeMatchesShowWhileTyping() {
        show(state(form = AddForm(accountId = 1, payee = "Thi"), suggestions = listOf(PayeeSuggestion("Third Wave Coffee", null, null, 1))))
        rule.onNodeWithText("Third Wave Coffee").performClick()
        assertEquals(listOf("pickPayee:Third Wave Coffee"), fake.calls)
    }

    @Test fun saveTooEarlyMarksTheMissingCategory() {
        show(state(form = AddForm(accountId = 1, input = AmountInput("240"), tried = true)))
        rule.onNodeWithText(text(R.string.pick_category_to_save)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.save)).performClick()
        assertEquals(listOf("save"), fake.calls)
    }
}
