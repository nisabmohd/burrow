#!/usr/bin/env python3
"""End-to-end smoke check of every Burrow feature on a connected emulator or phone.

Installs a fresh debug build (which seeds the sample month on first launch), then runs one short
check per feature and prints PASS/FAIL. Exit code is the number of failures.

    ./gradlew :app:assemblePlayDebug
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
APK = os.path.join(ROOT, "app/build/outputs/apk/play/debug/app-play-debug.apk")

CHECKS = []


def check(fn):
    CHECKS.append(fn)
    return fn


def fresh_install():
    d.adb("uninstall", d.PACKAGE)
    out = d.adb("install", APK)
    assert "Success" in out, f"install failed: {out}"
    d.shell(f"pm grant {d.PACKAGE} android.permission.POST_NOTIFICATIONS")
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
    time.sleep(3)


# --- checks, in the order a new user meets the features -----------------------------------
# Burrow's checks. Add one per feature as you build it (find by visible text or label, never coordinates).


@check
def welcome_shows_once():
    assert d.exists("Get started"), "welcome screen missing on first launch"
    d.tap("Get started")
    if d.exists("Continue", timeout=3):
        d.tap("Continue")
    if d.exists("Done", timeout=3):
        d.tap("Done")
    assert d.exists("Today", timeout=10), "Today not shown after onboarding"
    d.shell(f"am force-stop {d.PACKAGE}")
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
    time.sleep(2.5)
    assert not d.exists("Get started", timeout=1.5), "welcome shown again after restart"


@check
def today_shows_budget_and_sample_note():
    assert d.exists("left of", contains=True, timeout=5), "budget hero missing"
    assert d.exists("These are sample transactions", contains=True), "sample-data note missing"


@check
def add_expense_saves_and_undo():
    d.tap("Add expense")
    for k in "240":
        d.tap(k)
    assert d.exists("Save"), "Save button missing"
    d.tap("Choose a category")
    d.tap("Food & drinks")
    d.tap("Save")
    assert d.exists("Undo", timeout=3), "no Undo snackbar after saving"


@check
def sms_capture_asks_for_category():
    d.adb("emu", "sms", "send", "HDFCBK", "Rs.240.00 debited from A/c XX1234 to VPA thirdwave@okicici. UPI Ref 4821.")
    time.sleep(3)
    d.shell("cmd statusbar expand-notifications")
    ok = d.exists("₹240", contains=True, timeout=5)
    d.shell("cmd statusbar collapse")
    assert ok, "no capture notification for a bank SMS"


@check
def activity_filter_badge():
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
        sys.exit(f"Build first: ./gradlew :app:assemblePlayDebug (missing {APK})")
    fresh_install()
    if welcome_shows_once not in selected and d.exists("Get started", timeout=3):
        d.tap("Get started")
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
