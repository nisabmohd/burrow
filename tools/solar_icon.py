"""Converts Solar icons (480 Design, CC BY 4.0) into Burrow's vector drawables (docs/ICONS.md).

Each meaning gets ic_<name>_regular.xml, ic_<name>_fill.xml and ic_<name>_duotone.xml, all the same Linear
(outline) icon: the weight names stay so the code doesn't change, and a selected or tinted state comes from the
container (pill, circle), not the glyph. Viewport 24, white strokes (tint it).

usage: python3 tools/solar_icon.py <path to @iconify-json/solar package> [meaning ...]
e.g.   npm pack @iconify-json/solar && tar xzf iconify-json-solar-*.tgz
       python3 tools/solar_icon.py package                 # every icon in ICONS below
       python3 tools/solar_icon.py package hand_tap        # one
A new icon: add "meaning": "solar-name" (without the style suffix; it must exist in both styles) to ICONS, run it for that meaning.
Only weights that already exist are rewritten when regenerating everything; a new meaning gets all three.
"""
import json, os, sys
import xml.etree.ElementTree as ET

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "drawable")
WEIGHTS = ("regular", "fill", "duotone")
# One style everywhere: Linear (outline, 1.5 strokes; user 2026-10-31: no duotones, they looked inconsistent).
STYLES = {"regular": "linear", "fill": "linear", "duotone": "linear"}

