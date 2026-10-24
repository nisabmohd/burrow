"""Adds strings to values/ and every translation in one go.

usage: python3 tools/add_strings.py strings.json            (add new keys)
       python3 tools/add_strings.py --update strings.json   (set the text of existing keys too)
strings.json: {"key": {"en": "...", "de": "...", "es": "...", "fr": "...", "hi": "...", "ja": "...", "pt-rBR": "..."}}
Text is written as given (escape ' as \\' yourself). Existing keys are left alone and reported.
"""
import json, os, re, sys

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")
DIRS = {"en": "values", "de": "values-de", "es": "values-es", "fr": "values-fr", "hi": "values-hi", "ja": "values-ja", "pt-rBR": "values-pt-rBR"}

def main(path, update=False):
    data = json.load(open(path, encoding="utf-8"))
    for lang, d in DIRS.items():
        p = os.path.join(RES, d, "strings.xml")
        s = open(p, encoding="utf-8").read()
        add = ""
        for key, texts in data.items():
            if f'name="{key}"' in s:
                if update:
                    if lang not in texts:
                        sys.exit(f"{key}: no {lang} text")
                    s, n = re.subn(rf'(<string name="{key}"[^>]*>).*?(</string>)', lambda m: m.group(1) + texts[lang] + m.group(2), s, flags=re.S)
                    if n != 1:
                        sys.exit(f"{d}/{key}: matched {n} times")
                else:
                    print(f"skip {d}/{key}: exists")
                continue
            if lang not in texts:
                sys.exit(f"{key}: no {lang} text")
            add += f'    <string name="{key}">{texts[lang]}</string>\n'
        s = s.replace("</resources>", add + "</resources>")
        open(p, "w", encoding="utf-8").write(s)
    print(f"added {len(data)} strings to {len(DIRS)} languages")

if __name__ == "__main__":
    args = sys.argv[1:]
    main(args[-1], update="--update" in args)
