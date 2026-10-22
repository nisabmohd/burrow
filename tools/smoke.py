
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
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
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
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
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
    for label in ["This week", "Next bill", "Net worth", "Top category", "Money in and out", "Goal"]:
        assert d.scroll_to(label, contains=True), f"widget missing: {label}"


# Plan, Accounts, Insights: before the first real entry, which clears the sample month.


@check
def plan_overdue_mark_paid_and_undo():
    recover()
    d.nav("Plan")
    assert d.exists("is overdue", contains=True, timeout=5), "no overdue note on Plan (sample electricity bill)"
    paid = [n for n in d.dump() if n.label == "Mark paid"][0]
    tap_node(paid)
    time.sleep(0.5)
    nodes = d.dump()
    undo = [n for n in nodes if n.label == "Undo"]
    assert undo, "no Undo after Mark paid"
    tap_node(undo[0])
    assert d.exists("is overdue", contains=True, timeout=5), "Undo did not bring the overdue bill back"


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
    for label in ["Spent", "A day", "Biggest", "By category", "Patterns", "Busiest day", "No-spend days", "You kept", "Top payee", "by day"]:
        assert d.scroll_to(label, contains=True), f"Insights section missing: {label}"


@check
def insights_long_range_and_category_chart():
    # Charts with many bars (weekly / monthly buckets) and Category insights' by-day bars.
    recover()
    d.nav("Insights")
    d.tap("Dates")
    d.tap("This year")
    d.tap("Show", contains=True)
    assert d.exists("By category", timeout=5), "Insights broke with a year range"
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
    d.tap("Choose a category")
    d.tap("Food & drinks")
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


@check
def favourite_chip_saves_with_one_tap():
    # The Favourite chip (04) keeps an expense for one tap; the chip on New expense saves it.
    open_add()
    keypad("20")
    d.tap("Paid to")
    d.type_text("Chai")
    d.back()  # keyboard down
    d.tap("Choose a category")
    d.tap("Food & drinks")
    # The extras sit under the rows (the form scrolls on shorter phones), in a row that scrolls
    # sideways: Note, Repeat, Attach, Favourite, Lend or borrow.
    if not d.exists("Note", timeout=1):
        d.swipe(540, 1200, 540, 700)
    note = d.find("Note")
    if note and not d.exists("Favourite", timeout=1):
        d.swipe(900, note.center[1], 200, note.center[1])
    d.tap("Favourite")
    d.tap("Save")
    snackbar()
    open_add()
    assert d.exists("Favourites · one tap saves", timeout=5), "no favourites row on New expense"
    chip = d.find("Chai", contains=True)
    assert chip, "no Chai favourite chip"
    tap_node(chip)
    nodes = snackbar("saved")
    assert has("Chai", nodes), "snackbar doesn't name the favourite"
    assert has("Today", nodes), "one tap didn't close New expense"


@check
def favourites_hide_while_typing_or_income():
    open_add()
    assert d.exists("Favourites · one tap saves", timeout=5), "favourites missing on an empty form"
    keypad("5")
    assert not d.exists("Favourites · one tap saves", timeout=1), "favourites still shown with an amount typed"
    d.tap("Close")
    open_add()
    d.tap("Income")
    assert not d.exists("Favourites · one tap saves", timeout=1), "favourites shown for income"
    d.tap("Close")


