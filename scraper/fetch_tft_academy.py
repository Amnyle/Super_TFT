#!/usr/bin/env python3
"""Fetch TFT Academy data and write one compact JSON file for the Super TFT app.

Usage: python3 scraper/fetch_tft_academy.py [output_path]

Only public guides (isPublic != false) are kept. The output has a stable
"hash" of its content (without the timestamp) so CI can skip no-op commits.
"""
import hashlib
import json
import re
import sys
import time
import urllib.request

BASE = "https://tftacademy.com"
ASSETS = "https://assets.tftacademy.com"
UA = "SuperTFT-data-bot/1.0 (+https://github.com/Amnyle/Super_TFT)"
FORMAT_VERSION = 1


def get(path, as_json=True):
    req = urllib.request.Request(BASE + path, headers={"User-Agent": UA, "Accept": "*/*"})
    last = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                body = r.read().decode("utf-8")
                return json.loads(body) if as_json else body
        except Exception as e:  # network hiccup: retry with backoff
            last = e
            time.sleep(2 ** attempt)
    raise RuntimeError(f"GET {path} failed: {last}")


TAG_RE = re.compile(r"<[^>]+>")


def clean(text):
    if not text:
        return ""
    text = re.sub(r"<TFTCurveTable[^>]*>", "X", text)
    text = TAG_RE.sub("", text)
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def detect_set_and_patch():
    html = get("/tierlist/comps", as_json=False)
    m = re.search(r"/api/tierlist/comps\?set=(\d+)", html)
    if not m:
        raise RuntimeError("Could not detect current set number")
    tft_set = int(m.group(1))
    patch = ""
    try:
        d = get("/tierlist/comps/__data.json")
        node = d["nodes"][0]["data"]
        patch = node[node[0]["patch"]]
    except Exception:
        pass
    return tft_set, patch


def unit(u, with_board=False):
    out = {"id": u["apiName"], "items": u.get("items") or [], "stars": u.get("stars") or 1}
    if with_board and u.get("boardIndex") is not None:
        out["hex"] = u["boardIndex"]
    if u.get("predecessors"):
        out["replaces"] = u["predecessors"]
    return out


def main(out_path):
    tft_set, patch = detect_set_and_patch()
    q = f"?set={tft_set}"

    champs_raw = get("/api/assets/champions" + q)["champions"]
    items_raw = get("/api/assets/items" + q)["items"]
    traits_raw = get("/api/assets/traits" + q)["traits"]
    augs_raw = get("/api/assets/augments" + q)["augments"]
    comps_raw = get("/api/tierlist/comps" + q)["guides"]
    item_tiers_raw = get("/api/tierlist/items" + q)["items_tierlists"]
    aug_tiers_raw = get("/api/tierlist/augments" + q)["augments_tierlists"]

    champions = [
        {
            "id": c["apiName"],
            "name": c["name"],
            "cost": c.get("cost") or 0,
            "role": c.get("role") or "",
            "traits": list((c.get("traitContributions") or {}).keys()),
            "abilityName": c.get("abilityName") or "",
            "ability": clean(c.get("ability")),
            "items": c.get("recommendedItems") or [],
        }
        for c in champs_raw
    ]
    items = [
        {
            "id": i["apiName"],
            "name": i["name"],
            "type": i.get("type") or "others",
            "desc": clean(i.get("description")),
            "from": i.get("composition") or [],
        }
        for i in items_raw
    ]
    traits = [
        {
            "id": t["apiName"],
            "name": t["name"],
            "type": t.get("type") or "",
            "desc": clean(t.get("description")),
            "breakpoints": t.get("breakpoints") or [],
            "colors": t.get("colors") or [],
            "effects": [clean(e) for e in (t.get("effects") or [])],
        }
        for t in traits_raw
    ]
    augments = [
        {
            "id": a["apiName"],
            "name": a["name"],
            "tier": a.get("tier") or 0,
            "desc": clean(a.get("description")),
        }
        for a in augs_raw
        if not a.get("disabled")
    ]

    comps = []
    for g in sorted(comps_raw, key=lambda g: g.get("displayIndex") or 0):
        if g.get("isPublic") is False:
            continue
        comps.append(
            {
                "id": g["id"],
                "slug": g.get("compSlug") or "",
                "title": g.get("title") or g.get("metaTitle") or "",
                "tier": g.get("tier") or "",
                "style": g.get("style") or "",
                "difficulty": g.get("difficulty") or "",
                "main": (g.get("mainChampion") or {}).get("apiName", ""),
                "mainItem": (g.get("mainItem") or {}).get("apiName", ""),
                "mainAugment": (g.get("mainAugment") or {}).get("apiName", ""),
                "board": [unit(u, True) for u in g.get("finalComp") or []],
                "early": [unit(u) for u in g.get("earlyComp") or []],
                "levelUp": [unit(u) for u in g.get("levelUpUnits") or []],
                "capstone": [unit(u) for u in g.get("maxCap") or []],
                "carousel": [c["apiName"] for c in g.get("carousel") or []],
                "augments": [a["apiName"] for a in g.get("augments") or [] if not a.get("disabled")],
                "augmentTypes": g.get("augmentTypes") or [],
                "augmentsTip": clean(g.get("augmentsTip")),
                "tips": [
                    {"stage": t.get("stage", ""), "tip": clean(t.get("tip"))}
                    for t in g.get("tips") or []
                    if clean(t.get("tip"))
                ],
                "updated": g.get("updated") or "",
            }
        )

    tiers = ["S", "A", "B", "C"]
    item_tiers = {
        t["type"]: {k: t["tier"].get(k, []) for k in tiers}
        for t in item_tiers_raw
        if any(t["tier"].get(k) for k in tiers)
    }
    aug_tiers = [
        {
            "rarity": t.get("augmenttier") or 0,
            "stage": t.get("stage") or "",
            "tiers": {k: t["tier"].get(k, []) for k in tiers},
        }
        for t in aug_tiers_raw
        if any(t["tier"].get(k) for k in tiers)
    ]
    aug_tiers.sort(key=lambda t: (t["stage"], t["rarity"]))

    data = {
        "format": FORMAT_VERSION,
        "source": BASE,
        "assets": ASSETS,
        "set": tft_set,
        "patch": patch,
        "comps": comps,
        "champions": champions,
        "items": items,
        "traits": traits,
        "augments": augments,
        "itemTiers": item_tiers,
        "augmentTiers": aug_tiers,
    }
    if not comps or not champions or not items:
        raise RuntimeError("Fetched data looks empty; refusing to write it")

    body = json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    data["hash"] = hashlib.sha256(body.encode()).hexdigest()[:16]
    data["generatedAt"] = int(time.time())

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(
        f"set {tft_set} patch {patch}: {len(comps)} comps, {len(champions)} champions, "
        f"{len(items)} items, {len(traits)} traits, {len(augments)} augments -> {out_path} "
        f"(hash {data['hash']})"
    )


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "tft.json")
