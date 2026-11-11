"""Regenerates the README's badges and stats block.

Usage, from the project root after `./gradlew build` (which runs the unit tests and builds the jar):
    python3 -I scripts/stats.py
Standard library only.
"""
import html
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
REPO = "Atomiceatscode/serverworks"
ASSETS = ROOT / "src/main/resources/assets/serverworks"
DATA = ROOT / "src/main/resources/data/serverworks"

# Folder prefix -> (module name, colour). First match wins.
MODULES = [
    ("src/main/java", ("Mod (server side)", "#e8833a")),
    ("src/client/java", ("Client", "#3a86e8")),
    ("src/test/java", ("Unit tests", "#4caf50")),
    ("src/gametest/java", ("Game tests", "#2e9e8f")),
    ("art", ("Art pipeline", "#9c6ade")),
    ("scripts", ("Scripts", "#8a8f98")),
]
SOURCE_SUFFIXES = {".java", ".py"}


def module_of(rel):
    for prefix, info in MODULES:
        if rel.startswith(prefix + "/"):
            return info
    return None


def source_files():
    out = []
    for path in sorted(ROOT.rglob("*")):
        rel = path.relative_to(ROOT).as_posix()
        if not path.is_file() or path.suffix not in SOURCE_SUFFIXES or "/.venv/" in "/" + rel:
            continue
        info = module_of(rel)
        if info:
            lines = sum(1 for line in path.read_text(encoding="utf-8").splitlines() if line.strip())
            out.append((rel, lines, info[0], info[1]))
    return out


def unit_tests():
    total = failed = 0
    for report in (ROOT / "build/test-results/test").glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        total += int(suite.get("tests", 0))
        failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    if total == 0:
        raise SystemExit("No unit test results: run ./gradlew build first.")
    return total, total - failed


def game_tests():
    manifest = json.loads((ROOT / "src/gametest/resources/fabric.mod.json").read_text(encoding="utf-8"))
    return len(manifest["entrypoints"]["fabric-client-gametest"])


def gradle_property(name):
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    return "?"


def gradle_version():
    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    match = re.search(r"gradle-([0-9.]+)-(?:bin|all)\.zip", wrapper)
    return match.group(1) if match else "?"


def counts():
    lang = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
    advancements = [p for p in (DATA / "advancement").rglob("*.json") if "recipes" not in p.parts]
    return {
        # Waterfall is listed but cannot start on this Minecraft version.
        "platforms": sum(1 for key in lang if key.startswith("serverworks.platform.") and key.count(".") == 2
                         and key != "serverworks.platform.waterfall"),
        "textures": sum(1 for _ in (ASSETS / "textures").rglob("*.png")),
        "sounds": sum(1 for _ in (ASSETS / "sounds").rglob("*.ogg")),
        "recipes": len(json.loads((ASSETS / "manual_recipes.json").read_text(encoding="utf-8"))["recipes"]),
        "advancements": len(advancements),
        "blocks": sum(1 for _ in (ASSETS / "blockstates").glob("*.json")),
        "items": sum(1 for _ in (ASSETS / "items").glob("*.json")),
        "manual_pages": sum(1 for key in lang if key.startswith("serverworks.manual.") and key.endswith(".title")),
    }


def jar_size_kb():
    jar = ROOT / f"build/libs/serverworks-{gradle_property('version')}.jar"
    return round(jar.stat().st_size / 1024) if jar.exists() else None


