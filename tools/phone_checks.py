#!/usr/bin/env python3
"""The "Check on a real phone" list, done on an emulator with workarounds (2026-11-02).

Each check stands in for something TODO.md used to say only a real phone could show:

    backup      Backup → restore round trip, and a restore on a wiped app (password asked, wrong one refused)
    csv         CSV export, then import of that file on a wiped app
    budget      Budget alerts at 80% and 100% from bank SMS, once each per month
    bill        A bank SMS that pays a due bill marks it Paid
    words       Category words and the hidden merchant: "Medicines" + Health from a pharmacy SMS, then the alias
    restricted  Restricted settings note after installing the APK from a file (Files → Package installer)
    rtl         Right-to-left layout in the ar-XB pseudolocale (debug builds; no RTL translation yet)
    perf        20k transactions: import, Activity scroll jank and Insights load time

    ./gradlew :app:assembleFossDebug
    python3 tools/phone_checks.py               # all
    python3 tools/phone_checks.py budget bill   # some

Uses the smoke suite's helpers; installs a fresh foss debug build first. Set ANDROID_SERIAL when more
than one emulator runs.
"""
import os
import random
import re
import subprocess
import sys
import time
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(__file__))
import device as d  # noqa: E402
import smoke as s  # noqa: E402

PASSWORD = "burrow123"


def labels():
    return [n.label for n in d.dump() if n.label]


def sms(body, sender="HDFCBK"):
    d.adb("emu", "sms", "send", sender, body)
    time.sleep(4)


def notifications():
    d.shell("cmd statusbar expand-notifications")
    time.sleep(2)
    nodes = d.dump()
    return nodes


def hide_keyboard():
    """Back closes the keyboard, but only press it when one shows: else Back leaves the screen."""
    if "mInputShown=true" in d.shell("dumpsys input_method"):
        d.back()
        time.sleep(1)


def collapse():
    d.shell("cmd statusbar collapse")


def pay(amount, upi, pill=("Shopping", "Food & drinks")):
    """A bank SMS for [amount] to [upi], saved from the capture notification with the first [pill] shown."""
    sms(f"Rs.{amount}.00 debited from A/c XX1234 to VPA {upi}. UPI Ref {int(time.time())}.")
    nodes = notifications()
    hit = next((n for p in pill for n in nodes if n.label == p), None)
    assert hit, f"no capture notification with a {pill} pill for {amount}"
    s.tap_node(hit)
    time.sleep(3)


def wipe_and_onboard():
    d.shell(f"pm clear {d.PACKAGE}")
    d.shell(f"pm grant {d.PACKAGE} android.permission.POST_NOTIFICATIONS")
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(3)


def in_activity(text):
    s.recover()
    d.nav("Activity")
    time.sleep(1)
    return any(text in l for l in labels())


def check_backup():
    s.open_add()
    s.keypad("777")
    d.tap("Food & drinks")
    d.tap("Save")
    time.sleep(5)
    s.open_more_row("Backup")
    if d.exists("Set a password", timeout=2):
        d.tap("Set a password")
        d.tap("Password")
        d.type_text(PASSWORD)
        d.tap("Password again")
        d.type_text(PASSWORD)
        s.tap_last("Save")
    if d.exists("Pick a folder", timeout=2):
        d.tap("Pick a folder")
        d.tap("Documents", timeout=6)
        d.tap("USE THIS FOLDER")
        d.tap("ALLOW")
    d.tap("Back up now")
    time.sleep(3)
    # Earlier checks add payments too: the backup holds this one and theirs.
    held = next((n for n in d.dump() if re.fullmatch(r"[\d,]+ transactions?", n.label)), None)
    assert held, "the backup doesn't hold the new expense"
    # Same phone: delete it, restore, it's back.
    s.recover()
    d.nav("Activity")
    row = [n for n in d.dump() if n.label == "−₹777"][-1]
    x1, y1, x2, y2 = row.bounds
    d.shell(f"input swipe {(x1 + x2) // 2} {(y1 + y2) // 2} {(x1 + x2) // 2} {(y1 + y2) // 2} 900")
    d.tap("Delete")
    time.sleep(5)
    assert not in_activity("777"), "the expense wasn't deleted"
    s.open_more_row("Backup")
    d.tap(held.label)
    d.tap("Replace")
    time.sleep(4)
    assert in_activity("777"), "restore didn't bring the expense back"
    # A new phone: wiped app, Welcome › Restore a backup, wrong password first.
    name = next(l for l in d.shell("ls /sdcard/Documents").split() if l.endswith(".burrow"))
    wipe_and_onboard()
    d.tap("Restore a backup")
    s.pick_file(name)
    d.tap("Password")
    d.type_text("wrongpass")
    s.tap_last("Restore")
    assert d.exists("Wrong password. Try again.", timeout=5), "a wrong password wasn't refused"
    d.tap("Clear")
    d.type_text(PASSWORD)
    s.tap_last("Restore")
    d.tap("Replace", timeout=6)
    time.sleep(5)
    assert in_activity("777"), "restore on a wiped app didn't bring the expense back"


