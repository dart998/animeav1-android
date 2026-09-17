#!/usr/bin/env python3
"""Check the JavaScript text blocks that Android injects into its WebView."""

from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "app/src/main/java/com/ovelayos/animeav1"


def text_block(filename: str) -> str:
    source = (SOURCES / filename).read_text(encoding="utf-8")
    return source.split('"""', 1)[1].split('"""', 1)[0]


SCRIPTS = {
    "site": '(function(){window.__animeav1AndroidLabel="Descargar";'
    'window.__animeav1AndroidVersion="AnimeAV1 Android v1.5.0";'
    + text_block("SiteIntegration.java"),
    "downloads": "(function(data,online){" + text_block("DownloadsIntegration.java") + "[],false);",
    "ad_style": text_block("AdBlocker.java"),
}

with tempfile.TemporaryDirectory() as directory:
    for name, script in SCRIPTS.items():
        path = Path(directory) / f"{name}.js"
        path.write_text(script, encoding="utf-8")
        subprocess.run(["node", "--check", str(path)], check=True)
        print(f"{name}: JavaScript syntax OK")