# meaning (as used in R.drawable.ic_<meaning>_<weight>) → Solar name (without the style suffix)
ICONS = {
    "account_balance": "safe-square", "account_switch": "user-rounded", "cloud_download": "cloud-download", "logout": "logout-2", "account_balance_wallet": "wallet", "add": "add", "alarm": "alarm",
    "apartment": "buildings", "arrow_back": "arrow-left", "arrow_downward": "arrow-down",
    "arrow_drop_down": "alt-arrow-down", "arrow_forward": "arrow-right", "arrow_upward": "arrow-up",
    "arrow_upward_alt": "arrow-up", "attach_file": "paperclip", "attach_money": "dollar",
    "autorenew": "refresh", "backspace": "backspace", "backup": "cloud-upload", "balance": "scale",
    "bar_chart": "chart-2", "battery_full": "battery-full", "block": "forbidden-circle", "bolt": "bolt",
    "books": "book-2", "bug_report": "bug", "bus": "bus", "calendar_month": "calendar",
    "calendar_view_day": "calendar-minimalistic", "calendar_view_month": "calendar-date",
    "call_split": "branching-paths-down", "category": "widget", "celebration": "confetti", "check": "check",
    "check_circle": "check-circle", "checkroom": "t-shirt", "chevron_left": "alt-arrow-left",
    "chevron_right": "alt-arrow-right", "child_care": "balloon", "close": "close", "cloud": "cloud",
    "cloud_done": "cloud-check", "cloud_upload": "cloud-upload", "code": "code", "coffee": "cup-hot",
    "content_copy": "copy", "content_paste": "clipboard-text", "cooking_gas": "fire", "credit_card": "card",
    "currency_exchange": "round-transfer-horizontal", "currency_pound": "banknote-2", "currency_rupee": "banknote",
    "dark_mode": "moon", "dashboard_customize": "widget-add", "date": "calendar-minimalistic",
    "date_range": "calendar-date", "delete": "trash-bin-minimalistic", "delete_forever": "trash-bin-trash",
    "delivery": "delivery", "description": "document-text", "directions_car": "car", "doctor": "stethoscope",
    "donut_large": "pie-chart-3", "download": "download-minimalistic", "drag_indicator": "reorder",
    "edit": "pen-2", "edit_note": "pen-new-square", "envelope": "letter", "error": "danger-circle", "euro": "euro",
    "event": "calendar-mark", "event_available": "calendar-mark", "event_busy": "calendar",
    "event_repeat": "repeat", "event_upcoming": "calendar-date", "expand_less": "alt-arrow-up",
    "expand_more": "alt-arrow-down", "export": "export", "fastfood": "donut-bitten", "favorite": "heart",
    "filter_alt_off": "filter", "filter_list": "filter", "fingerprint": "shield-keyhole",
    "fitness_center": "dumbbell-large-minimalistic", "flag": "flag", "flight": "plane", "folder": "folder",
    "format_list_bulleted": "list", "gadgets": "headphones-round", "games": "gamepad",
    "gold": "medal-ribbons-star", "grid_view": "widget", "group": "users-group-rounded", "hand_tap": "cursor",
    "handshake": "hand-shake", "help": "question-circle", "history": "history", "home": "home-2",
    "hospital": "medical-kit", "hotel": "bed", "house_help": "broom", "image": "gallery", "import": "import",
    "info": "info-circle", "insights": "chart-square", "insurance": "umbrella", "investment": "graph-new-up",
    "ios_share": "share", "jewellery": "crown-star", "key": "key", "laptop_mac": "laptop", "light_mode": "sun",
    "lightbulb": "lightbulb", "loan_emi": "hand-money", "local_bar": "wineglass",
    "local_gas_station": "gas-station", "local_mall": "bag", "lock": "lock-keyhole", "medicine": "pill",
    "more_horiz": "menu-dots", "movie": "clapperboard-play", "notes": "notes", "notifications": "bell",
    "notifications_active": "bell-ring", "notifications_off": "bell-off", "notifications_paused": "alarm-sleep",
    "palette": "palette", "pause": "pause", "pause_circle": "pause-circle", "pause_circle_filled": "pause-circle",
    "payments": "wallet-money", "percent": "sale", "person": "user", "pets": "paw", "photo_camera": "camera",
    "pie": "pie-chart-2", "pie_chart": "pie-chart-2", "pin": "password", "plants": "leaf", "play_arrow": "play",
    "public": "global", "real_estate_agent": "home-angle", "receipt_long": "bill-list", "redeem": "gift",
    "redeem2": "ticket-sale", "redo": "undo-right", "refund": "undo-left-round", "remove": "minus",
    "repairs": "sledgehammer", "restaurant": "chef-hat-minimalistic", "rule": "checklist", "salon": "scissors",
    "savings": "money-bag", "scales": "scale", "schedule": "clock-circle", "school": "square-academic-cap",
    "science": "test-tube", "search": "magnifier", "search_off": "magnifier-slash",
    "settings_backup_restore": "restart", "shield": "shield", "shield_check": "shield-check",
    "shoes": "running-2", "shopping_cart": "cart-large-2", "signal_cellular_4_bar": "sim-card",
    "smartphone": "smartphone", "sms": "chat-round-dots", "spa": "meditation-round", "star": "star",
    "storefront": "shop", "summarize": "clipboard-list", "swap_horiz": "transfer-horizontal",
    "swap_vert": "transfer-vertical", "table": "table", "table_view": "table", "tag": "tag-horizontal",
    "task_alt": "check-circle", "tax": "calculator", "taxi": "streets-navigation", "text_fields": "text-field", "tickets": "ticket",
    "timer": "stopwatch", "today": "calendar", "toys": "balls", "train": "tram", "translate": "translation",
    "travel": "suitcase", "trending_down": "graph-down", "trending_up": "graph-up", "tune": "tuning-2",
    "tv": "tv", "two_wheeler": "scooter", "undo": "undo-left", "upload_file": "upload",
    "vegetables": "cart-large-4", "visibility_off": "eye-closed", "volunteer_activism": "hand-heart",
    "wallet": "wallet", "wallpaper": "wallpaper", "water": "waterdrop", "wifi": "wi-fi-router-round",
    "wifi_off": "wi-fi-router-minimalistic",
}

# A different glyph for the regular weight, when one is ever needed.
LINE = {}


def load(pkg):
    return json.load(open(os.path.join(os.path.expanduser(pkg), "icons.json")))["icons"]


def f(v):
    return f"{float(v):g}"


