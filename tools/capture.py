"""Captures the raw app screenshots on a connected emulator (1080 x 2400, as Weft's capture.py), then frames
the store shots with tools/store_shots.py.

  python3 tools/capture.py              # every shot
  python3 tools/capture.py drive        # only the shots whose name contains these words

Raw shots go to build/shots/ (store_shots.py's names: today, capture, review, activity, bills, insights,
rules, today-dark); the README's extra shots (category search, Backup › Google Drive) go to docs/screenshots/.
Then: release/phone-screenshots/ and fastlane's phoneScreenshots/1–8.png are rebuilt from the raw shots.
Light only, except the one dark Today the store asks for. Installs a fresh foss debug build (sample data);
the Drive shot installs the play debug build over it. Build both first:
  ./gradlew :app:assembleFossDebug :app:assemblePlayDebug
"""
import os
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import device as d  # noqa: E402
import smoke  # noqa: E402  (its helpers: fresh_install, finish_welcome, recover, open_add, open_more_row, tap_node)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RAW = os.path.join(ROOT, "build", "shots")
DOCS = os.path.join(ROOT, "docs", "screenshots")
FASTLANE = os.path.join(ROOT, "fastlane", "metadata", "android", "en-US", "images", "phoneScreenshots")
PLAY_APK = os.path.join(ROOT, "app/build/outputs/apk/play/debug/app-play-debug.apk")


def shot(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    time.sleep(1.2)  # animations settle (count-up, sheet slide)
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout)
    print("saved", os.path.relpath(path, ROOT))


def demo_bar(on=True):
    """A clean status bar: 9:30, full battery and signal, no notification icons."""
    b = "am broadcast -a com.android.systemui.demo -e command"
    if not on:
        d.shell(f"{b} exit")
        return
    d.shell("settings put global sysui_demo_allowed 1")
    d.shell(f"{b} enter")
    d.shell(f"{b} clock -e hhmm 0930")
    d.shell(f"{b} battery -e level 100 -e plugged false")
    d.shell(f"{b} network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4")
    d.shell(f"{b} notifications -e visible false")


def capture_on():
    """Auto-capture on, through the intro the way a person turns it on (smoke's capture_intro_turns_on)."""
    smoke.capture_intro_turns_on()
    smoke.recover()


def sms(body, sender="HDFCBK"):
    d.adb("emu", "sms", "send", sender, body)
    time.sleep(3)


def today():
    smoke.recover()
    d.nav("Today")
    d.scroll_to_top()
    shot(os.path.join(RAW, "today.png"))


def capture():
    sms("Rs.240.00 debited from A/c XX1234 to VPA thirdwave@okicici. UPI Ref 4821.")
    d.nav("Today")
    d.shell("cmd statusbar expand-notifications")
    time.sleep(1.5)
    shot(os.path.join(RAW, "capture.png"))
    d.shell("cmd statusbar collapse")


def review():
    sms("Rs.180.00 debited from A/c XX1234 on 20-10-26 to VPA chaayos.paytm@hdfc. UPI Ref 629184001.")
    smoke.recover()
    d.nav("Today")
    d.tap("payments to review", contains=True)
    shot(os.path.join(RAW, "review.png"))
    smoke.recover()


def activity():
    smoke.recover()
    d.nav("Activity")
    shot(os.path.join(RAW, "activity.png"))


def bills():
    smoke.recover()
    d.nav("Plan")
    shot(os.path.join(RAW, "bills.png"))


def insights():
    smoke.recover()
    d.nav("Insights")
    shot(os.path.join(RAW, "insights.png"))


def rules():
    sms("INR 1,590.00 spent on ICICI Bank Card XX9012 at DMART on 24-Oct-26. Avl Lmt INR 48,410.", sender="ICICIB")
    smoke.recover()
    d.nav("More")
    d.tap("Auto-capture")
    if d.exists("not understood", contains=True, timeout=4):
        d.tap("not understood", contains=True)
        d.tap("Teach")
        shot(os.path.join(RAW, "rules.png"))
    else:
        print("skip rules: the message was read by a rule, nothing to teach")
    smoke.recover()


def today_dark():
    d.shell("cmd uimode night yes")
    time.sleep(2)
    try:
        today_path = os.path.join(RAW, "today-dark.png")
        smoke.recover()
        d.nav("Today")
        shot(today_path)
    finally:
        d.shell("cmd uimode night no")
        time.sleep(1)


def category_search():
    smoke.recover()
    smoke.open_add()
    chip = next(n for n in d.dump() if n.label == "Food & drinks")
    w = d.screen_size()[0]
    for _ in range(3):
        if d.exists("More", timeout=0.5):
            break
        d.drag(int(w * 0.85), chip.center[1], int(w * 0.15), chip.center[1])
    d.tap("More")
    smoke.tap_node(next(n for n in d.dump() if n.cls.endswith("EditText")))
    d.type_text("tr")
    d.back()  # keyboard away
    shot(os.path.join(DOCS, "category-search.png"))
    smoke.recover()


def drive():
    out = d.adb("install", "-r", PLAY_APK)
    assert "Success" in out, f"play install failed: {out}"
    d.shell(f"am start -W -n {d.PACKAGE}/.MainActivity")
    time.sleep(3)
    demo_bar()
    smoke.open_more_row("Backup")
    d.scroll_to("Connect Google Drive")
    shot(os.path.join(DOCS, "backup-drive.png"))
    smoke.recover()


SHOTS = [today, capture, review, activity, bills, insights, rules, today_dark, category_search, drive]


def frame():
    """Store shots from the raw ones: release/phone-screenshots/, then fastlane's 1–8.png."""
    subprocess.run([sys.executable, os.path.join(ROOT, "tools", "store_shots.py"), RAW], check=True)
    out = os.path.join(ROOT, "release", "phone-screenshots")
    for i, name in enumerate(sorted(os.listdir(out)), start=1):
        shutil.copy(os.path.join(out, name), os.path.join(FASTLANE, f"{i}.png"))
    # The README shows the store shots and these raw ones; keep one copy of each.
    print("framed", len(os.listdir(out)), "store shots")


def main(words):
    picked = [s for s in SHOTS if not words or any(w in s.__name__ for w in words)]
    smoke.fresh_install()
    smoke.finish_welcome()
    demo_bar()
    try:
        capture_on()
        for s in picked:
            s()
    finally:
        demo_bar(False)
    store = {"today", "capture", "review", "activity", "bills", "insights", "rules", "today-dark"}
    if all(os.path.exists(os.path.join(RAW, f"{n}.png")) for n in store):
        frame()


if __name__ == "__main__":
    main(sys.argv[1:])
