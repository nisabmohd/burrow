"""Builds the Play Store shots in release/phone-screenshots/ (1080 x 1920): a caption and a phone frame
around a raw 1080 x 2400 emulator screenshot. The phone sits under the tallest caption with 72 px
left below it, so the navigation bar and the bezel are never cut. The status bar is pulled in from
the edges so the frame's rounded corners never cut the clock or the battery.

usage: python3 tools/store_shots.py <folder with the raw shots> [out folder]
Raw shots are named today.png, capture.png, review.png, activity.png, bills.png, insights.png,
rules.png and today-dark.png.
Needs Pillow."""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "release", "phone-screenshots")
os.makedirs(OUT, exist_ok=True)
FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "lexend.ttf")

shots = [
    ("today", "01-today.png", "Know what is left", "Your month at a glance, on one calm screen.", False),
    ("capture", "02-capture.png", "Payments record themselves", "A payment message comes in. One tap saves it.", False),
    ("review", "03-review.png", "Check and save in seconds", "Amount, payee and account are read for you.", False),
    ("activity", "04-activity.png", "Every payment, easy to find", "Search and filter by account, category or date.", False),
    ("bills", "05-bills.png", "Never miss a bill", "Reminders before every bill, subscription and EMI.", False),
    ("insights", "06-insights.png", "See your patterns", "Charts, busiest days and a month heatmap.", False),
    ("rules", "07-rules.png", "Works with your bank", "Teach it a new message by tapping the amount.", False),
    ("today-dark", "08-today-dark.png", "Private. Offline. Yours.", "No internet permission. Nothing leaves your phone.", True),
]

W, H = 1080, 1920
TOP, GAP, BOTTOM = 150, 56, 72                        # caption top; caption → phone; phone → edge
BEZEL, OUTER_R, INNER_R = 14, 62, 48
RAW_W, RAW_H = 1080, 2400                             # emulator screenshot size
TITLE_SIZE, TITLE_LINE, SUB_SIZE, SUB_LINE = 68, 82, 36, 48


def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([weight])
    return f


def wrap(draw, text, f, width):
    lines, cur = [], ""
    for word in text.split():
        t = (cur + " " + word).strip()
        if draw.textlength(t, font=f) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = word
    return lines + [cur]


def screen(raw, sw, sh):
    """The raw shot at screen size, status bar pulled in from the corners so they never cut it."""
    s = Image.open(raw).convert("RGB")
    k = sw / RAW_W
    out = s.resize((sw, sh), Image.LANCZOS)
    bar = round(110 * k)                                   # status bar, tall enough for the clock
    clock = s.crop((0, 0, 360, 110)).resize((round(360 * k), bar), Image.LANCZOS)
    icons = s.crop((720, 0, RAW_W, 110)).resize((round(360 * k), bar), Image.LANCZOS)
    ImageDraw.Draw(out).rectangle((0, 0, sw, bar), fill=s.getpixel((540, 8)))
    inset, drop = 14, 8
    out.paste(clock, (inset, drop))
    out.paste(icons, (sw - icons.width - inset, drop))
    return out


def caption(d, title, sub, tf, sf, tc, sc, dry=False):
    """Draws the title and line; returns the y under them."""
    y = TOP
    for line in wrap(d, title, tf, 960):
        if not dry:
            d.text((W / 2, y), line, font=tf, fill=tc, anchor="mt")
        y += TITLE_LINE
    y += 12
    for line in wrap(d, sub, sf, 920):
        if not dry:
            d.text((W / 2, y), line, font=sf, fill=sc, anchor="mt")
        y += SUB_LINE
    return y


tf, sf = font(TITLE_SIZE, 540), font(SUB_SIZE, 400)   # Lexend drawn a step heavier, as in the app
probe = ImageDraw.Draw(Image.new("RGB", (W, H)))
# One phone size for every shot: under the tallest caption, with room below for the shadow.
FY = max(caption(probe, t, s, tf, sf, None, None, dry=True) for _, _, t, s, _ in shots) + GAP
FH = H - BOTTOM - FY
SH = FH - 2 * BEZEL
SW = round(SH * RAW_W / RAW_H)
FW = SW + 2 * BEZEL
FX = (W - FW) // 2
SX, SY = FX + BEZEL, FY + BEZEL

for raw, out, title, sub, dark in shots:
    bg = (34, 24, 28) if dark else (250, 226, 233)   # Burrow rose, from the 345 seed
    tc = (245, 233, 237) if dark else (46, 18, 28)
    sc = (196, 176, 183) if dark else (112, 76, 88)
    img = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(img)
    caption(d, title, sub, tf, sf, tc, sc)

    # soft shadow under the phone
    sh = Image.new("L", (W, H), 0)
    ImageDraw.Draw(sh).rounded_rectangle((FX, FY + 12, FX + FW, FY + FH + 12), OUTER_R, fill=90 if not dark else 160)
    sh = sh.filter(ImageFilter.GaussianBlur(28))
    img.paste((0, 0, 0) if dark else (120, 60, 80), (0, 0), sh)

    d = ImageDraw.Draw(img)
    d.rounded_rectangle((FX, FY, FX + FW - 1, FY + FH - 1), OUTER_R, fill=(58, 54, 50) if dark else (22, 20, 18))
    if dark:  # a thin light rim so the dark phone reads on the dark page
        d.rounded_rectangle((FX, FY, FX + FW - 1, FY + FH - 1), OUTER_R, outline=(74, 60, 66), width=2)
        d.rounded_rectangle((FX + 2, FY + 2, FX + FW - 3, FY + FH - 3), OUTER_R - 2, fill=(14, 13, 12))

    scr = screen(f"{RAW}/{raw}.png", SW, SH)
    k = 4
    m = Image.new("L", (SW * k, SH * k), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, SW * k - 1, SH * k - 1), INNER_R * k, fill=255)
    img.paste(scr, (SX, SY), m.resize((SW, SH), Image.LANCZOS))
    img.save(f"{OUT}/{out}", optimize=True)
    print(out)
print(f"phone {FW} x {FH} at y {FY}, {H - FY - FH} px below")
