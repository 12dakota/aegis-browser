#!/usr/bin/env python3
"""Bump GeckoView + AndroidX WebKit to newest versions from Maven Central / Mozilla."""
import re
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app" / "build.gradle.kts"
PROPS = ROOT / "gradle" / "engine-versions.properties"


def latest_maven(group: str, artifact: str) -> str | None:
    # Maven Central search API
    q = f'g:"{group}" AND a:"{artifact}"'
    url = "https://search.maven.org/solrsearch/select?q=" + urllib.parse.quote(q) + "&rows=5&wt=json"
    import urllib.parse
    with urllib.request.urlopen(url, timeout=30) as r:
        data = json.load(r)
    docs = data.get("response", {}).get("docs", [])
    if not docs:
        return None
    # Prefer highest version string that looks like a release
    versions = [d.get("latestVersion") or d.get("v") for d in docs]
    versions = [v for v in versions if v]
    return versions[0] if versions else None


def latest_geckoview() -> str | None:
    # Mozilla maven: try maven-metadata
    urls = [
        "https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml",
        "https://repo1.maven.org/maven2/org/mozilla/geckoview/geckoview/maven-metadata.xml",
    ]
    for url in urls:
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                xml = r.read().decode()
            # <latest>...</latest> or last <version>
            m = re.search(r"<latest>([^<]+)</latest>", xml)
            if m:
                return m.group(1).strip()
            vers = re.findall(r"<version>([^<]+)</version>", xml)
            if vers:
                return vers[-1].strip()
        except Exception as e:
            print("geckoview meta failed", url, e)
    return latest_maven("org.mozilla.geckoview", "geckoview")


def main():
    import urllib.parse  # noqa

    gecko = latest_geckoview()
    webkit = latest_maven("androidx.webkit", "webkit")
    print("Latest geckoview:", gecko)
    print("Latest webkit:", webkit)

    if not gecko and not webkit:
        raise SystemExit("Could not resolve engine versions")

    text = GRADLE.read_text()
    changed = False
    if gecko:
        text2, n = re.subn(
            r'implementation\("org\.mozilla\.geckoview:geckoview:[^"]+"\)',
            f'implementation("org.mozilla.geckoview:geckoview:{gecko}")',
            text,
        )
        if n:
            text = text2
            changed = True
            print("Updated geckoview in build.gradle.kts")
    if webkit:
        text2, n = re.subn(
            r'implementation\("androidx\.webkit:webkit:[^"]+"\)',
            f'implementation("androidx.webkit:webkit:{webkit}")',
            text,
        )
        if n:
            text = text2
            changed = True
            print("Updated webkit in build.gradle.kts")

    if changed:
        GRADLE.write_text(text)
        PROPS.write_text(
            f"# Updated by scripts/update-engines.py\n"
            f"geckoview={gecko or ''}\n"
            f"webkit={webkit or ''}\n"
        )
        print("Wrote", PROPS)
    else:
        print("No changes")


if __name__ == "__main__":
    main()
