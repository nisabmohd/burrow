
#!/usr/bin/env python3
"""End-to-end smoke check of every Burrow feature on a connected emulator or phone.

Installs a fresh debug build (which seeds the sample month on first launch), then runs one short
check per feature and prints PASS/FAIL. Exit code is the number of failures. A check also fails
when the app crashed while it ran (the crash log is read after every check).

    ./gradlew :app:assembleFossDebug
    python3 tools/smoke.py                 # all checks (about 15 minutes)
    python3 tools/smoke.py split refund    # only checks whose name contains these words
    VERBOSE=1 python3 tools/smoke.py       # with tracebacks

Checks use visible text and TalkBack labels, so they also catch missing labels. Amounts are read
without their currency symbol: the emulator's region decides it ($, ₹, €).

The order matters: the sample month is there until the first real entry, so the checks that need
sample data (Plan, Accounts, Insights, charts) run before "add_expense_saves_and_undo".
"""
import os
import re
import subprocess
import sys
import tempfile
import time
import traceback

sys.path.insert(0, os.path.dirname(__file__))
import device as d  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
# foss: the build that reads bank SMS (play only reads payment-app notifications until the Play SMS
# declaration is approved), so every capture check can run.
APK = os.path.join(ROOT, "app/build/outputs/apk/foss/debug/app-foss-debug.apk")

CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


def fresh_install():
    d.adb("uninstall", d.PACKAGE)
    out = ""
    for _ in range(3):  # an emulator near its storage limit refuses installs now and then
        d.shell("pm trim-caches 8G")
        out = d.adb("install", APK)
        if "Success" in out:
            break
        time.sleep(2)
    assert "Success" in out, f"install failed: {out}"
    d.shell(f"pm grant {d.PACKAGE} android.permission.POST_NOTIFICATIONS")
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(3)


# --- small helpers ------------------------------------------------------------------------


def labels():
    return [n.label for n in d.dump() if n.label]


def has(text, nodes=None):
    """Any node whose label contains [text]."""
    return any(text in n.label for n in (nodes if nodes is not None else d.dump()))


def tap_node(n):
    x1, y1, x2, y2 = n.bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)


def tap_last(label):
    """Taps the lowest node with this label (a sheet's or dialog's button over the page's)."""
    nodes = [n for n in d.dump() if n.label == label]
    assert nodes, f"not on screen: {label!r}"
    tap_node(max(nodes, key=lambda n: n.bounds[1]))


def snackbar(contains=None, undo=False, tries=3):
    """Reads the Undo snackbar from one dump (it lasts ~4 s), optionally taps Undo. Returns the dump."""
    for _ in range(tries):
        nodes = d.dump()
        u = [n for n in nodes if n.label == "Undo"]
        if u and (contains is None or has(contains, nodes)):
            if undo:
                tap_node(u[0])
            return nodes
    raise AssertionError(f"no snackbar{' with ' + repr(contains) if contains else ''}")


def open_add():
    recover()
    d.nav("Today")
    d.tap("Add expense")
    assert d.exists("New expense", timeout=5), "New expense did not open"


def keypad(digits):
    # The keypad opens on tapping the amount (17 → 108).
    if not d.exists("00", timeout=1) and d.exists("Tap to type the amount", timeout=1):
        d.tap("Tap to type the amount")
    for k in digits:
        d.tap(k)


