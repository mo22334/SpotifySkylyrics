#!/usr/bin/env python3
"""Lataa Google Fonts -fontit (OFL-lisenssi) ja luo niille Minecraftin font-JSONit.
Aja projektin juuresta:  python download_fonts.py
Lista pitää täsmätä Fonts.java:n FILES-listaan (slug)."""
import json, os, urllib.request

FONTS = [  # slug, repo-polku (github.com/google/fonts)
    ("pacifico", "ofl/pacifico/Pacifico-Regular.ttf"),
    ("lobster", "ofl/lobster/Lobster-Regular.ttf"),
    ("bangers", "ofl/bangers/Bangers-Regular.ttf"),
    ("permanentmarker", "ofl/permanentmarker/PermanentMarker-Regular.ttf"),
    ("satisfy", "ofl/satisfy/Satisfy-Regular.ttf"),
    ("indieflower", "ofl/indieflower/IndieFlower-Regular.ttf"),
    ("specialelite", "ofl/specialelite/SpecialElite-Regular.ttf"),
    ("creepster", "ofl/creepster/Creepster-Regular.ttf"),
    ("pressstart2p", "ofl/pressstart2p/PressStart2P-Regular.ttf"),
    ("righteous", "ofl/righteous/Righteous-Regular.ttf"),
    ("chewy", "ofl/chewy/Chewy-Regular.ttf"),
    ("kaushanscript", "ofl/kaushanscript/KaushanScript-Regular.ttf"),
    ("abrilfatface", "ofl/abrilfatface/AbrilFatface-Regular.ttf"),
    ("bebasneue", "ofl/bebasneue/BebasNeue-Regular.ttf"),
    ("staatliches", "ofl/staatliches/Staatliches-Regular.ttf"),
    ("monoton", "ofl/monoton/Monoton-Regular.ttf"),
    ("rocksalt", "ofl/rocksalt/RockSalt-Regular.ttf"),
    ("amaticsc", "ofl/amaticsc/AmaticSC-Regular.ttf"),
    ("greatvibes", "ofl/greatvibes/GreatVibes-Regular.ttf"),
    ("sacramento", "ofl/sacramento/Sacramento-Regular.ttf"),
    ("russoone", "ofl/russoone/RussoOne-Regular.ttf"),
    ("vt323", "ofl/vt323/VT323-Regular.ttf"),
    ("silkscreen", "ofl/silkscreen/Silkscreen-Regular.ttf"),
    ("lilitaone", "ofl/lilitaone/LilitaOne-Regular.ttf"),
    ("luckiestguy", "ofl/luckiestguy/LuckiestGuy-Regular.ttf"),
    ("pirataone", "ofl/pirataone/PirataOne-Regular.ttf"),
    ("mountainsofchristmas", "ofl/mountainsofchristmas/MountainsofChristmas-Regular.ttf"),
]

OUT = os.path.join("src", "main", "resources", "assets", "lyricsmod", "font")
os.makedirs(OUT, exist_ok=True)
ok = 0
for slug, path in FONTS:
    url = "https://github.com/google/fonts/raw/main/" + path
    ttf = os.path.join(OUT, slug + ".ttf")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as r, open(ttf, "wb") as f:
            f.write(r.read())
        with open(os.path.join(OUT, slug + ".json"), "w", encoding="utf-8") as f:
            json.dump({"providers": [{"type": "ttf", "file": f"lyricsmod:{slug}.ttf",
                                      "shift": [0, 0], "size": 11.0, "oversample": 4.0}]}, f, indent=2)
        print("OK     ", slug)
        ok += 1
    except Exception as e:
        print("EI ONNISTU", slug, e)
print(f"\n{ok}/{len(FONTS)} fonttia ladattu. Puuttuvat fontit eivät näy modin valikossa.")