def check_csv():
    # One real expense of its own: sample data never goes into an export.
    s.open_add()
    s.keypad("333")
    d.tap("Food & drinks")
    d.tap("Save")
    time.sleep(3)
    # Earlier runs' exports would make the picker save as "burrow-… (1).csv".
    d.shell("rm -f /sdcard/Documents/burrow-2*.csv /sdcard/Download/burrow-2*.csv")
    s.open_more_row("Import or export")
    d.scroll_to("Export")
    d.tap("Export")
    time.sleep(3)
    d.tap("SAVE")
    time.sleep(3)
    # The picker opens in whichever folder was used last.
    path = d.shell("ls -t /sdcard/Documents/burrow-2*.csv /sdcard/Download/burrow-2*.csv 2>/dev/null").split("\n")[0].strip()
    assert path, "no file saved"
    name = path.rsplit("/", 1)[1]
    exported = d.shell(f"cat '{path}'")
    rows = [l for l in exported.splitlines()[1:] if l.strip()]
    assert rows, "the export is empty"
    assert any("333" in r for r in rows), "the new expense isn't in the export"
    wipe_and_onboard()
    s.finish_welcome()
    s.open_more_row("Import or export")
    d.scroll_to("Any spreadsheet (CSV)")
    d.tap("Any spreadsheet (CSV)")
    s.pick_file(name)
    button = f"Import {len(rows)} transaction" + ("s" if len(rows) > 1 else "")
    assert d.exists(button, timeout=8), f"Check before import doesn't offer {button!r}: {labels()}"
    d.tap(button)
    time.sleep(3)
    s.recover()
    d.nav("Activity")
    # The summary counts the payments behind "spent" (QA 12 Nov: it said transactions).
    assert d.exists(f"{len(rows)} payment", contains=True, timeout=5), "the imported rows aren't all in Activity"


def check_budget():
    # The welcome's ₹30,000 monthly budget: 24,000 crosses 80%, 6,000 more crosses 100%, then quiet.
    pay("24,000", "croma@okhdfc")
    nodes = notifications()
    assert s.has("Your budget is at 80%", nodes), "no 80% alert"
    collapse()
    pay("6,000", "croma@okhdfc")
    nodes = notifications()
    assert s.has("Your budget is used up", nodes), "no 100% alert"
    over = [n.label for n in nodes if "over · of" in n.label]
    collapse()
    pay("500", "croma@okhdfc")
    nodes = notifications()
    assert [n.label for n in nodes if "over · of" in n.label] == over, "the 100% alert came again"
    collapse()


def check_bill():
    s.recover()
    d.nav("Plan")
    d.tap("Coming up")
    d.tap("Add bill")
    d.tap("Netflix, rent, electricity…")
    d.type_text("BESCOM")
    hide_keyboard()
    s.tap_amount_field()
    s.keypad("1100")
    d.tap("Done")
    d.tap("Every month on", contains=True)
    d.tap("Delete")
    for k in str(datetime.now().day):
        d.tap(k)
    s.tap_last("Done")
    s.tap_last("Save")
    assert d.exists("Today · HDFC", timeout=5), "the bill isn't due today"
    pay("1,100", "bescom@okicici")
    s.recover()
    d.nav("Plan")
    d.tap("Coming up")
    assert not d.exists("Today · HDFC", timeout=3), "the SMS didn't mark today's BESCOM bill paid"


