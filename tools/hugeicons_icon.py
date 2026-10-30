"""Converts Hugeicons (free set, stroke rounded, MIT) into Burrow's vector drawables (docs/ICONS.md).

Each meaning gets ic_<name>_regular.xml, ic_<name>_fill.xml and ic_<name>_duotone.xml. The free set has
one style only, so the three files are the same stroke icon: the weight names stay so the code doesn't
change, and a selected or tinted state comes from the container (pill, circle), not the glyph.
Paths only, viewport 24, white strokes (tint it).

usage: python3 tools/hugeicons_icon.py <path to @hugeicons/core-free-icons> [meaning ...]
e.g.   npm pack @hugeicons/core-free-icons && tar xzf hugeicons-core-free-icons-*.tgz
       python3 tools/hugeicons_icon.py package                 # every icon in ICONS below
       python3 tools/hugeicons_icon.py package hand_tap        # one
A new icon: add "meaning": "HugeiconsName" (without "Icon") to ICONS, run it for that meaning.
Only weights that already exist are rewritten when regenerating everything; a new meaning gets all three.
"""
import os, re, sys

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res", "drawable")
WEIGHTS = ("regular", "fill", "duotone")

# meaning (as used in R.drawable.ic_<meaning>_<weight>) → Hugeicons name
ICONS = {
    "account_balance": "Bank", "account_balance_wallet": "Wallet01", "add": "Add01", "alarm": "AlarmClock",
    "apartment": "Building03", "arrow_back": "ArrowLeft02", "arrow_downward": "ArrowDown02",
    "arrow_drop_down": "ArrowDown01", "arrow_forward": "ArrowRight02", "arrow_upward": "ArrowUp02",
    "arrow_upward_alt": "ArrowUp02", "attach_file": "Attachment01", "attach_money": "DollarCircle",
    "autorenew": "Refresh", "backspace": "ArrowLeft04", "backup": "CloudUpload", "balance": "BalanceScale",
    "bar_chart": "ChartColumn", "battery_full": "BatteryFull", "block": "Unavailable", "bolt": "Flash",
    "books": "Books01", "bug_report": "Bug01", "bus": "Bus01", "calendar_month": "Calendar03",
    "calendar_view_day": "Calendar01", "calendar_view_month": "GridView", "call_split": "Split",
    "category": "Shapes", "celebration": "Party", "check": "Tick02", "check_circle": "CheckmarkCircle02",
    "checkroom": "TShirt", "chevron_left": "ArrowLeft01", "chevron_right": "ArrowRight01",
    "child_care": "Baby01", "close": "Cancel01", "cloud": "Cloud", "cloud_done": "CloudSavingDone01",
    "cloud_upload": "CloudUpload", "code": "SourceCode", "coffee": "Coffee02", "content_copy": "Copy01",
    "content_paste": "Clipboard", "cooking_gas": "Fire", "credit_card": "CreditCard",
    "currency_exchange": "Exchange01", "currency_pound": "Pound", "currency_rupee": "Rupee",
    "dark_mode": "Moon02", "dashboard_customize": "DashboardSquare02", "date": "Calendar01",
    "date_range": "Calendar02", "delete": "Delete02", "delete_forever": "Delete01", "delivery": "Scooter02",
    "description": "File02", "directions_car": "Car01", "doctor": "Stethoscope", "donut_large": "ChartRing",
    "download": "Download04", "drag_indicator": "DragDropVertical", "edit": "PencilEdit02",
    "edit_note": "NoteEdit", "envelope": "Mail01", "error": "AlertCircle", "euro": "Euro",
    "event": "CalendarCheckIn01", "event_available": "CalendarCheckIn01", "event_busy": "CalendarRemove01",
    "event_repeat": "Repeat", "event_upcoming": "CalendarClock", "expand_less": "ArrowUp01",
    "expand_more": "ArrowDown01", "export": "Share05", "fastfood": "Hamburger01", "favorite": "Favourite",
    "filter_alt_off": "FilterRemove", "filter_list": "FilterHorizontal", "fingerprint": "FingerPrint",
    "fitness_center": "Dumbbell01", "flag": "Flag02", "flight": "Airplane01", "folder": "Folder01",
    "format_list_bulleted": "LeftToRightListBullet", "gadgets": "Headphones", "games": "GameController03",
    "gold": "GoldIngots", "grid_view": "GridView", "group": "UserGroup", "hand_tap": "Touch01",
    "handshake": "Agreement02", "help": "HelpCircle", "history": "History", "home": "Home01",
    "hospital": "FirstAidKit", "hotel": "BedDouble", "house_help": "Clean", "image": "Image01",
    "import": "InboxDownload", "info": "InformationCircle", "insights": "Sparkles", "insurance": "Umbrella",
    "investment": "ChartIncrease", "ios_share": "Share05", "jewellery": "Diamond01", "key": "Key01",
    "laptop_mac": "Laptop", "light_mode": "Sun03", "lightbulb": "Idea01", "loan_emi": "HandCoins",
    "local_bar": "Beer", "local_gas_station": "FuelStation", "local_mall": "ShoppingBag02",
    "lock": "SquareLock02", "medicine": "Medicine02", "more_horiz": "MoreHorizontal", "movie": "Film02",
    "notes": "Note01", "notifications": "Notification03", "notifications_active": "Notification01",
    "notifications_off": "NotificationOff03", "notifications_paused": "NotificationSnooze03",
    "palette": "PaintBoard", "pause": "Pause", "pause_circle": "PauseCircle",
    "pause_circle_filled": "PauseCircle", "payments": "Money03", "percent": "Percent", "person": "User",
    "pets": "PawPrint", "photo_camera": "Camera01", "pie": "PieChart", "pie_chart": "PieChart",
    "pin": "PasswordValidation", "plants": "Plant02", "play_arrow": "Play", "public": "Globe02",
    "real_estate_agent": "RealEstate02", "receipt_long": "Invoice01", "redeem": "Gift",
    "redeem2": "GiftCard", "redo": "Redo03", "refund": "ArrowTurnBackward", "remove": "MinusSign",
    "repairs": "Wrench01", "restaurant": "Restaurant01", "rule": "CheckList", "salon": "Scissors",
    "savings": "PiggyBank", "scales": "BalanceScale", "schedule": "Clock01", "school": "Mortarboard02",
    "science": "TestTube01", "search": "Search01", "search_off": "SearchRemove",
    "settings_backup_restore": "History", "shield": "SecurityCheck", "shield_check": "SecurityCheck",
    "shoes": "RunningShoes", "shopping_cart": "ShoppingCart01", "signal_cellular_4_bar": "SignalFull02",
    "smartphone": "Smartphone", "sms": "Message01", "spa": "Flower", "star": "Star", "storefront": "Store01",
    "summarize": "News01", "swap_horiz": "ArrowDataTransferHorizontal",
    "swap_vert": "ArrowDataTransferVertical", "table": "LayoutTable01", "table_view": "LayoutTable01",
    "tag": "Tag01", "task_alt": "CheckmarkCircle02", "tax": "Taxes", "taxi": "Taxi",
    "text_fields": "TextFont", "tickets": "Ticket01", "timer": "Timer02", "today": "Calendar04",
    "toys": "Puzzle", "train": "Train01", "translate": "Translate", "travel": "Luggage01",
    "trending_down": "TrendingDown", "trending_up": "TrendingUp", "tune": "SlidersHorizontal", "tv": "Tv01",
    "two_wheeler": "Motorbike02", "undo": "Undo03", "upload_file": "Upload04", "vegetables": "Carrot",
    "visibility_off": "ViewOff", "volunteer_activism": "HandHeart", "wallet": "Wallet01",
    "wallpaper": "PaintBrush02", "water": "Droplet", "wifi": "Wifi01", "wifi_off": "WifiOff01",
}

