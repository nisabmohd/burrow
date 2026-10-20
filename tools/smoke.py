#!/usr/bin/env python3
"""End-to-end smoke check of every Burrow feature on a connected emulator or phone.

Installs a fresh debug build (which seeds the sample month on first launch), then runs one short
check per feature and prints PASS/FAIL. Exit code is the number of failures.

    ./gradlew :app:assembleFossDebug
    python3 tools/smoke.py            # all checks
    python3 tools/smoke.py today notes  # only checks whose name contains these words

Checks use visible text and TalkBack labels, so they also catch missing labels.
"""
import os
import re
import sys
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


# --- checks, in the order a new user meets the features -----------------------------------
# Burrow's checks. Add one per feature as you build it (find by visible text or label, never coordinates).


def finish_welcome():
    """Welcome (01) → main currency (02) → accounts (03): keep the defaults."""
    d.tap("Get started")
    if d.exists("Continue", timeout=3):
        d.tap("Continue")
    if d.exists("Done", timeout=3):
        d.tap("Done")


@check
def welcome_shows_once():
    assert d.exists("Get started"), "welcome screen missing on first launch"
    finish_welcome()
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


# Plan, Accounts, Insights: before the first real entry, which clears the sample month.


@check
def plan_overdue_mark_paid_and_undo():
    recover()
    d.nav("Plan")
    assert d.exists("is overdue", contains=True, timeout=5), "no overdue note on Plan (sample electricity bill)"
    paid = [n for n in d.dump() if n.label == "Mark paid"][0]
    x1, y1, x2, y2 = paid.bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
    time.sleep(0.5)
    nodes = d.dump()
    undo = [n for n in nodes if n.label == "Undo"]
    assert undo, "no Undo after Mark paid"
    x1, y1, x2, y2 = undo[0].bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
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
    x1, y1, x2, y2 = days[0].bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
    assert d.exists("Clear all", timeout=5), "heatmap day did not open Activity with that day"
    d.tap("Clear all")


@check
def add_expense_saves_and_undo():
    recover()
    d.nav("Today")
    d.tap("Add expense")
    for k in "240":
        d.tap(k)
    assert d.exists("Save"), "Save button missing"
    d.tap("Choose a category")
    d.tap("Food & drinks")
    d.tap("Save")
    assert d.exists("Undo", timeout=3), "no Undo snackbar after saving"


@check
def activity_delete_and_undo():
    d.nav("Activity")
    # "Food & drinks": the expense add_expense saved, or a sample row when run alone.
    d.tap("Food & drinks")
    d.tap("Delete")
    # "Ask before delete" (Customise › Transactions) is off by default: Undo is the safety net.
    # One screen read takes ~2.5 s and the snackbar lasts ~4 s, so each read looks for both.
    undo = []
    for _ in range(3):
        nodes = d.dump()
        undo = [n for n in nodes if n.label == "Undo"]
        if undo:
            break
        if any(n.label == "Delete this transaction?" for n in nodes):
            x1, y1, x2, y2 = [n for n in nodes if n.label == "Delete"][-1].bounds  # the dialog's button
            d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
    assert undo, "no Undo snackbar after delete"
    x1, y1, x2, y2 = undo[0].bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
    assert d.exists("Food & drinks", timeout=5), "Undo did not bring the transaction back"


@check
def activity_search_no_results():
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
    ok = d.exists("₹240", contains=True, timeout=5)
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
    assert d.exists("2 payments to review", timeout=5), "expected 2 to review (the 240 and one 180)"
    d.tap("2 payments to review")
    save = [n for n in d.dump() if n.label == "Save 2 payments"]
    assert save, "no Save 2 payments button"
    x1, y1, x2, y2 = save[0].bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
    time.sleep(0.5)
    nodes = d.dump()  # read and tap Undo from one dump: the snackbar lasts ~4 s
    assert any(n.label == "All caught up" for n in nodes), "review not emptied after saving"
    undo = [n for n in nodes if n.label == "Undo"]
    assert undo, "no Undo after saving"
    x1, y1, x2, y2 = undo[0].bounds
    d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
    assert d.exists("Save 2 payments", timeout=5), "Undo did not bring the payments back"


@check
def not_understood_waits_for_teach():
    d.adb("emu", "sms", "send", "KOTAKB", "Rs.2,350.00 debited from A/c XX4471 on 18-10-26 to VPA zomato@hdfcbank Ref 629184. Not you? Call 1860-266-2666")
    time.sleep(3)
    recover()
    d.nav("More")
    d.tap("Auto-capture")
    assert d.exists("1 message not understood", timeout=5), "unreadable bank SMS not kept for Teach"
    d.tap("1 message not understood")
    assert d.exists("Teach a message", timeout=5), "Teach did not open"
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
    d.nav("Plan")
    if d.exists("Mark paid", timeout=3):
        d.tap("Mark paid")
        assert d.exists("Undo", timeout=3), "no Undo after Mark paid"


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


def recover():
    """After a failure, back out of sheets and inner screens to the tabs so later checks run."""
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
    for _ in range(4):
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
        try:
            c()
            print(f"PASS  {c.__name__}")
        except Exception as e:  # noqa: BLE001 - report and continue
            failures += 1
            print(f"FAIL  {c.__name__}: {e}")
            if os.environ.get("VERBOSE"):
                traceback.print_exc()
            recover()
    print(f"\n{len(selected) - failures}/{len(selected)} passed")
    return failures


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