def check_words():
    sms("Rs.450.00 debited from A/c XX1234 to VPA apollopharmacy@okicici. UPI Ref 7711. Medicines")
    nodes = notifications()
    assert s.has("Medicines · ", nodes), "the notification doesn't show the expense name"
    pills = [n for n in nodes if n.label in ("Health", "Food & drinks", "Shopping", "Groceries", "Transport")]
    assert pills and pills[0].label == "Health", f"Health isn't the first pill: {[p.label for p in pills]}"
    s.tap_node(pills[0])
    time.sleep(3)
    assert in_activity("Medicines"), "the expense isn't named Medicines"
    d.tap("Medicines")
    assert d.exists("Merchant", timeout=3) and d.exists("apollopharmacy@okicici"), "no Merchant row"
    # Rename it; the next payment to that merchant takes the new name (its alias).
    d.tap("Edit")
    d.tap("Clear")  # ✕ empties Paid to and closes the keyboard: tap the field to type
    d.tap("Paid to")
    d.type_text("Apollo Pharmacy")
    hide_keyboard()
    s.tap_last("Save")
    time.sleep(5)
    sms("Rs.120.00 debited from A/c XX1234 to VPA apollopharmacy@okicici. UPI Ref 7712.")
    nodes = notifications()
    collapse()
    assert s.has("Apollo Pharmacy · ", nodes), "the alias isn't used for the next payment"


def check_restricted():
    # Package installer from the Files app: the same "local file" install a downloaded APK gets.
    apk = s.APK
    d.adb("push", apk, "/sdcard/Download/burrow.apk")
    d.shell(f"pm uninstall {d.PACKAGE}")
    # Open it in Files like a person would: an intent from adb can't hand the installer the file.
    d.shell("am start -a android.intent.action.VIEW -t vnd.android.document/root "
            "-d content://com.android.externalstorage.documents/root/primary")
    if not d.exists("burrow.apk", timeout=4):  # Files may reopen in Download already
        d.tap("Download", timeout=6)
    d.tap("burrow.apk", timeout=6)
    for label in ("Settings", "SETTINGS"):  # first time: allow Files to install apps
        if d.exists(label, timeout=2):
            d.tap(label)
            d.tap("Allow from this source", timeout=4)
            d.back()
    d.tap("INSTALL" if d.exists("INSTALL", timeout=6) else "Install")
    time.sleep(8)
    out = d.shell(f"dumpsys package {d.PACKAGE}")
    assert "packageSource=" in out or "installerPackageName" in out, "not installed"
    d.shell(f"pm grant {d.PACKAGE} android.permission.POST_NOTIFICATIONS")
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(3)
    s.finish_welcome()
    s.open_more_row("Auto-capture")
    # Bank SMS's switch: Android refuses SMS to an app installed from a file ("App was denied access
    # to SMS"), and Burrow then shows its note.
    switch = [n for n in d.dump() if n.checkable][0]
    s.tap_node(switch)
    assert d.exists("App was denied access to SMS", timeout=5), f"Android didn't restrict SMS: {labels()}"
    d.tap("OK")
    time.sleep(1)
    assert d.exists("Android is blocking access", timeout=5), f"no restricted settings note: {labels()}"


def check_rtl():
    # ar-XB, the right-to-left pseudolocale (debug builds have pseudolocales on): English text drawn
    # reverse, wrapped in bidi marks, and the whole layout mirrored. A real language with no Arabic
    # translation stays left-to-right (Android falls back to English, direction and all).
    d.shell(f"cmd locale set-app-locales {d.PACKAGE} --locales ar-XB")
    d.shell(f"am force-stop {d.PACKAGE}")
    d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
    time.sleep(3)

    def plain(label):
        return re.sub("[\u200e\u200f\u202a-\u202e\u2066-\u2069]", "", label)

    try:
        w = d.screen_size()[0]
        for tab in ("Today", "Activity", "Plan", "Insights", "More"):
            nodes = d.dump()
            nav = [n for n in nodes if plain(n.label) == tab]
            assert nav, f"no {tab} tab in RTL: {[n.label for n in nodes][:12]}"
            s.tap_node(nav[-1])  # the nav bar's, lowest on screen
            time.sleep(1.5)
            title = next((n for n in d.dump() if plain(n.label) == tab and n.bounds[1] < 400), None)
            assert title, f"{tab}: no title"
            assert title.bounds[2] > w * 0.85, f"{tab}: the title doesn't reach the right edge in RTL ({title.bounds})"
            crash = s.crashed()
            assert not crash, f"{tab} crashed in RTL: {crash}"
    finally:
        d.shell(f"cmd locale set-app-locales {d.PACKAGE} --locales en-US")
        d.shell(f"am force-stop {d.PACKAGE}")
        d.shell(f"am start -W -n {d.PACKAGE}/app.burrow.money.MainActivity")
        time.sleep(3)


