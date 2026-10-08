#!/usr/bin/env python3
"""Find CC0 recordings on Freesound for the built-in sounds and download them into sound-sources/.

For each built-in sound it runs the searches in QUERIES (CC0 only, within a duration range), ranks the
results by rating and popularity, downloads the best one's high-quality OGG preview (full length,
192 kbps; originals need OAuth) into sound-sources/<sound>/, and records it in tools/sound_sources.json
for tools/import_sounds.py. The runners-up are saved to sound-sources/<sound>/candidates.json.

Sounds that already have a manifest entry are skipped, so hand-picked choices stick.

Usage:
  python tools/fetch_freesound.py --list             # only search; write candidates.json files to review
  python tools/fetch_freesound.py                    # fill in every sound without an entry
  python tools/fetch_freesound.py rain wind          # only these (replacing their current entry)
  python tools/fetch_freesound.py --pick rain=12345  # use a specific Freesound sound id

The API key is read from the FREESOUND_API_KEY environment variable or a .freesound-key file in the
repo root (gitignored). Get one at https://freesound.org/apiv2/apply.
"""
import argparse
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "sound-sources"
MANIFEST = ROOT / "tools" / "sound_sources.json"
API = "https://freesound.org/apiv2"
FIELDS = "id,name,url,username,license,duration,tags,description,avg_rating,num_ratings,num_downloads,previews"

# sound: (searches, min seconds, max seconds). Loops want long, steady recordings; one-shots short ones.
LOOP = (45, 300)  # previews are full length; long recordings are slow to fetch and only 45 s is used
QUERIES = {
    "town_crowd": (["medieval town ambience", "town square crowd -music", "crowd walla outdoor -music"], *LOOP),
    "tavern_chatter": (["pub ambience -music", "tavern crowd", "restaurant walla -music"], *LOOP),
    "market_crowd": (["market ambience crowd", "street market -music"], *LOOP),
    "rain": (["rain loop -thunder -music", "rain on roof -thunder"], *LOOP),
    "wind": (["wind loop -rain", "wind howling -rain"], *LOOP),
    "hearth_fire": (["fireplace crackling", "fireplace"], *LOOP),
    "torches": (["torch burning", "fire crackle"], 30, 300),
    "horse_cart": (["horse carriage", "horse cart", "horse hooves cobblestone"], 20, 300),
    "birdsong": (["forest birds", "birdsong forest"], *LOOP),
    "forest_breeze": (["wind trees leaves", "leaves rustling wind"], *LOOP),
    "stream": (["stream brook", "creek water"], *LOOP),
    "crickets": (["crickets night"], *LOOP),
    "water_drips": (["cave dripping water", "water drips cave"], *LOOP),
    "dark_drone": (["dark drone ambience", "dungeon ambience"], *LOOP),
    "chains": (["chains rattle"], 15, 300),
    "cave_wind": (["cave ambience", "cave wind"], *LOOP),
    "tavern_music": (["medieval music", "tavern music", "lute music"], 30, 400),
    "church_bell": (["church bell toll", "church bell"], 3, 40),
    # Adventures pack
    "ocean_waves": (["ocean waves", "sea waves", "waves boat"], *LOOP),
    "ship_creak": (["ship creaking", "wooden boat creaking", "ship hull creak"], 20, 300),
    "seagulls": (["seagulls", "gulls harbour", "seagull"], 15, 300),
    "swamp_frogs": (["frogs night", "frogs chorus", "swamp ambience"], *LOOP),
    "mud_bubbles": (["bubbling mud", "mud bubbles", "swamp bubbles"], 10, 300),
    "great_hall": (["banquet hall crowd -music", "large hall crowd -music", "castle ambience"], *LOOP),
    "temple_choir": (["choir chant", "monks chanting", "gregorian chant"], 30, 400),
    "temple_bells": (["temple bell", "singing bowl", "tibetan bell"], 3, 60),
    "blizzard": (["blizzard", "snow storm wind", "winter storm wind"], *LOOP),
    "wolves": (["wolves howling", "wolf howl"], 5, 300),
    "event_fire": (["fireball", "fire whoosh"], 0.8, 8),
    "event_explosion": (["explosion"], 1, 10),
    "event_light": (["magic shimmer", "heal spell", "holy spell"], 1, 10),
    "event_thunder": (["thunder clap", "thunder"], 2, 15),
    "event_sword": (["sword clash", "sword hit"], 0.3, 5),
    "event_magic": (["magic spell cast", "spell"], 0.5, 8),
    "event_roar": (["monster roar", "creature roar"], 1, 8),
    "event_arrows": (["arrow whoosh", "arrow impact", "bow arrow"], 0.3, 5),
}