def path_data(kind, a):
    if kind == "path":
        return a["d"]
    if kind in ("circle", "ellipse"):
        cx, cy = float(a["cx"]), float(a["cy"])
        rx = float(a.get("r", a.get("rx")))
        ry = float(a.get("r", a.get("ry")))
        return (f"M{f(cx - rx)},{f(cy)}a{f(rx)},{f(ry)} 0 1,0 {f(2 * rx)},0"
                f"a{f(rx)},{f(ry)} 0 1,0 {f(-2 * rx)},0Z")
    if kind == "rect":
        x, y, w, h = (float(a.get(k, 0)) for k in ("x", "y", "width", "height"))
        r = min(float(a.get("rx", a.get("ry", 0))), w / 2, h / 2)
        if not r:
            return f"M{f(x)},{f(y)}h{f(w)}v{f(h)}h{f(-w)}Z"
        return (f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)}v{f(h - 2 * r)}"
                f"a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(r)}h{f(-(w - 2 * r))}a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(-r)}"
                f"v{f(-(h - 2 * r))}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(-r)}Z")
    raise ValueError(f"unknown element {kind}")


INHERITED = ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-rule")


def shapes(body):
    """(pathData, paint) for every shape; <g> passes paint, opacity and fill-rule down to its children."""
    out = []

    def walk(el, alpha, paint):
        a = el.attrib
        if "transform" in a or "clip-path" in a or "mask" in a or "stroke-dasharray" in a:
            raise ValueError(f"unsupported attribute on <{el.tag}>")
        alpha *= float(a.get("opacity", 1))
        paint = {**paint, **{k: a[k] for k in INHERITED if k in a}}
        if el.tag == "g":
            for c in el:
                walk(c, alpha, paint)
        elif el.tag in ("path", "circle", "ellipse", "rect"):
            out.append((path_data(el.tag, a), {**paint, "alpha": alpha}))
        elif el.tag != "defs":
            raise ValueError(f"unknown element <{el.tag}>")

    for el in ET.fromstring(f"<svg>{body}</svg>"):
        walk(el, 1.0, {"fill": "currentColor"})
    return out


def drawable(name, weight, solar, els):
    style = STYLES[weight].replace("-", " ").title()
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- Solar {solar} ({style}) by 480 Design, CC BY 4.0. Tint it: Icon(painterResource(R.drawable.ic_{name}_{weight}), tint = …) -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">',
    ]
    for d, p in els:
        attrs = []
        alpha = p["alpha"]
        if p.get("fill", "none") != "none":
            attrs.append('android:fillColor="@android:color/white"')
            if p.get("fill-rule") == "evenodd":
                attrs.append('android:fillType="evenOdd"')
            if alpha != 1:
                attrs.append(f'android:fillAlpha="{f(alpha)}"')
        if p.get("stroke", "none") != "none":
            attrs.append('android:strokeColor="@android:color/white"')
            attrs.append(f'android:strokeWidth="{p.get("stroke-width", "1")}"')
            if p.get("stroke-linecap") in ("round", "square"):
                attrs.append(f'android:strokeLineCap="{p["stroke-linecap"]}"')
            if p.get("stroke-linejoin") in ("round", "bevel"):
                attrs.append(f'android:strokeLineJoin="{p["stroke-linejoin"]}"')
            if alpha != 1:
                attrs.append(f'android:strokeAlpha="{f(alpha)}"')
        if attrs:
            lines.append(f'    <path {" ".join(attrs)} android:pathData="{d}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main(pkg, names):
    icons = load(pkg)
    everything = not names
    problems = []
    for name in names or sorted(ICONS):
        existing = [w for w in WEIGHTS if os.path.exists(os.path.join(OUT, f"ic_{name}_{w}.xml"))]
        for weight in (existing if everything and existing else WEIGHTS):
            solar = LINE.get(name, ICONS[name]) if weight == "regular" else ICONS[name]
            key = f"{solar}-{STYLES[weight]}"
            try:
                if key not in icons:
                    raise ValueError("not in the set")
                els = shapes(icons[key]["body"])
            except ValueError as e:
                problems.append(f"{name}: {key}: {e}")
                continue
            open(os.path.join(OUT, f"ic_{name}_{weight}.xml"), "w").write(drawable(name, weight, solar, els))
    if problems:
        sys.exit("\n".join(problems))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2:])