def check_perf():
    n = 20_000
    rnd = random.Random(7)
    payees = ["Swiggy", "Uber", "BigBasket", "Amazon", "Apollo Pharmacy", "Indian Oil", "Netflix", "Chaayos", "Zepto", "BESCOM"]
    cats = ["Food & drinks", "Transport", "Groceries", "Shopping", "Health", "Transport", "Entertainment", "Food & drinks", "Groceries", "Bills & utilities"]
    start = datetime.now() - timedelta(days=730)
    lines = ["date,type,payee,amount,currency,category,account,note"]
    for i in range(n):
        k = rnd.randrange(len(payees))
        at = start + timedelta(minutes=rnd.randrange(730 * 24 * 60))
        lines.append(f"{at:%Y-%m-%d %H:%M},EXPENSE,{payees[k]},{rnd.randrange(20, 3000)}.00,INR,{cats[k]},HDFC Savings,")
    s.push_file("burrow-20k.csv", "\n".join(lines) + "\n")
    s.open_more_row("Import or export")
    d.scroll_to("Any spreadsheet (CSV)")
    d.tap("Any spreadsheet (CSV)")
    s.pick_file("burrow-20k.csv")
    t0 = time.time()
    d.tap(f"Import {n:,} transactions", timeout=60)
    s.recover()
    print(f"      import of {n:,}: {time.time() - t0:.0f} s", flush=True)
    w, h = d.screen_size()

    def jank(tab, swipes):
        # The emulator draws in software (swangle), so frame times only mean something next to a
        # baseline: the plain More list, which holds no data at all.
        d.nav(tab)
        time.sleep(3)
        d.shell(f"dumpsys gfxinfo {d.PACKAGE} reset")
        for _ in range(swipes):
            d.swipe(w // 2, int(h * 0.8), w // 2, int(h * 0.25), 150)
            d.swipe(w // 2, int(h * 0.25), w // 2, int(h * 0.8), 150)
        m = re.search(r"Janky frames: (\d+) \(([\d.]+)%\)", d.shell(f"dumpsys gfxinfo {d.PACKAGE}"))
        return float(m.group(2)) if m else None

    base = jank("More", 6)
    activity = jank("Activity", 8)
    print(f"      Activity scroll: janky {activity}% (the empty More list: {base}%)", flush=True)
    # uiautomator takes ~2.5 s a dump on this emulator: time it and leave it out.
    t0 = time.time()
    d.dump()
    one_dump = time.time() - t0
    d.nav("Today")
    time.sleep(2)
    t0 = time.time()
    d.nav("Insights")
    ready = d.exists("By category", timeout=30)
    print(f"      Insights ready in {max(0.0, time.time() - t0 - 2 * one_dump):.1f} s (dump time left out)", flush=True)
    assert ready, "Insights didn't show categories with 20k transactions"
    assert activity is None or base is None or activity <= base + 10, "Activity janks more than an empty list"


CHECKS = {
    "words": check_words, "budget": check_budget, "bill": check_bill, "backup": check_backup,
    "csv": check_csv, "perf": check_perf, "rtl": check_rtl, "restricted": check_restricted,
}


def main(names):
    picked = [k for k in CHECKS if not names or k in names]
    s.fresh_install()
    if d.exists("Get started", timeout=3):
        s.finish_welcome()
    s.capture_intro_turns_on()
    failures = 0
    for k in picked:
        d.adb("logcat", "-b", "crash", "-c")
        try:
            CHECKS[k]()
            print(f"PASS  {k}", flush=True)
        except Exception as e:  # noqa: BLE001
            failures += 1
            print(f"FAIL  {k}: {e}", flush=True)
            # The screen at the failure, for a look: build/phone_checks/<check>.png
            os.makedirs("build/phone_checks", exist_ok=True)
            with open(f"build/phone_checks/{k}.png", "wb") as f:
                f.write(subprocess.run([d.ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
            s.recover()
    print(f"\n{len(picked) - failures}/{len(picked)} passed")
    return failures


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