ATTR = re.compile(r'(\w+):\s*"([^"]*)"')


def elements(pkg, huge):
    js = open(os.path.join(os.path.expanduser(pkg), "dist", "esm", f"{huge}Icon.js")).read()
    return [(m.group(1), dict(ATTR.findall(m.group(2)))) for m in re.finditer(r'\["(\w+)",\s*\{(.*?)\}\]', js)]


def f(v):
    return f"{float(v):g}"


def path_data(kind, a):
    if kind == "path":
        if "transform" in a:
            raise ValueError("transform not supported")
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


def drawable(name, weight, huge, els):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- Hugeicons {huge} (stroke rounded), MIT. Tint it: Icon(painterResource(R.drawable.ic_{name}_{weight}), tint = …) -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">',
    ]
    for kind, a in els:
        attrs = []
        if a.get("fill") == "currentColor":
            attrs.append('android:fillColor="@android:color/white"')
            if a.get("fillRule") == "evenodd":
                attrs.append('android:fillType="evenOdd"')
        if a.get("stroke") == "currentColor":
            attrs.append('android:strokeColor="@android:color/white"')
            attrs.append(f'android:strokeWidth="{a.get("strokeWidth", "1.5")}"')
            if a.get("strokeLinecap") in ("round", "square"):
                attrs.append(f'android:strokeLineCap="{a["strokeLinecap"]}"')
            if a.get("strokeLinejoin") in ("round", "bevel"):
                attrs.append(f'android:strokeLineJoin="{a["strokeLinejoin"]}"')
        if "opacity" in a:
            attrs.append(f'android:fillAlpha="{a["opacity"]}" android:strokeAlpha="{a["opacity"]}"')
        lines.append(f'    <path {" ".join(attrs)} android:pathData="{path_data(kind, a)}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main(pkg, names):
    everything = not names
    for name in names or sorted(ICONS):
        huge = ICONS[name]
        els = elements(pkg, huge)
        if not els:
            sys.exit(f"{huge}: no shapes")
        existing = [w for w in WEIGHTS if os.path.exists(os.path.join(OUT, f"ic_{name}_{w}.xml"))]
        for weight in (existing if everything and existing else WEIGHTS):
            open(os.path.join(OUT, f"ic_{name}_{weight}.xml"), "w").write(drawable(name, weight, huge, els))
        print(f"{name:28} ← {huge}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
