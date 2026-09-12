#!/usr/bin/env python3
"""Fetch CADB annotation JSON (small). Image zips stay manual (Drive/Baidu)."""

from __future__ import annotations

import argparse
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

CADB_ANN = {
    "composition_elements.json": (
        "https://raw.githubusercontent.com/bcmi/Image-Composition-Assessment-Dataset-CADB/"
        "master/annotations/composition_elements.json"
    ),
    "composition_scores.json": (
        "https://raw.githubusercontent.com/bcmi/Image-Composition-Assessment-Dataset-CADB/"
        "master/annotations/composition_scores.json"
    ),
    "scene_categories.json": (
        "https://raw.githubusercontent.com/bcmi/Image-Composition-Assessment-Dataset-CADB/"
        "master/annotations/scene_categories.json"
    ),
}

CADB_IMAGES = (
    "https://www.dropbox.com/scl/fi/fvlsnit7on6218szply4q/CADB_Dataset.zip"
    "?rlkey=mwt9eftdhmnawomv44x4deliw&dl=1"
)
PICD_PAGE = "https://github.com/CV-xueba/PICD_ImageComposition"


def fetch(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"GET {url} -> {dest}")
    req = urllib.request.Request(url, headers={"User-Agent": "AutoCam/0.1"})
    with urllib.request.urlopen(req, timeout=60) as resp, dest.open("wb") as out:
        out.write(resp.read())


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--out", type=Path, default=ROOT / "ml" / "datasets" / "raw")
    p.add_argument("--cadb-ann", action="store_true", help="download CADB json annotations")
    args = p.parse_args()
    if args.cadb_ann:
        cadb = args.out / "cadb"
        for name, url in CADB_ANN.items():
            fetch(url, cadb / name)
        print(f"CADB annotations in {cadb}")
        print("Put CADB images in ml/datasets/raw/cadb/images/")
        print(f"  zip: {CADB_IMAGES}")
    print(f"PICD images+labels: {PICD_PAGE} (Baidu/Drive; not redistributed here)")
    print("Folder of your own stills: ml/datasets/raw/folder/")


if __name__ == "__main__":
    main()