def commits():
    result = subprocess.run(["git", "rev-list", "--count", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    return int(result.stdout.strip()) if result.returncode == 0 and result.stdout.strip() else 1


# ---- badges ----

def badge(label, message, color, logo=None, link=None):
    def part(text):
        return quote(str(text).replace("-", "--").replace("_", "__").replace(" ", "_"), safe="_.,+%/()✓")
    url = f"https://img.shields.io/badge/{part(label)}-{part(message)}-{color.lstrip('#')}"
    if logo:
        url += f"?logo={logo}&logoColor=white"
    img = f'<img alt="{html.escape(label)}: {html.escape(str(message))}" src="{url}">'
    return f'<a href="{link}">{img}</a>' if link else img


def badges(stats):
    tests, passing = stats["tests"]
    c = stats["counts"]
    rows = [
        [badge("CI", f"build + {tests} tests", "success", link=f"https://github.com/{REPO}/actions/workflows/ci.yml"),
         badge("unit tests", f"{passing}/{tests} passing", "brightgreen" if passing == tests else "red"),
         badge("game tests", f"{stats['game_tests']} walkthroughs", "brightgreen"),
         badge("license", "MIT", "blue", link=f"https://github.com/{REPO}/blob/main/LICENSE")],
        [badge("Minecraft", gradle_property("minecraft_version"), "62B47A"),
         badge("Fabric Loader", gradle_property("loader_version") + "+", "DBD0B4"),
         badge("Fabric API", gradle_property("fabric_api_version").split("+")[0], "DBD0B4"),
         badge("Java", "25", "ED8B00", logo="openjdk"),
         badge("Gradle", gradle_version(), "02303A", logo="gradle"),
         badge("powered by", "Modrinth API", "1BD96A", logo="modrinth")],
        [badge("platforms", c["platforms"], "6f42c1"),
         badge("blocks", c["blocks"], "6f42c1"),
         badge("items", c["items"], "6f42c1"),
         badge("recipes", c["recipes"], "6f42c1"),
         badge("advancements", c["advancements"], "6f42c1"),
         badge("textures", f"{c['textures']} generated", "6f42c1"),
         badge("sounds", f"{c['sounds']} synthesized", "6f42c1")],
        [badge("built with", "Claude Code", "d97757"),
         badge("made by", "Claude", "d97757"),
         badge("art", "100% generated by script", "ff7a00")],
        [badge("lines of code", f"{stats['lines']:,}", "informational"),
         badge("Java files", stats["java_files"], "informational"),
         badge("jar size", f"{stats['jar_kb']:,} KB" if stats["jar_kb"] else "not built", "informational"),
         badge("commits", stats["commits"], "informational"),
         badge("version", gradle_property("version"), "informational")],
    ]
    out = ["<!-- badges:start -->"]
    for row in rows:
        out.append('<p align="center">')
        out.extend(row)
        out.append("</p>")
    out.append("<!-- badges:end -->")
    return "\n".join(out)


# ---- stats block ----

def bar(fraction, width=20):
    filled = round(fraction * width)
    return "█" * filled + "░" * (width - filled)


def stats_block(stats, files):
    tests, passing = stats["tests"]
    c = stats["counts"]
    by_module = {}
    for _, lines, module, _ in files:
        by_module[module] = by_module.get(module, 0) + lines
    biggest = max(by_module.values())
    lines = [
        "<!-- stats:start -->",
        "### Stats",
        "",
        "| Overall | |",
        "|---|---|",
        f"| Unit tests | **{passing}/{tests}** passing |",
        f"| In-game walkthroughs | **{stats['game_tests']}** client game tests driving a real game |",
        f"| Platforms | **{c['platforms']}** server platforms and proxies that start and run |",
        f"| Content | {c['blocks']} blocks, {c['items']} items, {c['recipes']} recipes, {c['advancements']} advancements, "
        f"{c['manual_pages']} manual topics |",
        f"| Art | {c['textures']} textures and {c['sounds']} sounds, all generated by the scripts in `art/` |",
        f"| Lines of code | **{stats['lines']:,}** across {stats['java_files']} Java and {stats['py_files']} Python files |",
        f"| Release jar | {stats['jar_kb']:,} KB |" if stats["jar_kb"] else "| Release jar | not built |",
        f"| Commits | {stats['commits']} |",
        "",
        "| Module | Non-blank lines | Share |",
        "|---|---|---|",
    ]
    total = sum(by_module.values())
    for module, count in sorted(by_module.items(), key=lambda kv: -kv[1]):
        lines.append(f"| {module} | {count:,} | {bar(count / biggest)} {100 * count / total:.0f}% |")
    lines += ["", f"<sub>Generated by `scripts/stats.py` on {date.today().isoformat()}.</sub>", "<!-- stats:end -->"]
    return "\n".join(lines)


def replace_block(text, name, block):
    pattern = re.compile(rf"<!-- {name}:start -->.*?<!-- {name}:end -->", re.S)
    if not pattern.search(text):
        raise SystemExit(f"README has no {name} markers")
    return pattern.sub(lambda _: block, text)


def main():
    files = source_files()
    stats = {
        "tests": unit_tests(),
        "game_tests": game_tests(),
        "counts": counts(),
        "lines": sum(lines for _, lines, _, _ in files),
        "java_files": sum(1 for rel, *_ in files if rel.endswith(".java")),
        "py_files": sum(1 for rel, *_ in files if rel.endswith(".py")),
        "jar_kb": jar_size_kb(),
        "commits": commits(),
    }
    readme = ROOT / "README.md"
    text = readme.read_text(encoding="utf-8")
    text = replace_block(text, "badges", badges(stats))
    text = replace_block(text, "stats", stats_block(stats, files))
    readme.write_text(text, encoding="utf-8")
    print(f"{stats['lines']:,} lines, {stats['tests'][1]}/{stats['tests'][0]} unit tests")


if __name__ == "__main__":
    main()