def api_key():
    key = os.environ.get("FREESOUND_API_KEY")
    key_file = ROOT / ".freesound-key"
    if not key and key_file.exists():
        key = key_file.read_text(encoding="utf-8").strip()
    if not key:
        sys.exit("No API key: set FREESOUND_API_KEY or put it in .freesound-key (https://freesound.org/apiv2/apply)")
    return key


def get_json(path, key, **params):
    url = f"{API}{path}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url, headers={"Authorization": f"Token {key}", "User-Agent": "TavernTales-tools"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def score(sound):
    """Bayesian-averaged rating (prior: 3.5 stars from 5 votes) weighted by log popularity."""
    rating = (sound["avg_rating"] * sound["num_ratings"] + 3.5 * 5) / (sound["num_ratings"] + 5)
    return rating * math.log10(sound["num_downloads"] + 10)


def search(name, key):
    queries, low, high = QUERIES[name]
    found = {}
    for query in queries:
        data = get_json(
            "/search/", key, query=query, page_size=30, fields=FIELDS, sort="downloads_desc",
            filter=f'license:"Creative Commons 0" duration:[{low} TO {high}]',
        )
        for sound in data["results"]:
            found.setdefault(sound["id"], sound)
        time.sleep(1.1)  # stay well under the API's per-minute limit
    return sorted(found.values(), key=score, reverse=True)


def slug(text):
    return re.sub(r"[^A-Za-z0-9]+", "-", text).strip("-")[:60] or "sound"


def download(name, sound):
    folder = SOURCES / name
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / f"{sound['id']}__{slug(sound['username'])}__{slug(sound['name'])}.ogg"
    partial = target.with_suffix(".part")
    request = urllib.request.Request(sound["previews"]["preview-hq-ogg"], headers={"User-Agent": "TavernTales-tools"})
    with urllib.request.urlopen(request, timeout=60) as response, open(partial, "wb") as out:
        while chunk := response.read(1 << 16):
            out.write(chunk)
    for old in folder.glob("*__*.ogg"):  # previous pick
        old.unlink()
    partial.rename(target)
    return target


def save_manifest(manifest):
    MANIFEST.write_text(json.dumps(dict(sorted(manifest.items())), indent=2) + "\n", encoding="utf-8", newline="\n")


def save_candidates(name, results):
    folder = SOURCES / name
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "candidates.json").write_text(json.dumps([
        {"id": s["id"], "name": s["name"], "by": s["username"], "seconds": round(s["duration"]),
         "rating": round(s["avg_rating"], 1), "ratings": s["num_ratings"], "downloads": s["num_downloads"],
         "score": round(score(s), 2), "tags": s["tags"][:15], "description": s.get("description", "")[:300],
         "url": s["url"]}
        for s in results[:12]
    ], indent=2), encoding="utf-8")


def entry_for(sound, file):
    return {
        "file": file.relative_to(SOURCES).as_posix(),
        "title": sound["name"],
        "author": sound["username"],
        "source": sound["url"],
        "license": "CC0 1.0" if sound["license"].rstrip("/").endswith("zero/1.0") else sound["license"],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sounds", nargs="*", help="built-in sound names (default: all without a manifest entry)")
    parser.add_argument("--pick", action="append", default=[], metavar="SOUND=ID", help="use this Freesound id")
    parser.add_argument("--list", action="store_true", help="only search and write candidates.json files")
    args = parser.parse_args()
    key = api_key()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}

    picks = dict(p.split("=", 1) for p in args.pick)
    for name, sound_id in picks.items():
        sound = get_json(f"/sounds/{sound_id}/", key, fields=FIELDS)
        if "zero" not in sound["license"]:
            print(f"  note: {name} pick {sound_id} is {sound['license']}, check that it allows bundling")
        manifest[name] = entry_for(sound, download(name, sound))
        save_manifest(manifest)
        print(f"  {name}: picked {sound['name']!r} by {sound['username']} ({sound['duration']:.0f} s)", flush=True)
    if picks and not args.sounds:
        return

    names = args.sounds or [n for n in QUERIES if args.list or n not in manifest]
    for name in names:
        if name in picks:
            continue
        if name not in QUERIES:
            sys.exit(f"unknown sound {name}")
        results = search(name, key)
        if not results:
            print(f"  {name}: nothing found, keeping the synthesized sound", flush=True)
            continue
        save_candidates(name, results)
        if args.list:
            print(f"  {name}: {len(results)} candidates", flush=True)
            continue
        best = results[0]
        manifest[name] = entry_for(best, download(name, best))
        save_manifest(manifest)
        print(f"  {name}: {best['name']!r} by {best['username']} ({best['duration']:.0f} s, "
              f"{best['avg_rating']:.1f}* x{best['num_ratings']}, {best['num_downloads']} downloads)", flush=True)


if __name__ == "__main__":
    main()
