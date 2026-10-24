"""Собирает app/src/main/assets/aerials.json из манифеста Apple Aerial.

Использование: python3 build_aerials.py entries.json ru.json > aerials.json
entries.json и TVIdleScreenStrings.bundle/ru.lproj лежат в
https://sylvan.apple.com/Aerials/resources-16.tar (strings конвертировать: plutil -convert json).
"""
import json
import sys

entries = json.load(open(sys.argv[1]))
ru = json.load(open(sys.argv[2]))
out = []
for a in entries["assets"]:
    url = a.get("url-4K-SDR") or a.get("url-1080-SDR")
    if not url:
        continue
    poi = a.get("pointsOfInterest", {})
    name = ru.get(poi.get("0", ""), a.get("accessibilityLabel", "")).replace(" ", " ").replace("‑", "-")
    out.append({"url": url, "name": name})
json.dump(out, sys.stdout, ensure_ascii=False, indent=0)