@check
def favourites_screen_remove_undo_and_empty():
    open_add()
    d.tap("Edit")
    assert d.exists("One tap on New expense saves them", timeout=5), "Favourites screen header missing"
    d.tap("Remove Chai")
    snackbar("Favourite removed", undo=True)
    assert d.exists("Chai", contains=True, timeout=5), "Undo did not bring the favourite back"
    d.tap("Remove Chai")
    time.sleep(4.5)  # let the Undo go
    assert d.exists("No favourites yet", timeout=5), "no empty state without favourites"
    d.back()
    d.tap("Close")


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
    d.tap("More for Groceries")
    assert d.exists("more than the payment", contains=True, timeout=3), "too much isn't shown"
    take = d.find("Take ", contains=True)
    assert take, "no 'Take … from' chip"
    tap_node(take)
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
    d.tap("Next")
    d.tap("Next")
    d.tap("Allow")
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
    save = [n for n in d.dump() if n.label == "Save 2 payments"]
    assert save, "no Save 2 payments button"
    tap_node(save[0])
    time.sleep(0.5)
    nodes = d.dump()  # read and tap Undo from one dump: the snackbar lasts ~4 s
    assert any(n.label == "All caught up" for n in nodes), "review not emptied after saving"
    undo = [n for n in nodes if n.label == "Undo"]
    assert undo, "no Undo after saving"
    tap_node(undo[0])
    assert d.exists("Save 2 payments", timeout=5), "Undo did not bring the payments back"


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
    assert d.exists("Teach it 1 message it missed", timeout=5), "unreadable bank SMS not kept for Teach"
    assert d.exists("KOTAKB", contains=True), "Teach row doesn't say who sent it"
    d.tap("Teach it 1 message it missed")
    assert d.exists("Teach a message", timeout=5), "Teach did not open"
    d.back()


@check
def auto_capture_shows_recorded_by_itself():
    # Trust in capture (15): the ring and "N of M payments this month · you typed K".
    open_more_row("Auto-capture")
    assert d.exists("Recorded by itself", timeout=5), "no 'Recorded by itself' header"
    assert d.exists("payments this month · you typed", contains=True), "no trust line"
    d.back()
    assert d.exists("recorded by itself", contains=True, timeout=5), "More tile doesn't show the share"


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
    if not d.exists("Import or export", timeout=2):
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
    if not d.exists("Import or export", timeout=2):
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
    if not d.exists("Import or export", timeout=2):
        open_more_row("Import or export")
    d.tap("Walnut")
    pick_file("not-a-csv.csv")
    assert d.exists("Nothing to import", timeout=8), "a bad file isn't explained"
    d.tap("Pick another file")
    assert d.exists("Bring your history", timeout=5), "Pick another file didn't go back"


@check
def shortcut_new_income_opens_form():
    d.shell(f"am start -a app.burrow.money.action.NEW_INCOME -n {d.PACKAGE}/.MainActivity")
    assert d.exists("New income", timeout=6), "New income shortcut did not open the form"
    d.tap("Close")


@check
def shortcut_search_focuses_activity():
    d.shell(f"am start -a app.burrow.money.action.SEARCH -n {d.PACKAGE}/.MainActivity")
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
        open_more_row("Notifications")
        d.tap("Sunday review")
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


@check
def welcome_skip_budget_leaves_none():
    # Last: a second fresh install that skips the budget step. Today then shows what was spent.
    fresh_install()
    finish_welcome(budget=False)
    assert d.exists("Today", timeout=10), "Today not shown after Skip for now"
    d.tap("Remove")  # the sample month (and its sample budget) go
    assert d.exists("Spent in", contains=True, timeout=5) or d.exists("Nothing here yet", contains=True, timeout=3), "a budget appeared after Skip for now"


def recover():
    """After a failure, back out of sheets and inner screens to the tabs so later checks run."""
    d.shell("cmd statusbar collapse")
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
    for _ in range(5):
        if d.exists("Today", timeout=1) and d.exists("Insights", timeout=1):
            return
        d.back()


def main(filters):
    selected = [c for c in CHECKS if not filters or any(f in c.__name__ for f in filters)]
    if not os.path.exists(APK):
        sys.exit(f"Build first: ./gradlew :app:assembleFossDebug (missing {APK})")
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
            if os.environ.get("VERBOSE"):
                traceback.print_exc()
            recover()
    print(f"\n{len(selected) - failures}/{len(selected)} passed")
    return failures


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
