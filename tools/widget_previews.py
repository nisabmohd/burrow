"""Draws the home-screen widgets' picker previews (as Weft's widget_preview_*.webp): what each widget
looks like with sample data, light and dark, from the widgets' own colours (res/values*/widget.xml),
sizes (TOKENS / design 89), the Burrow mark (tools/burrow_mark.py) and the Solar icons they use.

Launchers that can't draw `previewLayout` (many OEM launchers, user 2026-11-09) fell back to the app
icon; `previewImage` always shows.

  app/src/main/res/drawable-nodpi/widget_preview_{safe,add,bill}.webp        light
  app/src/main/res/drawable-night-nodpi/widget_preview_{safe,add,bill}.webp  dark

usage: python3 tools/widget_previews.py   (headless Chrome, then Pillow for WebP)
"""
import os
import re
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import burrow_mark as bm  # noqa: E402

RES = os.path.join(bm.ROOT, "app", "src", "main", "res")
SCALE = 3  # px per dp: crisp on xxhdpi, the picker scales it down


def colours(night):
    path = os.path.join(RES, "values-night" if night else "values", "widget.xml")
    text = open(path, encoding="utf-8").read()
    out = {}
    for name, value in re.findall(r'<color name="widget_(\w+)">#([0-9A-Fa-f]{6,8})</color>', text):
        if len(value) == 8:  # #AARRGGBB → rgba()
            a, rgb = int(value[:2], 16) / 255, value[2:]
            out[name] = f"rgba({int(rgb[0:2], 16)},{int(rgb[2:4], 16)},{int(rgb[4:6], 16)},{a:.3f})"
        else:
            out[name] = "#" + value
    return out


def icon(name, colour, size):
    """A Solar vector drawable as inline SVG, stroked in [colour]."""
    text = open(os.path.join(RES, "drawable", f"{name}.xml"), encoding="utf-8").read()
    paths = "".join(
        f'<path d="{d}" fill="none" stroke="{colour}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>'
        for w, d in re.findall(r'android:strokeWidth="([\d.]+)"[^>]*?android:pathData="([^"]+)"', text))
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24">{paths}</svg>'


def mark(colour, size):
    s, t = bm.group(512, 512 * 0.8)
    return f'<svg width="{size}" height="{size}" viewBox="0 0 512 512">{bm.mark_svg(colour, s, t, t)}</svg>'


CSS = """
* { box-sizing: border-box; margin: 0; }
.w { font-family: Lexend; font-variation-settings: "wght" 440; border-radius: 24px; padding: 16px; position: relative;
     width: var(--w); height: var(--h); overflow: hidden; }
.b { font-variation-settings: "wght" 540; }
.circle { border-radius: 50%; display: flex; align-items: center; justify-content: center; }
"""


def safe(c):
    w, h = 360, 164
    body = f'''
<div class="w" style="--w:{w}px;--h:{h}px;background:{c['hero']};color:{c['on_hero']};display:flex;align-items:center">
  <div style="flex:1;min-width:0">
    <div style="display:flex;align-items:center;gap:8px">
      <div class="circle" style="width:24px;height:24px;background:{c['primary']}">{mark(c['on_primary'], 16)}</div>
      <span style="font-size:14px">Safe to spend today</span>
    </div>
    <div class="b" style="font-size:44px;line-height:1.15;padding-top:4px">₹235</div>
    <div style="font-size:14px;color:{c['on_hero_muted']}">₹13.6k left in October · 13 days</div>
    <div style="height:8px;border-radius:4px;background:{c['track']};margin-top:12px;width:100%">
      <div style="height:8px;border-radius:4px;background:{c['primary']};width:55%"></div>
    </div>
  </div>
  <div class="circle" style="width:56px;height:56px;background:{c['primary']};margin-left:16px;flex:none">{icon('ic_add_regular', c['on_primary'], 28)}</div>
</div>'''
    return body, w, h


def add(c):
    w, h = 172, 122
    body = f'''
<div class="w" style="--w:{w}px;--h:{h}px;background:{c['surface']};color:{c['text']}">
  <div class="circle" style="width:40px;height:40px;background:{c['primary']}">{icon('ic_add_regular', c['on_primary'], 22)}</div>
  <div class="b" style="position:absolute;left:16px;bottom:16px;font-size:16px">Add expense</div>
</div>'''
    return body, w, h


def bill(c):
    w, h = 172, 122
    body = f'''
<div class="w" style="--w:{w}px;--h:{h}px;background:{c['surface']};color:{c['text']}">
  <div class="circle" style="width:40px;height:40px;background:{c['circle']}">{icon('ic_event_available_duotone', c['text_muted'], 22)}</div>
  <div style="position:absolute;left:16px;bottom:16px">
    <div class="b" style="font-size:16px">Netflix ₹649</div>
    <div style="font-size:14px;color:{c['text_muted']}">Due today</div>
  </div>
</div>'''
    return body, w, h


def main():
    from PIL import Image
    for night in (False, True):
        c = colours(night)
        folder = os.path.join(RES, "drawable-night-nodpi" if night else "drawable-nodpi")
        os.makedirs(folder, exist_ok=True)
        for name, draw in (("safe", safe), ("add", add), ("bill", bill)):
            body, w, h = draw(c)
            html = bm.page(f"<style>{CSS}</style>{body}", w, h).replace(
                "--force-device-scale-factor=1", "")
            with tempfile.TemporaryDirectory() as tmp:
                png = os.path.join(tmp, "w.png")
                render_scaled(html, w, h, png)
                out = os.path.join(folder, f"widget_preview_{name}.webp")
                Image.open(png).save(out, "WEBP", lossless=True)
                print(os.path.relpath(out, bm.ROOT))


def render_scaled(html, width, height, out):
    """burrow_mark.render at [SCALE]× with a transparent background (the rounded corners)."""
    import shutil
    import subprocess
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "page.html")
        open(page, "w", encoding="utf-8").write(html)
        png = os.path.join(tmp, "shot.png")
        subprocess.run([bm.CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        f"--force-device-scale-factor={SCALE}", f"--window-size={width},{height}",
                        "--default-background-color=00000000", f"--screenshot={png}", "file://" + page],
                       check=True, capture_output=True)
        shutil.copy(png, out)


if __name__ == "__main__":
    main()
