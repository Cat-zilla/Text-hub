"""Builds TextHub-<version>-source.zip: the complete project - source, tests, resources, icons,
themes, Gradle configuration, documentation, the release keystore and the pre-built APKs.

Only generated output (build/, .gradle/) and editor state (.idea/, *.iml) are skipped, so nothing
that matters can be silently left out. Run from the project root.
"""
import io
import os
import re
import zipfile

VERSION = "1.5.1"
ROOT = os.path.abspath(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(os.path.dirname(ROOT), "TextHub-%s-source.zip" % VERSION)

SKIP_DIRS = {"build", ".gradle", ".kotlin", ".idea", "__pycache__", ".git", "out"}
SKIP_FILES = {"local.properties", "TextHub-%s-source.zip" % VERSION}
SKIP_SUFFIX = (".iml",)
# Pre-built binaries of *other* versions would be stale inside this archive; the current release and
# debug APKs are included so the archive can be installed straight from without a build.
SKIP_APK = re.compile(r"TextHub-(?!%s)[0-9.]+-(release|debug)\.apk$" % re.escape(VERSION))

written = []
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
    for folder, dirs, files in os.walk(ROOT):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS and not d.startswith("."))
        for name in sorted(files):
            if name in SKIP_FILES or name.endswith(SKIP_SUFFIX) or SKIP_APK.search(name):
                continue
            path = os.path.join(folder, name)
            rel = os.path.relpath(path, ROOT)
            zf.write(path, os.path.join("TextHub", rel))
            written.append(rel)

print("wrote", OUT)
print("size", os.path.getsize(OUT), "bytes")
print("files", len(written))
print("kotlin", sum(1 for f in written if f.endswith(".kt")))
print("keystore", [f for f in written if f.endswith(".jks")])
print("apk", [f for f in written if f.endswith(".apk")])
print("tests", sum(1 for f in written if "/src/test/" in f))
print("docs", [f for f in written if f.startswith("docs/")])
for required in ("README.md", "app/build.gradle.kts", "core/build.gradle.kts",
                 "settings.gradle.kts", "gradle.properties", "gradlew",
                 "docs/ENCRYPTION_FORMAT.md", "docs/PRIVACY.md", "docs/TOOLS.md",
                 "app/src/main/AndroidManifest.xml", "app/src/main/res/values/strings.xml"):
    assert required in written, "missing from archive: " + required
print("all required files present")