def push_file(name, text):
    """Puts a file in the phone's Downloads, for the system file picker."""
    path = os.path.join(tempfile.gettempdir(), name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    d.adb("push", path, f"/sdcard/Download/{name}")
    d.shell(f"am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/{name}")
    time.sleep(1)


def pick_file(name):
    """In the system file picker: the file from Recent, else from Downloads."""
    if not d.exists(name, timeout=4):
        if d.exists("Show roots", timeout=2):
            d.tap("Show roots")
            d.tap("Downloads", contains=True)
    d.tap(name, timeout=6)
    time.sleep(2)


def crashed():
    """The app's crash since the last check, if any (first line)."""
    out = d.adb("logcat", "-d", "-b", "crash")
    if d.PACKAGE in out and "FATAL EXCEPTION" in out:
        lines = [l for l in out.splitlines() if "Exception" in l or "Error" in l]
        return lines[0] if lines else "FATAL EXCEPTION"
    return None


def open_more_row(label):
    recover()
    d.nav("More")
    d.scroll_to(label)
    d.tap(label)


# --- checks, in the order a new user meets the features -----------------------------------


def finish_welcome(budget=True):
    """Welcome (01) → main currency (02) → monthly budget (12) → accounts (03)."""
    d.tap("Get started")
    if d.exists("Continue", timeout=3):
        d.tap("Continue")  # currency
    if d.exists("How much a month?", timeout=3):
        d.tap("Continue" if budget else "Skip for now")
    if d.exists("Done", timeout=3):
        d.tap("Done")


@check
def welcome_shows_once():
    assert d.exists("Get started"), "welcome screen missing on first launch"
    d.tap("Get started")
    d.tap("Continue")
    # The budget step (12): stepper, quick chips, what it means a day; Skip or Continue.
    assert d.exists("How much a month?", timeout=5), "no budget step in Welcome"
    nodes = d.dump()
    assert has("Step 2 of 3", nodes), "budget step is not step 2 of 3"
    assert has("a day", nodes), "no 'a day' line on the budget step"
    assert has("Skip for now", nodes), "no Skip for now"
    d.tap("Continue")
    d.tap("Done")
    assert d.exists("Today", timeout=10), "Today not shown after onboarding"
    d.shell(f"am force-stop {d.PACKAGE}")
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(2.5)
    assert not d.exists("Get started", timeout=1.5), "welcome shown again after restart"


@check
def tabs_switch_with_titles():
    for tab in ["Activity", "Plan", "Insights", "More", "Today"]:
        d.nav(tab)
        titles = [n for n in d.dump() if n.text == tab]
        assert len(titles) >= 2, f"{tab}: no title above the content (nav label only)"


@check
def today_shows_budget_and_sample_note():
    assert d.exists("left of", contains=True, timeout=5), "budget hero missing"
    assert d.exists("These are sample transactions", contains=True), "sample-data note missing"


@check
def today_hero_says_safe_to_spend():
    # The daily number (01): "left of ₹30,000 · ₹608 safe to spend today".
    recover()
    d.nav("Today")
    assert d.exists("safe to spend today", contains=True, timeout=5), "hero has no 'safe to spend today'"


@check
def today_has_no_customise_button():
    # Customise stays optional (NEXT.md): only in More.
    recover()
    d.nav("Today")
    for _ in range(4):
        assert not has("Customise Today"), "Customise Today button still on Today"
        d.swipe(540, 1700, 540, 600)


@check
def today_widgets_and_charts():
    recover()
    d.nav("Today")
    # Calm pass: two widgets by default (the rest in Customise › Today), and the coming-up row.
    for label in ["coming up this week", "This week", "Top category"]:
        assert d.scroll_to(label, contains=True), f"missing on Today: {label}"
    assert not d.exists("Net worth", timeout=1), "Net worth widget on by default"


# Plan, Accounts, Insights: before the first real entry, which clears the sample month.


@check
def plan_overdue_mark_paid_and_undo():
    # Coming up (22): the overdue group first; swipe the row right to mark it paid, Undo brings it back.
    recover()
    d.nav("Plan")
    assert d.exists("Overdue", timeout=5), "no Overdue group on Coming up (sample electricity bill)"
    late = d.find("Was due", contains=True)
    assert late, "overdue row has no 'Was due' line"
    y = late.center[1]
    d.swipe(150, y, 1000, y, 250)
    snackbar("Marked paid", undo=True)
    assert d.exists("Overdue", timeout=5), "Undo did not bring the overdue bill back"


@check
def plan_budgets_and_goals():
    recover()
    d.nav("Plan")
    d.tap("Budgets")
    assert d.exists("left", contains=True, timeout=5), "no budget header"
    d.tap("Goals")
    assert d.exists("Saved for goals", timeout=5), "no goals header"
    d.tap("Japan trip")
    assert d.exists("Add money", timeout=5), "goal detail has no Add money"
    d.back()


@check
def accounts_card_and_loan():
    recover()
    d.nav("Today")
    d.tap("Accounts")
    assert d.exists("Net worth", timeout=5), "Accounts has no net worth"
    d.tap("ICICI Amazon Pay")
    assert d.exists("Pay bill", timeout=5), "card has no Pay bill"
    d.back()
    d.tap("Home loan")
    assert d.exists("EMIs paid", contains=True, timeout=5), "loan has no EMI count"
    d.back()
    d.back()


@check
def account_page_lists_its_own_history():
    # 0.4.1: the page reads its own rows (not the newest few hundred of every account) in a lazy list.
    recover()
    d.nav("Today")
    d.tap("Accounts")
    d.tap("ICICI Amazon Pay")
    assert d.exists("Since statement", contains=True, timeout=5), "card has no since-statement row"
    assert d.scroll_to("Uber"), "card page has no Uber payment in its history"
    assert not crashed(), "crash scrolling the card's history"
    d.back()
    d.back()


@check
def insights_heatmap_opens_activity():
    recover()
    d.nav("Insights")
    assert d.exists("By category", timeout=8), "Insights has no By category"
    d.scroll_to("Less")
    days = [n for n in d.dump() if re.match(r"^(\d{1,2} \w+|\w+ \d{1,2}), ", n.label)]  # "17 Oct, ₹…" or "Oct 17, ₹…"
    assert days, "no heatmap days"
    tap_node(days[0])
    assert d.exists("Clear all", timeout=5), "heatmap day did not open Activity with that day"
    d.tap("Clear all")


@check
def insights_every_section():
    recover()
    d.nav("Insights")
    for label in ["Spent", "Average a day", "Biggest", "By category", "Patterns", "Busiest day", "No-spend days", "You saved", "Top payee", "by day"]:
        assert d.scroll_to(label, contains=True), f"Insights section missing: {label}"


@check
def insights_long_range_and_category_chart():
    # Charts with many bars (weekly / monthly buckets) and Category insights' by-day bars.
    recover()
    d.nav("Insights")
    d.tap("Dates")
    d.tap("This year")
    d.tap("Show", contains=True)
    d.scroll_to_top()  # the list keeps its place from the check before
    assert d.scroll_to("By category"), "Insights broke with a year range"
    assert not crashed(), "crash with a year range"
    d.scroll_to("By category")
    rows = [n for n in d.dump() if re.search(r" · \d+%$", n.label)]
    assert rows, "no category rows"
    tap_node(rows[0])
    assert d.exists("By day", timeout=5), "Category insights has no By day chart"
    assert d.exists("avg", contains=True), "no average on the category chart"
    d.back()
    d.tap("Dates")
    d.tap("All dates")


@check
def long_press_sheet_lists_actions():
    # Long-press any row (06): the actions sheet, Delete in its own group.
    recover()
    d.nav("Activity")
    d.long_press("Third Wave Coffee")
    nodes = d.dump()
    for label in ["Change category", "Duplicate to today", "Split across categories", "Add a refund", "Save as favourite", "Delete"]:
        assert has(label, nodes), f"long-press sheet has no {label}"
    d.tap("Close")
    assert not d.exists("Duplicate to today", timeout=1), "sheet did not close"


@check
def long_press_change_category_with_undo():
    recover()
    d.nav("Activity")
    d.long_press("Third Wave Coffee")
    d.tap("Change category")
    d.tap("Groceries")
    snackbar("Saved", undo=True)
    d.nav("Activity")
    assert d.exists("Food & drinks", contains=True, timeout=5), "Undo did not restore the category"


# From here on there are real entries: the sample month goes with the first one.


@check
def add_expense_saves_and_undo():
    open_add()
    keypad("240")
    assert d.exists("Save"), "Save button missing"
    d.tap("Food & drinks")  # a category chip (17)
    d.tap("Save")
    assert d.exists("Undo", timeout=3), "no Undo snackbar after saving"


@check
def new_expense_cant_save_without_category():
    # Can't-save-yet: Save looks off; tapping it turns the missing row red, nothing is saved.
    open_add()
    keypad("50")
    d.tap("Save")
    assert d.exists("New expense", timeout=2), "Save without a category left the form"
    d.tap("Close")


# --- every create flow: type, save, edit, delete (2026-10-31: "." did nothing visible on New expense) ---


def shown(ending, nodes=None):
    """An amount on screen, whatever the currency symbol: shown("12.50") matches "₹12.50" and "−$12.50"."""
    return any(n.label.endswith(ending) for n in (nodes or d.dump()))


def tap_amount_field():
    """The empty amount pill in a form (₹0, $0, 0 €): it opens the value sheet's keypad."""
    n = next((n for n in d.dump() if re.fullmatch(r"[^\d\s]{1,3}\s?0|0\s?[^\d\s]{1,3}", n.label)), None)
    assert n, "no empty amount field"
    tap_node(n)
    assert d.exists("Done", timeout=3), "the amount field didn't open the keypad sheet"


@check
def new_expense_dot_and_decimals_show_as_typed():
    open_add()
    keypad("12")
    d.tap(".")
    assert shown("12."), "the dot doesn't show after typing 12"
    keypad("5")
    assert shown("12.5"), "12.5 isn't shown"
    keypad("0")
    assert shown("12.50"), "the trailing zero is dropped"
    keypad("9")  # one decimal more than the currency has: refused
    assert shown("12.50") and not shown("12.509"), "more decimals than the currency allows"
    d.tap("Delete")
    assert shown("12.5"), "Delete didn't remove the last digit"
    keypad("0")
    d.tap("Food & drinks")
    d.tap("Save")
    snackbar("saved")
    d.nav("Activity")
    assert d.find("12.50", contains=True, timeout=5), "₹12.50 isn't in Activity"


@check
def new_expense_sum_with_plus_and_minus():
    open_add()
    keypad("100")
    d.tap("Plus")
    keypad("25.5")
    d.tap("Minus")
    keypad("5")
    nodes = d.dump()
    assert has("100+25.5−5", nodes), "the sum isn't written under the amount"
    assert shown("120.50", nodes), "100 + 25.5 − 5 isn't 120.50"
    d.tap("Close")


@check
def edit_expense_amount_and_category():
    recover()
    d.nav("Activity")
    row = next((n for n in d.dump() if n.label.startswith("−") and n.label.endswith("12.50")), None)
    assert row, "no −12.50 row to edit"
    tap_node(row)
    d.tap("Edit")
    assert d.exists("Edit expense", timeout=5), "Edit didn't open the form"
    assert shown("12.5"), "the form doesn't start with the saved amount"
    d.tap("Delete")
    keypad("75")
    assert shown("12.75"), "typing after Delete didn't give 12.75"
    d.tap("Groceries")
    d.tap("Save")
    time.sleep(1)
    d.back()
    recover()
    d.nav("Activity")
    nodes = d.dump()
    assert shown("12.75", nodes), "the new amount isn't in Activity"
    assert any("Groceries" in n.label for n in nodes), "the new category isn't in Activity"


@check
def new_income_saves():
    open_add()
    d.tap("New expense")
    d.tap("Income")
    assert d.exists("New income", timeout=3), "the type sheet didn't switch to income"
    keypad("5000.5")
    d.tap("Salary")
    d.tap("Save")
    snackbar("saved")
    d.nav("Activity")
    assert d.find("+", contains=True, timeout=5) and shown("5,000.50"), "the income isn't in Activity"


@check
def add_account_with_decimal_balance():
    recover()
    d.nav("Today")
    d.tap("Accounts")
    d.tap("Add account")
    d.tap("Bank account")  # Add account opens a sheet of kinds first (190)
    d.tap("HDFC Savings, Cash…")
    d.type_text("Kotak")
    d.back()  # keyboard down
    tap_amount_field()
    keypad("2500.75")
    assert shown("2,500.75"), "the balance sheet doesn't show 2,500.75 as typed"
    d.tap("Done")
    assert shown("2,500.75"), "the form doesn't show the balance after Done"
    d.tap("Save")
    assert d.exists("Kotak", timeout=5), "the new account isn't listed"
    assert shown("2,500.75"), "the new account's balance isn't listed"
    d.back()


@check
def new_transfer_between_accounts():
    open_add()
    d.tap("New expense")
    d.tap("Transfer")
    assert d.exists("New transfer", timeout=3), "the type sheet didn't switch to transfer"
    keypad("1000")
    # Your own account comes first (Kotak, made above; samples never default: QA 12 Nov) and the
    # "to" account is filled in beside it.
    nodes = d.dump()
    srcs = [n for n in nodes if n.label == "Kotak"]
    assert srcs, "a transfer doesn't start from your own account"
    # "Kotak" may show more than once (a recent pill too): the one with an account beside it is the From.
    to = [n for src in srcs for n in nodes if n.label and n is not src and abs(n.center[1] - src.center[1]) < 40 and n.center[0] > src.center[0] + 100]
    assert to, f"no 'to' account beside Kotak: {[x.label for x in nodes if x.label][:30]}"
    d.tap("Save")
    snackbar()
    d.nav("Activity")
    assert d.find("→", contains=True, timeout=5), "the transfer isn't in Activity"


@check
def new_bill_decimal_edit_and_delete():
    recover()
    d.nav("Plan")
    d.tap("Coming up")
    d.tap("Add bill")
    d.tap("Netflix, rent, electricity…")
    d.type_text("Gym")
    d.back()
    tap_amount_field()
    keypad("1499.5")
    assert shown("1,499.5"), "the bill amount doesn't show 1,499.5 as typed"
    d.tap("Done")
    d.tap("Save")
    assert d.exists("Gym", timeout=5), "the new bill isn't in Coming up"
    assert shown("1,499.50"), "the bill amount isn't in Coming up"
    d.tap("Gym")
    d.tap("Edit")
    pill = next((n for n in d.dump() if n.label.endswith("1,499.50")), None)
    assert pill, "Edit doesn't show the bill's amount"
    tap_node(pill)
    keypad("999")
    d.tap("Done")
    d.tap("Save")
    time.sleep(1)
    assert shown("999"), "the edited amount isn't shown"
    if not d.exists("Delete bill", timeout=2):
        d.tap("Gym")
    d.tap("Delete bill")
    d.tap("Delete")
    assert not d.exists("Gym", timeout=2), "the deleted bill is still there"


@check
def new_goal_add_money_undo_and_delete():
    recover()
    d.nav("Plan")
    d.tap("Goals")
    d.tap("Add goal")
    d.tap("Trip, laptop, emergency fund…")
    d.type_text("Laptop")
    d.back()
    tap_amount_field()
    keypad("80000")
    d.tap("Done")
    d.tap("Save")
    assert d.exists("Laptop", timeout=5), "the new goal isn't listed"
    d.tap("Laptop")
    d.tap("Add money")
    keypad("1500.5")
    assert shown("1,500.5"), "Add money doesn't show 1,500.5 as typed"
    d.tap("Add")
    snackbar("Money added", undo=True)
    assert d.exists("Laptop", timeout=3) and not shown("1,500.50"), "Undo didn't take the money back"
    d.tap("Delete goal")
    if d.exists("Delete", timeout=2):
        d.tap("Delete")
    recover()
    d.nav("Plan")
    d.tap("Goals")
    assert not d.exists("Laptop", timeout=2), "the deleted goal is still there"


@check
def category_budget_set_and_shown():
    recover()
    d.nav("Plan")
    d.tap("Budgets")
    d.tap("Add")
    assert not d.exists("No spending this month", timeout=1), "the budget's category sheet claims no spending"
    d.tap("Shopping")
    assert d.exists("Monthly budget", timeout=3), "no budget sheet for the category"
    keypad("3000")
    d.tap("Save")
    snackbar()
    assert d.exists("Shopping", timeout=5) and shown("3,000 left") or d.exists("left", contains=True), "the category budget isn't listed"


@check
def new_category_saves_and_shows_in_new_expense():
    open_more_row("Categories")
    d.scroll_to("New category")
    d.tap("New category")
    fields = [n for n in d.dump() if n.cls.endswith("EditText")]
    assert fields, "New category has no name field"
    tap_node(fields[0])
    d.type_text("Plants")
    d.back()
    d.tap("Save")
    assert d.scroll_to("Plants"), "the new category isn't listed"
    open_add()
    # The category chips scroll sideways; More (the category sheet) is the last one.
    # The lowest "Food & drinks": a Recent pill above can read the same (no payee: its category).
    chip = max((n for n in d.dump() if n.label == "Food & drinks"), key=lambda n: n.center[1])
    w = d.screen_size()[0]
    for _ in range(3):
        if d.exists("More", timeout=0.5):
            break
        d.drag(int(w * 0.85), chip.center[1], int(w * 0.15), chip.center[1])
    d.tap("More")
    assert d.scroll_to("Plants"), "the new category isn't offered on New expense"
    recover()


@check
def snackbar_swipes_away():
    # 2026-10-31: swipe the Undo snackbar sideways to dismiss it, without undoing.
    open_add()
    keypad("33")
    d.tap("Food & drinks")
    d.tap("Save")
    nodes = snackbar()
    seen = time.time()
    undo = next(n for n in nodes if n.label == "Undo")
    y = undo.center[1]
    w = d.screen_size()[0]
    d.swipe(int(w * 0.2), y, int(w * 0.95), y, 200)
    gone = not d.exists("Undo", timeout=0.3)
    assert time.time() - seen < 3.8, "too slow to tell a swipe from the snackbar timing out"
    assert gone, "a sideways swipe didn't dismiss the snackbar"
    d.nav("Activity")
    assert shown("33"), "dismissing the snackbar undid the save"


@check
def favourite_chip_saves_with_one_tap():
    # Favourites (91): added by long-press › Save as favourite; on an empty New expense one tap saves.
    open_add()
    keypad("20")
    d.tap("Paid to")
    d.type_text("Chai")
    d.back()  # keyboard down
    d.tap("Food & drinks")
    d.tap("Save")
    snackbar()
    d.nav("Activity")
    d.long_press("Chai", contains=True)
    d.tap("Save as favourite")
    snackbar("Saved as a favourite")
    open_add()
    chip = d.find("Chai", contains=True, timeout=5)
    assert chip, "no Chai favourite chip on an empty New expense"
    tap_node(chip)
    nodes = snackbar("saved")
    assert has("Chai", nodes), "snackbar doesn't name the favourite"
    assert has("Today", nodes), "one tap didn't close New expense"


@check
def favourites_hide_while_typing_or_income():
    open_add()
    assert d.exists("Chai", contains=True, timeout=5), "favourites missing on an empty form"
    keypad("5")
    assert not d.exists("Chai", contains=True, timeout=1), "favourites still shown with an amount typed"
    d.tap("Close")
    open_add()
    d.tap("New expense")  # the title opens the type sheet (111)
    d.tap("Income")
    assert d.exists("New income", timeout=3), "type sheet didn't switch to income"
    assert not d.exists("Chai", contains=True, timeout=1), "favourites shown for income"
    d.tap("Close")


@check
def favourites_screen_remove_undo_and_empty():
    recover()
    d.nav("More")
    d.tap("New expense form")
    d.scroll_to("one tap saves", contains=True)
    d.tap("one tap saves", contains=True)  # the Favourites row (its header has the same name)
    assert d.exists("One tap on New expense saves them", timeout=5), "Favourites screen header missing"
    d.tap("Remove Chai")
    snackbar("Favourite removed", undo=True)
    assert d.exists("Chai", contains=True, timeout=5), "Undo did not bring the favourite back"
    d.tap("Remove Chai")
    time.sleep(4.5)  # let the Undo go
    assert d.exists("No favourites yet", timeout=5), "no empty state without favourites"
    d.back()


@check
def long_press_save_as_favourite_and_duplicate():
    recover()
    d.nav("Activity")
    d.long_press("Food & drinks", contains=True)
    d.tap("Save as favourite")
    snackbar("Saved as a favourite")
    d.nav("Activity")
    d.long_press("Food & drinks", contains=True)
    d.tap("Duplicate to today")
    snackbar("Duplicated to today", undo=True)


@check
def split_needs_parts_that_add_up():
    # Split (08): Save stays off until 2+ parts add up; the payment stays one row.
    recover()
    d.nav("Activity")
    d.tap("Food & drinks", contains=True)
    d.tap("Split across categories")
    assert d.exists("Split payment", timeout=5), "Split screen did not open"
    d.tap("Save")
    assert d.exists("Split payment", timeout=2), "Save worked with one part"
    d.tap("Add a part")
    d.tap("Groceries")
    assert d.exists("The parts add up", timeout=3), "new part didn't take half"
    # A part's amount is a pill that opens the value sheet (95): type ₹1, Done.
    # The pill's clickable node has no text, so take the amount text: its centre sits on the pill.
    pills = [n for n in d.dump() if n.text.startswith("₹") and n.bounds[3] > n.bounds[1]]
    assert pills, "no amount pill on the split parts"
    tap_node(pills[-1])
    assert d.exists("Done", timeout=3), "the amount pill didn't open the value sheet"
    assert not d.exists("Fewer", contains=True, timeout=1), "− / + stepper still there"
    d.tap("1")
    d.tap("Done")
    assert d.exists("still to put in a category", contains=True, timeout=3), "what's left isn't shown"
    put = d.find("Put ", contains=True)
    assert put, "no 'Put ₹X in …' chip"
    tap_node(put)
    assert d.exists("The parts add up", timeout=3), "chip didn't settle the parts"
    d.tap("Save")
    snackbar("Split saved")
    assert d.exists("Groceries", contains=True, timeout=5), "detail doesn't list the split parts"


@check
def split_survives_rotation_and_undo_split():
    d.tap("Split across categories")
    d.shell("settings put system accelerometer_rotation 0")
    d.shell("settings put system user_rotation 1")
    time.sleep(2)
    ok = d.exists("Split payment", timeout=4)
    d.shell("settings put system user_rotation 0")
    time.sleep(2)
    assert ok, "Split screen lost on rotation"
    assert not crashed(), "crash on rotation"
    d.scroll_to("Put it back in one category")
    d.tap("Put it back in one category")
    snackbar("Back in one category")


@check
def refund_half_then_rest_then_full():
    # Refund (09): Half, then All of it; then nothing is left to refund.
    recover()
    d.nav("Activity")
    d.tap("Food & drinks", contains=True)
    d.tap("Add a refund")
    assert d.exists("Lowers", contains=True, timeout=5), "refund sheet has no 'Lowers … not counted as income'"
    d.tap("Half")
    d.tap("Save refund")
    snackbar("Refund saved")
    assert d.exists("back ·", contains=True, timeout=5), "detail doesn't show the refund"
    d.tap("Add a refund")
    assert d.exists("came back already", contains=True, timeout=5), "sheet doesn't say what came back"
    d.tap("All of it")
    d.tap("Save refund")
    snackbar("Refund saved")
    d.tap("Add a refund")
    assert d.exists("already paid back in full", contains=True, timeout=5), "full refund still offers more"
    d.tap("Cancel")


@check
def refund_row_in_activity_opens_original():
    recover()
    d.nav("Activity")
    d.tap("Refund · ", contains=True)
    assert d.exists("Refund for", contains=True, timeout=5), "refund detail doesn't link its expense"
    assert not d.exists("Edit", timeout=1), "a refund offers Edit"
    d.tap("Refund for", contains=True)
    assert d.exists("Add a refund", timeout=5), "link did not open the expense"


@check
def long_press_delete_with_undo():
    recover()
    d.nav("Activity")
    d.long_press("Food & drinks", contains=True)
    d.tap("Delete")
    snackbar("deleted", undo=True)


@check
def activity_delete_and_undo():
    recover()
    d.nav("Activity")
    d.tap("Food & drinks", contains=True)
    d.tap("Delete")
    # "Ask before delete" (Customise › Transactions) is off by default: Undo is the safety net.
    undo = []
    for _ in range(3):
        nodes = d.dump()
        undo = [n for n in nodes if n.label == "Undo"]
        if undo:
            break
        if any(n.label == "Delete this transaction?" for n in nodes):
            tap_node([n for n in nodes if n.label == "Delete"][-1])  # the dialog's button
    assert undo, "no Undo snackbar after delete"
    tap_node(undo[0])
    assert d.exists("Food & drinks", contains=True, timeout=5), "Undo did not bring the transaction back"


@check
def activity_search_no_results():
    recover()
    d.nav("Activity")
    d.tap("Search payee, amount, note")
    d.type_text("zzqq")
    assert d.exists("No results for", contains=True), "no empty state for a search miss"
    d.tap("Clear search")
    d.back()  # close the keyboard, so the nav bar is visible for the next check


@check
def capture_intro_turns_on():
    recover()
    d.nav("More")
    d.tap("Auto-capture")
    d.tap("How it works")
    d.tap("Next", timeout=6)  # the intro may still be opening
    d.tap("Next", timeout=6)
    d.tap("Allow", timeout=6)
    for _ in range(3):  # Android's own permission dialogs, one per permission group
        if not d.exists("ALLOW", timeout=3):
            break
        d.tap("ALLOW")
    # The intro opens the notification-access settings; grant it the way the switch there would.
    d.shell(f"cmd notification allow_listener {d.PACKAGE}/{d.PACKAGE}.capture.PaymentNotificationListener")
    d.back()
    assert d.exists("Bank SMS", timeout=5), "Allow did not end on Auto-capture"


@check
def sms_capture_asks_for_category():
    d.adb("emu", "sms", "send", "HDFCBK", "Rs.240.00 debited from A/c XX1234 to VPA thirdwave@okicici. UPI Ref 4821.")
    time.sleep(3)
    d.shell("cmd statusbar expand-notifications")
    ok = d.exists("240", contains=True, timeout=5)
    d.shell("cmd statusbar collapse")
    assert ok, "no capture notification for a bank SMS"


@check
def sms_capture_dedupes_and_reviews():
    msg = "Rs.180.00 debited from A/c XX1234 on 20-10-26 to VPA chaayos.paytm@hdfc. UPI Ref 629184001."
    d.adb("emu", "sms", "send", "HDFCBK", msg)
    time.sleep(2)
    d.adb("emu", "sms", "send", "HDFCBK", msg)  # the same payment twice: one entry
    time.sleep(3)
    recover()
    d.nav("Today")
    if not d.exists("2 payments to review", timeout=5):
        seen = [n.label for n in d.dump() if "to review" in n.label]
        raise AssertionError(f"expected 2 to review (the 240 and one 180), saw {seen or 'none'}")
    d.tap("2 payments to review")
    # Final 12 Nov (44): CheckCards. Both payees are new to Burrow: "?" cards, Save off, the bottom button waits.
    assert d.exists("Found in your SMS", contains=True, timeout=5), "To review has no hint line"
    assert d.exists("Pick categories first", timeout=2), "Save should wait while no payment is ready"
    d.tap("Food")  # the first card's chip: now it is ready
    save = d.find("Save 1 ready", timeout=3)
    assert save is not None, "no Save 1 ready button after picking a category"
    tap_node(save)
    time.sleep(0.5)
    nodes = d.dump()  # read and tap Undo from one dump: the snackbar lasts ~4 s
    assert any("isn’t sure" in n.label for n in nodes), "the other payment didn't stay waiting for a category"
    undo = [n for n in nodes if n.label == "Undo"]
    assert undo, "no Undo after saving"
    tap_node(undo[0])
    assert d.exists("Save 1 ready", timeout=5), "Undo did not bring the payment back"


@check
def review_shortcut_appears_with_payments():
    # Long-press the icon (02): "Review N payments" only while some wait.
    d.shell(f"am broadcast -a app.burrow.money.debug.HOME -p {d.PACKAGE}")
    time.sleep(2)
    out = d.shell("dumpsys shortcut")
    mine = out[out.find(f"Package: {d.PACKAGE}"):]
    for sid in ["new_expense", "new_income", "search", "review"]:
        assert f"id={sid}," in mine, f"shortcut missing: {sid}"


@check
def sunday_review_switch_off_sends_nothing():
    # Notifications › Sunday review (17): off means no notification.
    open_more_row("Notifications")
    rows = [n for n in d.dump() if n.checkable]
    assert d.exists("Sunday review"), "no Sunday review row in Notifications"
    sunday = d.find("Sunday review")
    switch = min((n for n in rows if abs(n.center[1] - sunday.center[1]) < 120), key=lambda n: abs(n.center[1] - sunday.center[1]))
    if switch.checked:
        tap_node(switch)
    d.shell("service call notification 1")  # clear all
    d.shell(f"am broadcast -a app.burrow.money.debug.SUNDAY_REVIEW -p {d.PACKAGE}")
    time.sleep(3)
    d.shell("cmd statusbar expand-notifications")
    sent = d.exists("Your week:", contains=True, timeout=3)
    d.shell("cmd statusbar collapse")
    assert not sent, "Sunday review sent with the switch off"
    d.back()
    open_more_row("Notifications")
    switch = [n for n in d.dump() if n.checkable]
    sunday = d.find("Sunday review")
    tap_node(min(switch, key=lambda n: abs(n.center[1] - sunday.center[1])))


@check
def sunday_review_notification_opens_review():
    # Sunday 6 PM (13): "Your week: ₹4,738 spent" · "N to review · …" → the Sunday review (14).
    d.shell(f"am broadcast -a app.burrow.money.debug.SUNDAY_REVIEW -p {d.PACKAGE}")
    time.sleep(3)
    d.shell("cmd statusbar expand-notifications")
    n = d.find("Your week:", contains=True, timeout=5)
    assert n, "no Sunday review notification"
    nodes = d.dump()
    assert has("to review", nodes), "notification doesn't say what waits"
    assert has("Takes 30 seconds", nodes), "notification line incomplete"
    tap_node(n)
    if not d.exists("Sunday review", timeout=10):
        n = d.find("Your week:", contains=True, timeout=2)  # the shade may still be animating
        if n:
            tap_node(n)
    assert d.exists("Sunday review", timeout=10), "notification did not open the Sunday review"


@check
def sunday_review_save_and_finish():
    if not d.exists("Sunday review", timeout=2):
        open_more_row("Notifications")
        d.tap("Sunday review")
    assert d.exists("Your week", contains=True, timeout=5), "review has no week header"
    # "Most on" and the bills row only show when the week has them; these two always do.
    for label in ["to review", "left in"]:
        assert d.scroll_to(label, contains=True), f"review row missing: {label}"
    save = d.find("and finish", contains=True)
    assert save, "no 'Save N and finish'"
    tap_node(save)
    snackbar("saved")
    recover()
    d.nav("Today")
    assert not d.exists("payments to review", contains=True, timeout=2), "payments still waiting after Save and finish"


@check
def not_understood_waits_for_teach():
    d.adb("emu", "sms", "send", "KOTAKB", "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184. Not you? Call 1860-266-2666")
    time.sleep(3)
    recover()
    d.nav("More")
    d.tap("Auto-capture")
    # "1 message not understood · Teach it the ones it missed" opens the list (sender on each card).
    assert d.exists("1 message not understood", timeout=5), "unreadable bank SMS not kept for Teach"
    d.tap("1 message not understood")
    # Review opens on its Not understood tab.
    assert d.exists("Not understood · 1", timeout=5), "Review did not open on the Not understood tab"
    # Final 12 Nov (116): a CheckCard with Burrow's own reading; Edit opens Fix it; Yes, save keeps the rule.
    assert d.exists("Zomato", timeout=3), "the card doesn't name who was paid"
    assert d.exists("Burrow read it as expense. Is this right?", timeout=2), "the card doesn't say what Burrow read"
    assert d.exists("−₹2,350", timeout=2), "Burrow didn't read the amount"
    d.tap("Edit")
    assert d.exists("What’s wrong?", timeout=5), "Edit didn't open Fix it"
    d.tap("Amount")
    assert d.exists("Tap the right amount", timeout=3), "fixing the amount doesn't ask for it"
    d.tap("Done")
    assert d.exists("Is this right?", timeout=3), "Done didn't show Burrow's reading"
    d.tap("Yes, save")
    assert d.exists("messages like this are read", contains=True, timeout=3), "saving didn't say messages like it are read now"
    d.back()


@check
def auto_capture_shows_recorded_by_itself():
    # Trust in capture (15): the ring and "N of M payments this month · you typed K". It needs a
    # payment recorded this month: the other checks' SMS carry dates ("on 20-10-26"), so send one without.
    d.shell("service call notification 1")  # clear all: grouped notifications hide their pills
    d.adb("emu", "sms", "send", "HDFCBK", f"Rs.99.00 debited from A/c XX1234 to VPA chaipoint@okicici. UPI Ref {int(time.time())}.")
    time.sleep(4)
    d.shell("cmd statusbar expand-notifications")
    time.sleep(2)
    pill = next((n for n in d.dump() if n.label in ("Food & drinks", "Groceries", "Shopping", "Transport")), None)
    assert pill, "no capture notification for the ₹99 payment"
    tap_node(pill)
    time.sleep(3)
    open_more_row("Auto-capture")
    assert d.exists("Recorded by itself", timeout=5), "no 'Recorded by itself' header"
    assert d.exists("this month · you typed", contains=True), "no trust line"  # "1 of 1 payment…" / "2 of 2 payments…"
    d.back()
    assert d.exists("% by itself", contains=True, timeout=5), "More tile doesn't show the share (\"92% by itself\")"


@check
def capture_status_follows_real_access():
    # 2026-10-24: More said "On" while Android had notification access off. Take both accesses
    # away: the tile says Paused, Today shows the paused note, Auto-capture's switches are off.
    listener = f"{d.PACKAGE}/{d.PACKAGE}.capture.PaymentNotificationListener"
    d.shell(f"cmd notification disallow_listener {listener}")
    d.shell(f"pm revoke {d.PACKAGE} android.permission.RECEIVE_SMS")
    d.shell(f"pm revoke {d.PACKAGE} android.permission.READ_SMS")
    try:
        d.adb("shell", "am", "start", "-n", f"{d.PACKAGE}/app.burrow.money.MainActivity")  # revoking SMS restarts the app
        time.sleep(2)
        d.nav("More")
        assert d.exists("Needs access", timeout=5), "More tile doesn't say Needs access without access"
        assert d.exists("Tap to fix", timeout=2), "More tile line doesn't say Tap to fix"
        assert not any(n.label == "On" and n.bounds[1] < 900 for n in d.dump()), "More tile still says On"
        d.nav("Today")
        assert d.exists("Auto-capture is paused", contains=True, timeout=5), "no paused note on Today"
        open_more_row("Auto-capture")
        switches = [n for n in d.dump() if n.checkable]
        assert switches and not any(n.checked for n in switches), "a capture switch shows on without access"
        assert d.exists("Android is blocking access", timeout=2) or d.exists("Auto-capture is off", timeout=2), "no note saying why"
        assert not d.exists("Try it", timeout=1), "Try it shown while nothing can capture"
        d.back()
    finally:
        d.shell(f"cmd notification allow_listener {listener}")
        d.shell(f"pm grant {d.PACKAGE} android.permission.RECEIVE_SMS")
        d.shell(f"pm grant {d.PACKAGE} android.permission.READ_SMS")
    # Access is read again when the app comes back (as from Settings): leave it, then reopen.
    d.shell("input keyevent KEYCODE_HOME")
    time.sleep(1)
    d.adb("shell", "am", "start", "-n", f"{d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(2)
    d.nav("More")
    assert d.exists("On", timeout=12), "More tile doesn't come back to On with access"


@check
def no_text_overlaps_at_large_font():
    # Big font sizes on Android 14+ (non-linear scaling) crowded wrapped lines into each other.
    problems = []
    for scale in ("1.3", "2.0"):
        d.shell(f"settings put system font_scale {scale}")
        time.sleep(2)
        try:
            for tab in ["Today", "Activity", "Plan", "Insights", "More"]:
                d.nav(tab)
                problems += [f"{scale}x {tab}: {p}" for p in d.overlapping_text()]
            open_more_row("Auto-capture")
            problems += [f"{scale}x Auto-capture: {p}" for p in d.overlapping_text()]
            d.back()
            open_add()
            problems += [f"{scale}x New expense: {p}" for p in d.overlapping_text()]
            d.tap("Close")
        finally:
            d.shell("settings put system font_scale 1.0")
            time.sleep(1)
    assert not problems, "text drawn over other text: " + "; ".join(problems)


@check
def customise_tabs_and_decimal_tiles():
    # Same pills as Plan; Currency's decimal tiles show the whole value.
    open_more_row("Today screen")
    for _ in range(4):
        if d.exists("Currency", timeout=1):
            break
        d.swipe(900, 330, 200, 330)
    d.tap("Currency")
    assert d.exists("1,240.00", timeout=5) or d.exists("1.240,00", timeout=1), "Always tile doesn't show the full value"
    assert not d.exists("1,240....", contains=True, timeout=1), "decimal value cut off"
    d.back()


@check
def new_goal_shows_all_eight_icons():
    d.nav("Plan")
    d.tap("Goals")
    d.tap("Add goal")
    labels_now = {n.desc for n in d.dump()}
    missing = [i for i in ["flight", "shield", "laptop mac", "home", "school", "redeem", "directions car", "flag"] if i not in labels_now]
    assert not missing, f"goal icons not on screen: {missing}"
    d.tap("Close")


@check
def activity_all_days_is_whole():
    d.nav("Activity")
    nodes = d.dump()
    # The bars are one tap area (TalkBack reads "Last 14 days, ₹…"): tap near its left end to pick a day.
    chart = next((n for n in nodes if n.desc and "Last 14 days" in n.desc), None)
    if chart is None:
        return  # chart folded on this phone; nothing to check
    x1, y1, x2, y2 = chart.bounds
    d.tap_xy(x1 + (x2 - x1) // 10, (y1 + y2) // 2)
    n = d.find("All days", timeout=5)
    assert n is not None, "All days not shown after picking a day"
    h = (n.bounds[3] - n.bounds[1]) / d.density()
    assert h >= 16, f"All days is cut off ({h:.0f} dp tall)"
    d.tap("All days")


@check
def licences_open_their_text():
    # Every licence row opens its full text inside the app (no browser, no network).
    open_more_row("About Burrow")
    d.scroll_to("Licences")
    d.tap("Licences")
    assert d.exists("GNU General Public License v3.0", timeout=5), "Burrow's own licence missing"
    for row, words in [("Burrow", "GNU GENERAL PUBLIC LICENSE"), ("Solar icons", "480 Design"), ("Lexend font", "SIL Open Font License")]:
        d.tap(row, contains=True)
        assert d.exists(words, contains=True, timeout=5), f"{row}: licence text not shown in the app"
        assert d.PACKAGE + "/" in d.shell("dumpsys window | grep mCurrentFocus"), f"{row}: left the app"
        d.back()
    d.back()
    d.back()


def pick_in_card(category="Other"):
    """A new payee's CheckCard (44): More opens What was it for? (241); picking a category turns its Save on."""
    if not d.exists("More", timeout=1):
        # More is last in the card's chip row, which scrolls sideways: swipe the row as a user would.
        chip = d.find("Food", timeout=3) or d.find("Groceries", timeout=1)
        assert chip is not None, "the card has no category chips"
        w = d.screen_size()[0]
        d.swipe(int(w * 0.85), chip.center[1], int(w * 0.15), chip.center[1], 300)
        time.sleep(0.5)
    d.tap("More")
    assert d.exists("What was it for?", timeout=3), "More didn't open the category grid"
    d.tap(category)
    time.sleep(0.5)


def save_everything_waiting():
    """To review: give every new payee a category on its card, then the one bottom button."""
    for _ in range(10):
        if d.find("isn’t sure", contains=True, timeout=2) is None:
            break
        pick_in_card("Other")
    for label in ("Save all", "Save payment", "ready"):
        n = d.find(label, contains=True, timeout=1)
        if n is not None:
            tap_node(n)
            return


@check
def review_cards_save_one_by_one():
    # Final 12 Nov (44, 241): To review as CheckCards: chips, More (the grid), Ignore · Edit · Save on each card.
    # Different amounts: the same amount and account within 10 minutes is one payment (dedupe).
    for ref, amount in (("7001", "75.00"), ("7002", "86.00")):
        d.adb("emu", "sms", "send", "HDFCBK", f"Rs.{amount} debited from A/c XX1234 to VPA tea{ref}@okaxis. UPI Ref {ref}.")
        time.sleep(3)
    recover()
    d.nav("Today")
    n = d.find("to review", contains=True, timeout=6)
    assert n is not None, "no payments to review"
    tap_node(n)
    assert d.exists("To review · ", contains=True, timeout=5), "Review has no To review tab with its count"
    # Final 12 Nov (44): each payment is a CheckCard approved in place. A new payee: Save is off until a chip.
    assert d.exists("Tea7001", contains=True, timeout=3) or d.exists("tea7001", contains=True, timeout=1), "the payment is not a card in To review"
    assert d.exists("Edit", timeout=2) and d.exists("Ignore", timeout=2), "the card has no Ignore · Edit"
    pick_in_card("Food")
    d.tap("Save")  # the first card's own Save (the one just given a category)
    assert d.exists("Saved", timeout=3), "Save on the card didn't save"
    # The second: a chip on the card, then its own Save.
    d.tap("Food")
    d.tap("Save")  # the first card's own Save (the one just given a category)
    assert d.exists("Saved", timeout=3), "the second card didn't save"
    d.back()


@check
def builtin_rules_list_like_yours_and_rename():
    open_more_row("Auto-capture")
    assert d.exists("Reads messages from banks", timeout=5), "Bank SMS line isn't the fixed one"
    d.scroll_to("Message rules")
    d.tap("Message rules")
    d.scroll_to("India")
    d.tap("India")
    assert d.exists("Rules in this pack", timeout=5), "the India pack doesn't list its rules"
    # "HDFC Bank / HDFCBK · read 3 times", as your own rules show
    rows = [n for n in d.dump() if " · read " in n.label]  # the row's line; tapping it opens the row
    assert rows, "pack rules aren't listed like your own (name · sender · read N times)"
    tap_node(rows[0])
    assert d.exists("What it reads", timeout=5), "built-in rule didn't open"
    assert not d.exists("Rename rule", timeout=1), "built-in rules can't be renamed"
    d.back(); d.back(); d.back(); d.back()


@check
def pasted_message_is_read_first():
    open_more_row("Auto-capture")
    d.scroll_to("Message rules")
    d.tap("Message rules")
    d.tap("Add rule")
    d.tap("Paste a message")
    nodes = [n for n in d.dump() if n.cls.endswith("EditText")]
    tap_node(nodes[0]); d.type_text("Rs.99.00 debited from A/c XX1111")
    tap_node(nodes[1]); d.type_text("TESTBK")
    d.tap("Continue")
    # Final 12 Nov (52): a pasted message is read the same way; its type comes from "debited".
    assert d.exists("Burrow read it as", timeout=5), "a pasted message isn't read first"
    assert d.exists("Expense", contains=True, timeout=2), "the type isn't filled in from the message's words"
    d.tap("Close")
    d.back()


@check
def activity_filter_badge():
    recover()
    d.nav("Activity")
    d.tap("Filter")
    d.tap("Food & drinks")
    d.tap("Show", contains=True)
    assert d.exists("Clear all", timeout=3), "active filter chips missing"
    d.tap("Clear all")


@check
def bill_mark_paid():
    recover()
    d.nav("Plan")
    if d.exists("Mark paid", timeout=3):
        d.tap("Mark paid")
        assert d.exists("Undo", timeout=3), "no Undo after Mark paid"


@check
def customise_drag_reorders_widgets():
    # Reorder (16): hold ⠿ and drag; no up/down arrows anywhere.
    open_more_row("Today screen")
    assert d.exists("Hold ⠿ and drag to reorder", timeout=5), "no drag hint"
    nodes = d.dump()
    assert not any(n.label.startswith("Move ") for n in nodes if n.clickable), "up/down arrow buttons still there"
    d.scroll_to("Goal")
    order = lambda: [n.label for n in d.dump() if n.label in ("This week", "Next bill", "Net worth", "Top category", "Money in and out", "Goal")]
    before = order()
    first = d.find(before[0])
    y = first.center[1]
    d.shell(f"input draganddrop 115 {y} 115 {y + 330} 1500")
    time.sleep(1.5)
    after = order()
    # How many places a fixed drag moves a row depends on row heights: it only has to go down.
    assert after != before and after.index(before[0]) > 0, f"drag did not move {before[0]} ({before} → {after})"


@check
def customise_expense_has_favourites():
    recover()
    d.nav("More")
    d.tap("New expense form")
    assert d.exists("Favourite", timeout=5), "no Favourite toggle tile"
    d.scroll_to("Favourites")
    d.tap("Favourites", contains=False)
    assert d.exists("Favourites", timeout=5), "Favourites screen did not open from Customise"
    d.back()


CASHEW = """account,amount,currency,title,note,date,income,type,category name,subcategory name,color,icon,emoji,budget,objective
HDFC,-240.0,USD,Third Wave Coffee,,2026-10-19 09:12:00.000,false,null,Dining,,,,,,
HDFC,-1400.0,USD,DMart,,2026-10-12 18:00:00.000,false,null,Groceries,,,,,,
Cash,-40,USD,Metro,,2026-10-18 08:30:00.000,false,null,Transport,,,,,,
HDFC,85000.0,USD,Salary,October,2026-10-01 10:00:00.000,true,null,Salary,,,,,,
Cash,-90,USD,Chai stall,,2026-09-02 08:30:00.000,false,null,Misc,,,,,,
Cash,-120,USD,Gift wrap,,2026-09-03 08:30:00.000,false,null,Other stuff,,,,,,
"""


@check
def import_cashew_matches_and_imports():
    # Import (10, 11): count and range, category mapping (unmatched in red), accounts, one button.
    push_file("cashew-export.csv", CASHEW)
    open_more_row("Import or export")
    assert d.exists("Bring your history", timeout=5), "Import or export has no header"
    for label in ["Cashew", "Walnut", "Money Manager", "Any spreadsheet (CSV)", "Restore a backup", "Export"]:
        assert d.scroll_to(label), f"Import or export missing: {label}"
    d.scroll_to_top()
    d.tap("Cashew")
    pick_file("cashew-export.csv")
    assert d.exists("Transactions from Cashew", timeout=8), "Check before import did not open"
    nodes = d.dump()
    # Unmatched categories become new ones (no "choose a category" step since 2026-10-22).
    assert has("4 matched · 2 new", nodes), "category matching off (expected 4 matched · 2 new)"
    assert has("“Dining” in Cashew", nodes), "Dining not mapped to a category"
    assert has("New category · 1 transaction", nodes), "unmatched categories not made new"
    d.scroll_to("Import 6 transactions")
    d.tap("Import 6 transactions")
    snack = [n.label for n in d.dump()]
    assert any("6 transactions added" in l for l in snack) or d.exists("Import or export", timeout=3), "import did not finish"


@check
def import_same_file_skips_duplicates():
    if not d.exists("Bring your history", timeout=2):  # More has a row with the same title
        open_more_row("Import or export")
    d.tap("Cashew")
    pick_file("cashew-export.csv")
    assert d.exists("Transactions from Cashew", timeout=8), "Check before import did not open"
    assert d.exists("6 of 6 matched", timeout=3), "mapping not remembered from the last import"
    d.scroll_to("Skip ones already here")
    assert d.exists("6 found", contains=True), "duplicates not found"
    assert d.exists("Import 0 transactions"), "duplicates would be imported again"
    d.back()


@check
def import_spreadsheet_columns_by_hand():
    # Any spreadsheet: names it can't guess; the user picks the columns once.
    push_file("odd-columns.csv", "when,what,how much\n2026-10-19,Chai,20\n2026-10-20,Metro,40\n")
    if not d.exists("Bring your history", timeout=2):  # More has a row with the same title
        open_more_row("Import or export")
    d.tap("Any spreadsheet (CSV)")
    pick_file("odd-columns.csv")
    assert d.exists("Columns", timeout=8), "no Columns section for a spreadsheet"
    assert d.exists("Choose a column"), "missing date column not flagged"
    d.tap("Date")
    d.tap("when")
    d.tap("Amount")
    d.tap("how much")
    d.tap("Payee")
    d.tap("what")
    d.scroll_to("Import 2 transactions")
    assert d.exists("Import 2 transactions", timeout=3), "rows not read after choosing columns"
    d.back()


@check
def import_bad_file_says_nothing_to_import():
    push_file("not-a-csv.csv", "hello\nthis is not an export\n")
    if not d.exists("Bring your history", timeout=2):  # More has a row with the same title
        open_more_row("Import or export")
    d.tap("Walnut")
    pick_file("not-a-csv.csv")
    assert d.exists("Nothing to import", timeout=8), "a bad file isn't explained"
    d.tap("Pick another file")
    assert d.exists("Bring your history", timeout=5), "Pick another file didn't go back"


@check
def shortcut_new_income_opens_form():
    d.shell(f"am start -a app.burrow.money.action.NEW_INCOME -n {d.PACKAGE}/app.burrow.money.MainActivity")
    assert d.exists("New income", timeout=6), "New income shortcut did not open the form"
    d.tap("Close")


@check
def shortcut_search_focuses_activity():
    d.shell(f"am start -a app.burrow.money.action.SEARCH -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(2)
    assert d.exists("Search payee, amount, note", timeout=5), "Search shortcut did not open Activity"
    shown = "mInputShown=true" in d.shell("dumpsys input_method")
    d.back()
    assert shown, "search field not focused (no keyboard)"


@check
def quick_settings_tile_opens_new_expense():
    tile = f"{d.PACKAGE}/.home.AddExpenseTile"
    d.shell(f"cmd statusbar add-tile {tile}")
    time.sleep(1)
    d.shell(f"cmd statusbar click-tile {tile}")
    assert d.exists("New expense", timeout=6), "tile did not open New expense"
    d.tap("Close")
    d.shell(f"cmd statusbar remove-tile {tile}")


@check
def widgets_redraw_without_crash():
    d.shell(f"am broadcast -a app.burrow.money.debug.HOME -p {d.PACKAGE}")
    time.sleep(2)
    assert not crashed(), "widgets crashed while redrawing"


@check
def dark_mode_and_large_text_new_screens():
    # The new screens in dark mode at the largest text: they open, nothing crashes, targets ≥ 48 dp.
    d.shell("cmd uimode night yes")
    d.shell("settings put system font_scale 1.3")
    time.sleep(2)
    try:
        problems = []
        open_add()
        problems += [f"New expense: {p}" for p in d.small_targets()]
        d.tap("Close")
        # Its row in Notifications sets the time; the review opens as its notification does.
        open_link("SUNDAY_REVIEW")
        assert d.exists("Your week", contains=True, timeout=5), "Sunday review broke in dark mode"
        problems += [f"Sunday review: {p}" for p in d.small_targets()]
        d.back()
        open_more_row("Import or export")
        problems += [f"Import: {p}" for p in d.small_targets()]
        assert not crashed(), "crash in dark mode / large text"
        assert not problems, "small touch targets: " + "; ".join(problems)
    finally:
        d.shell("cmd uimode night no")
        d.shell("settings put system font_scale 1.0")
        time.sleep(1)


@check
def no_internet_permission():
    # The smoke suite installs the foss build: it must not have INTERNET (only github does, for updates).
    perms = d.shell(f"dumpsys package {d.PACKAGE}")
    assert "android.permission.INTERNET" not in perms, "INTERNET permission is present"


@check
def touch_targets_48dp():
    recover()
    problems = []
    for tab in ["Today", "Activity", "Plan", "Insights", "More"]:
        d.nav(tab)
        problems += [f"{tab}: {p}" for p in d.small_targets()]
    assert not problems, "small touch targets: " + "; ".join(problems)


# --- deep links, launcher long-press, home-screen widgets (2026-10-31: none of these were tested) ---

ACTION = "app.burrow.money.action."


def open_link(action, extra_id=None, cold=False):
    """Starts the app the way a notification, shortcut or widget does: cold (process killed) or warm."""
    if cold:
        d.shell(f"am force-stop {d.PACKAGE}")
    extra = f" --el id {extra_id}" if extra_id is not None else ""
    # Single top, as the app's notifications, widgets and tile send them: without it an intent equal
    # to the one that started the task only brings the task forward (no onNewIntent), so a warm link
    # after the same cold one did nothing.
    d.shell(f"am start -W --activity-single-top -a {ACTION}{action}{extra} -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(2)


def on_a_tab():
    return d.on_tab()


# action → something only that screen shows
LINKS = [
    ("NEW_EXPENSE", "New expense"), ("NEW_INCOME", "New income"), ("OPEN_PLAN", "Budgets"),
    ("OPEN_INSIGHTS", "By category"), ("OPEN_ACCOUNTS", "Net worth"), ("OPEN_REVIEW", "To review"),
    ("SUNDAY_REVIEW", "Sunday review"), ("SEARCH", "Search payee, amount, note"),
]


@check
def deep_links_open_their_screen_cold_and_warm():
    problems = []
    for cold in (True, False):
        for action, expect in LINKS:
            recover()
            if not cold:
                d.nav("Insights")  # warm: from another tab
            open_link(action, cold=cold)
            if not d.exists(expect, timeout=6):
                problems.append(f"{action} ({'cold' if cold else 'warm'}) didn't show {expect!r}")
            if crashed():
                problems.append(f"{action} crashed")
            d.back()
            if action == "SEARCH":
                d.back()
    recover()
    assert not problems, "; ".join(problems)


@check
def deep_link_twice_opens_one_form():
    # A second New expense link (widget tapped twice) mustn't stack two forms.
    recover()
    open_link("NEW_EXPENSE")
    open_link("NEW_EXPENSE")
    assert d.exists("New expense", timeout=5), "New expense not open"
    d.tap("Close")
    time.sleep(1)
    assert on_a_tab(), "a second New expense was left under the first"


@check
def deep_links_with_missing_ids_dont_crash():
    # A notification for something since deleted: the app opens without crashing.
    problems = []
    for action in ("OPEN_TXN", "OPEN_BILL", "OPEN_CAPTURE"):
        for cold in (True, False):
            recover()
            open_link(action, extra_id=987654, cold=cold)
            if crashed():
                problems.append(f"{action} id 987654 ({'cold' if cold else 'warm'}) crashed")
            d.back()
        recover()
        open_link(action)  # no id at all
        if crashed():
            problems.append(f"{action} without an id crashed")
    recover()
    assert not problems, "; ".join(problems)


@check
def deep_link_before_onboarding_waits():
    # A shortcut tapped right after install: Welcome first, never a form over it.
    d.shell(f"pm clear {d.PACKAGE}")
    d.shell(f"pm grant {d.PACKAGE} android.permission.POST_NOTIFICATIONS")
    open_link("NEW_EXPENSE", cold=True)
    ok = d.exists("Get started", timeout=6)
    assert not crashed(), "crash opening a link before onboarding"
    assert ok, "a link before onboarding skipped Welcome"
    finish_welcome()
    assert d.exists("Today", timeout=10), "onboarding after a link didn't end on Today"


@check
def launcher_long_press_shows_shortcuts():
    # The real long-press on the app icon in the launcher's app list (02).
    d.shell("input keyevent KEYCODE_HOME")
    time.sleep(1)
    w, h = d.screen_size()
    d.swipe(w // 2, int(h * 0.85), w // 2, int(h * 0.2), 300)  # open all apps
    time.sleep(1.5)
    icon = d.find("Burrow", timeout=4)
    if icon is None:  # search for it
        d.type_text("Burrow")
        time.sleep(1.5)
        icon = d.find("Burrow", timeout=4)
    assert icon, "Burrow isn't in the launcher's app list"
    d.shell(f"input swipe {icon.center[0]} {icon.center[1]} {icon.center[0]} {icon.center[1]} 1200")
    time.sleep(1.5)
    nodes = d.dump()
    missing = [s for s in ("New expense", "New income", "Search") if not has(s, nodes)]
    assert not missing, f"long-press menu is missing: {missing}"
    d.tap("New income")
    assert d.exists("New income", timeout=6) and d.exists("Save", timeout=2), "the New income shortcut didn't open the form"
    d.tap("Close")


def pin_widget(which):
    """Asks the launcher to put a Burrow widget on the home screen (debug build) and accepts its dialog."""
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    d.shell(f"am broadcast -a app.burrow.money.debug.PIN_WIDGET --es which {which} -p {d.PACKAGE}")
    for label in ("Add to home screen", "ADD TO HOME SCREEN", "Add automatically", "Add"):
        if d.exists(label, timeout=3):
            d.tap(label)
            time.sleep(1.5)
            return True
    return False


def home_labels():
    """Labels on the first two home pages: three widgets don't fit one page, and Home from the
    launcher jumps to the first page, wherever the launcher put the last widget."""
    d.shell("input keyevent KEYCODE_HOME")
    time.sleep(2)
    d.shell("input keyevent KEYCODE_HOME")  # on the launcher already: to the first page
    time.sleep(1.5)
    labels = [n.label for n in d.dump() if n.label]
    w, h = d.screen_size()
    d.swipe(int(w * 0.9), h // 2, int(w * 0.1), h // 2, 250)
    time.sleep(1.5)
    labels += [n.label for n in d.dump() if n.label]
    d.shell("input keyevent KEYCODE_HOME")
    return labels


def find_on_home(label):
    """The node for [label] on the first or second home page, left showing so it can be tapped."""
    d.shell("input keyevent KEYCODE_HOME")
    time.sleep(2)
    d.shell("input keyevent KEYCODE_HOME")
    time.sleep(1.5)
    n = d.find(label, timeout=2)
    if n:
        return n
    w, h = d.screen_size()
    d.swipe(int(w * 0.9), h // 2, int(w * 0.1), h // 2, 250)
    time.sleep(1.5)
    return d.find(label, timeout=2)


def widget_amount(labels):
    """The big number on the safe-to-spend widget: the label right after its title."""
    for title in ("Safe to spend today", "Spent in"):
        for i, l in enumerate(labels):
            if l.startswith(title):
                # The title can show twice (its text and the widget's description): skip repeats.
                rest = [x for x in labels[i + 1:] if not x.startswith(title)]
                if rest:
                    return rest[0]
    return None


def save_expense(amount, category="Food & drinks"):
    open_link("NEW_EXPENSE")
    keypad(amount)
    d.tap(category)
    d.tap("Save")
    snackbar()


@check
def widgets_pin_and_show_numbers():
    for which in ("safe", "bill", "add"):
        assert pin_widget(which), f"the launcher didn't offer to add the {which} widget"
    labels = home_labels()
    assert widget_amount(labels), f"the safe-to-spend widget isn't on the home screen ({labels[:12]})"
    assert any("bill" in l.lower() or "due" in l.lower() for l in labels), "the next bill widget shows nothing"
    assert has("Add expense") or any(l == "Add expense" for l in labels), "the Add expense widget isn't there"


@check
def widget_updates_at_once_after_save_delete_and_undo():
    # Run alone, ₹777 is the first real entry: the sample month goes with it, so "before" can't come back.
    recover()
    d.nav("Today")
    samples = d.exists("These are sample transactions", timeout=3)
    before = widget_amount(home_labels())
    save_expense("777")
    after_save = widget_amount(home_labels())
    assert after_save and after_save != before, f"widget didn't change after saving ₹777 ({before} → {after_save})"
    recover()
    d.nav("Activity")
    # The row, lowest: the chart above also says "₹777".
    row = [n for n in d.dump() if "777" in n.label and n.bounds[3] > n.bounds[1]][-1]
    x, y = row.center
    d.swipe(x, y, x, y, 900)
    d.tap("Delete")
    snackbar("deleted")
    after_delete = widget_amount(home_labels())
    assert after_delete != after_save, f"widget didn't change after the delete ({after_save} → {after_delete})"
    if not samples:
        assert after_delete == before, f"widget didn't go back after the delete ({after_save} → {after_delete}, expected {before})"


@check
def widget_updates_after_budget_change():
    before = widget_amount(home_labels())
    recover()
    d.nav("Plan")
    d.tap("Budgets")
    d.tap("budget", contains=True)  # the month's budget opens its page; Edit opens the value sheet
    if d.exists("Edit", timeout=3):
        d.tap("Edit")
    if d.exists("Monthly budget", timeout=3) or d.exists("Done", timeout=1) or d.exists("Save", timeout=1):
        keypad("45000")
        d.tap("Save") if d.exists("Save", timeout=1) else d.tap("Done")
    after = widget_amount(home_labels())
    assert after != before, f"widget didn't change after the budget changed ({before} → {after})"


@check
def widget_next_bill_follows_new_and_deleted_bills():
    recover()
    d.nav("Plan")
    d.tap("Coming up")
    d.tap("Add bill")
    d.tap("Netflix, rent, electricity…")
    d.type_text("Zumba")
    d.back()
    tap_amount_field()
    keypad("5")
    d.tap("Done")
    d.tap("Save")
    labels = home_labels()
    assert any("Zumba" in l for l in labels) or any("due" in l.lower() for l in labels), "the bill widget shows no bill"
    recover()
    d.nav("Plan")
    d.tap("Coming up")
    d.tap("Zumba")
    d.tap("Delete bill")
    d.tap("Delete")
    assert not any("Zumba" in l for l in home_labels()), "the bill widget still shows a deleted bill"


@check
def widget_hides_amounts_with_the_app():
    open_more_row("Hide amounts")
    switch = next((n for n in d.dump() if n.checkable), None)
    assert switch, "no Hide amounts switch"
    if not switch.checked:
        tap_node(switch)
    labels = home_labels()
    hidden = any("••••" in l for l in labels)
    open_more_row("Hide amounts")
    switch = next((n for n in d.dump() if n.checkable and n.checked), None)
    if switch:
        tap_node(switch)
    assert hidden, "the widget shows amounts while Hide amounts is on"
    assert not any("••••" in l for l in home_labels()), "the widget still hides amounts after turning it off"


@check
def widget_taps_open_the_app():
    add = find_on_home("Add expense")
    assert add, "no Add expense widget to tap"
    tap_node(add)
    assert d.exists("New expense", timeout=6), "tapping the Add expense widget didn't open New expense"
    d.tap("Close")
    amount = widget_amount(home_labels())
    n = find_on_home(amount) if amount else None
    assert n, "no safe-to-spend widget to tap"
    tap_node(n)
    assert on_a_tab() or d.exists("Today", timeout=5), "tapping the widget didn't open the app"


@check
def category_sheet_search():
    # 184 / 185: the category sheet filters by name; no match shows the shared empty state.
    recover()
    open_add()
    # The lowest "Food & drinks": a Recent pill above can read the same (no payee: its category).
    chip = max((n for n in d.dump() if n.label == "Food & drinks"), key=lambda n: n.center[1])
    w = d.screen_size()[0]
    for _ in range(3):
        if d.exists("More", timeout=0.5):
            break
        d.drag(int(w * 0.85), chip.center[1], int(w * 0.15), chip.center[1])
    d.tap("More")
    field = next((n for n in d.dump() if n.cls.endswith("EditText")), None)
    assert field, "the category sheet has no search field"
    tap_node(field)
    d.type_text("trans")
    assert d.exists("Transport", timeout=3), "search didn't find Transport"
    assert not d.exists("Food & drinks", timeout=1), "search didn't filter the list"
    d.tap("Clear", contains=True)
    tap_node(next(n for n in d.dump() if n.cls.endswith("EditText")))
    d.type_text("zzzz")
    assert d.exists("No results for", contains=True, timeout=3), "no empty state for a search with no match"
    assert d.exists("Clear search", timeout=2), "no Clear search button"
    d.tap("Clear search")
    assert d.exists("Food & drinks", timeout=3), "Clear search didn't bring the list back"
    recover()


@check
def foss_backup_has_no_drive():
    # foss (F-Droid) has no Google code: Backup has no Google Drive section.
    open_more_row("Backup")
    assert d.exists("Backup and restore", timeout=5), "Backup didn't open"
    assert not d.scroll_to("Google Drive"), "foss shows Google Drive"
    recover()


@check
def play_backup_shows_drive():
    # 179: the play build has a Google Drive section after Settings. Installed over foss (same debug
    # key), then the suite goes back to foss with a fresh install.
    play = os.path.join(ROOT, "app/build/outputs/apk/play/debug/app-play-debug.apk")
    try:
        out = d.adb("install", "-r", play)
        assert "Success" in out, f"play install failed: {out}"
        d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
        time.sleep(3)
        open_more_row("Backup")
        # DRIVE_BACKUP is off until the Google Cloud setup (user, 12 Nov): no Drive section, no Connect.
        assert d.exists("Backup and restore", timeout=5), "Backup didn't open"
        assert not d.scroll_to("Connect Google Drive"), "Connect Google Drive shows while DRIVE_BACKUP is off"
    finally:
        recover()
        fresh_install()
        finish_welcome()


@check
def welcome_skip_budget_leaves_none():
    # Last: a second fresh install that skips the budget step. Today then shows what was spent.
    fresh_install()
    finish_welcome(budget=False)
    assert d.exists("Today", timeout=10), "Today not shown after Skip for now"
    d.tap("Remove")  # the sample month (and its sample budget) go
    assert d.exists("Spent in", contains=True, timeout=5) or d.exists("Nothing here yet", contains=True, timeout=3), "a budget appeared after Skip for now"


@check
def two_cards_one_bill():
    # Designs 186–201: a second card from the same bank on the Same bill; SMS from either card's
    # digits land on the one account and each payment says which card paid it.
    recover()
    d.nav("Today")
    d.tap("Accounts")
    for name, digits, hint in (("Flipkart", "7711", "Regalia, Millennia…"), ("RuPay", "8822", "Visa, Amex, RuPay…")):
        d.tap("Add account")
        d.tap("Credit card")
        d.tap("Choose the bank")
        d.tap("Axis Bank")
        if digits == "8822":
            assert d.exists("Same bill", timeout=3), "a second Axis card isn't offered the Same bill"
        d.tap(hint)
        d.type_text(name)
        d.back()
        d.tap("Used to match its SMS")
        d.type_text(digits)
        d.back()
        d.tap("Save")
    assert d.exists("Flipkart ••7711 · RuPay ••8822", contains=True, timeout=5), "the two cards aren't one account"
    for amount, digits, shop in (("311.00", "7711", "CROMA"), ("422.00", "8822", "DECATHLON")):
        d.adb("emu", "sms", "send", "HDFCBK", f"Spent Rs.{amount} On HDFC Bank Card {digits} At {shop} On 2026-11-12:10:11:03.Not You? To Block+Reissue Call 18002586161")
        time.sleep(3)
    recover()
    d.nav("Today")
    n = d.find("to review", contains=True, timeout=6)
    assert n is not None, "the card payments aren't waiting in To review"
    tap_node(n)
    save_everything_waiting()  # earlier checks may leave others waiting; new shops get a category first
    time.sleep(1)
    d.back()
    d.nav("Activity")
    d.tap("Croma")  # payees read tidied ("CROMA" → "Croma")
    assert d.exists("Flipkart ••7711", contains=True, timeout=5), "the payment doesn't say which card paid it"
    d.back()
    d.tap("Decathlon")
    assert d.exists("RuPay ••8822", contains=True, timeout=5), "the second card's payment doesn't say which card"
    d.back()


@check
def savings_once_add_money_and_other_kinds():
    # Savings (202–228, SAVINGS-SIMPLE): a Once deposit; Add money starts a new deposit (205, 207);
    # Every month and Any time save too; Edit savings opens like Edit account.
    recover()
    d.nav("Today")
    d.tap("Accounts")

    def new_savings(name, kind, amount, rate=None, market=False):
        d.tap("Add account")
        d.tap("Savings")
        d.tap("Deposit, emergency fund, child’s future…")
        d.type_text(name)
        d.back()
        d.tap(kind)
        if market:
            d.tap("Market value")
        d.tap("Type the amount")
        keypad(amount)
        d.tap("Done")
        if rate:
            d.tap("Add the rate")
            keypad(rate)
            d.tap("Done")
        d.tap("Save")
        assert d.exists("Saved", timeout=4) or d.exists(name, timeout=2), f"{name} ({kind}) didn't save"

    new_savings("SBI FD", "Once", "50000", rate="7.1")
    d.scroll_to("SBI FD")
    d.tap("SBI FD")
    assert d.exists("at maturity", contains=True, timeout=5), "a fixed deposit doesn't say what it pays at maturity"
    d.tap("Add money")
    assert d.exists("New deposit", timeout=4), "Add money to a Once deposit doesn't start a new deposit (207)"
    d.tap("Add")
    snackbar()
    assert d.scroll_to("Deposits"), "the savings page has no Deposits list"
    d.tap("Edit")
    assert d.exists("Archive account", timeout=4) or d.scroll_to("Archive account"), "Edit savings isn't like Edit account"
    d.tap("Close")
    d.back()
    new_savings("RD", "Every month", "2000", rate="6.5")
    new_savings("Gold", "Any time", "10000", market=True)
    assert d.scroll_to("Gold"), "a market-value savings isn't listed"
    d.back()


def recover():
    """After a failure, back out of sheets and inner screens to the tabs so later checks run."""
    d.shell("cmd statusbar collapse")
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    for _ in range(5):
        if d.on_tab():
            return
        d.back()


def main(filters):
    selected = [c for c in CHECKS if not filters or any(f in c.__name__ for f in filters)]
    if not os.path.exists(APK):
        sys.exit(f"Build first: ./gradlew :app:assembleFossDebug (missing {APK})")
    # A Settings screen left open from another app catches "back" from the notification-access page.
    d.shell("am force-stop com.android.settings")
    d.shell("settings put system font_scale 1.0")
    # Animations off: the blinking caret kept every dump waiting (3 min a check). Lists scroll with
    # d.drag, which doesn't fling. On again at the end.
    d.animations(False)
    try:
        return run(selected)
    finally:
        d.animations(True)


def run(selected):
    fresh_install()
    if welcome_shows_once not in selected and d.exists("Get started", timeout=3):
        finish_welcome()
    failures = 0
    for c in selected:
        d.adb("logcat", "-b", "crash", "-c")
        try:
            c()
            crash = crashed()
            if crash:
                raise AssertionError(f"the app crashed: {crash}")
            print(f"PASS  {c.__name__}", flush=True)
        except Exception as e:  # noqa: BLE001 - report and continue
            failures += 1
            print(f"FAIL  {c.__name__}: {e}", flush=True)
            # The screen at the failure: build/smoke/<check>.png
            os.makedirs("build/smoke", exist_ok=True)
            with open(f"build/smoke/{c.__name__}.png", "wb") as f:
                f.write(subprocess.run([d.ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
            if os.environ.get("VERBOSE"):
                traceback.print_exc()
            recover()
    print(f"\n{len(selected) - failures}/{len(selected)} passed")
    return failures


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
