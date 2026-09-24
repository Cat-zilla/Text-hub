"""Builds the two Text Hub release archives.

**1. The public source archive** (`~/TextHub-<version>-source.zip`)

The complete project - source, tests, resources, icons, themes, Gradle configuration,
documentation, the build scripts, the debug and release APKs - and **no signing material at all**.
This is the archive that can be uploaded, published or pushed to a public repository.

Excluded by name, by pattern and (as a second, independent check) by scanning the finished archive
for anything that looks like a private key or a password:

    *.jks  *.keystore  *.p12  *.pfx  *.pem  *.key  *.der  *.bks
    signing.properties  keystore.properties  local.properties  .netrc
    docs/SIGNING.md                     (documents the store and key passwords)
    *.iml, .idea/, build/, .gradle/, .kotlin/, __pycache__/, .git/

**2. The private signing archive** (`~/TextHub-<version>-signing-files.zip`)

The release keystore, the credentials file, the signing configuration and the signing document.
It is produced for the release owner only: it must never be uploaded, attached, committed or put
inside the public archive. The script refuses to build it into the project directory and prints a
warning to that effect.

Run from anywhere; paths are derived from this file's location.
"""
import hashlib
import io
import os
import re
import zipfile

VERSION = "1.6.7"
ROOT = os.path.abspath(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HOME = os.path.dirname(ROOT)
PUBLIC_ZIP = os.path.join(HOME, "TextHub-%s-source.zip" % VERSION)
PRIVATE_ZIP = os.path.join(HOME, "TextHub-%s-signing-files.zip" % VERSION)

SKIP_DIRS = {"build", ".gradle", ".kotlin", ".idea", "__pycache__", ".git", "out", ".venv"}

# Signing material: never in the public archive, by name ...
SKIP_NAMES = {
    "local.properties",
    "keystore.properties",
    "signing.properties",
    ".netrc",
    "docs/SIGNING.md",
}
# ... and never by extension, wherever it appears in the tree.
SKIP_SUFFIXES = (
    ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".der", ".bks", ".pkcs12",
    ".iml", ".jceks",
)

# Pre-built binaries of *other* versions would be stale inside this archive; the current release and
# debug APKs are included so the archive can be installed straight from without a build.
SKIP_APK = re.compile(r"TextHub-(?!%s)[0-9.]+-(release|debug)(-unsigned)?\.apk$" % re.escape(VERSION))

# A second, independent check on the finished archive: no file may contain *real* key material or a
# credential written as a literal.
#
# A private-key header alone proves nothing - the RSA tools parse PEM blocks and the tests use
# truncated dummies - so a real key is detected by the header followed by a base64 body of at least
# 100 characters. A credential is detected as an assignment to a quoted value; assigning from a
# variable, a Gradle property or the environment (which is what app/build.gradle.kts does) is not a
# stored secret.
SECRET_PATTERNS = (
    re.compile(rb"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED |PGP )?PRIVATE KEY-----[^\n]{0,4}\r?\n"
               rb"[A-Za-z0-9+/=\r\n]{100,}"),
    re.compile(rb"(?:store|key)Password\s*[:=]\s*[\"']"),
    re.compile(rb"(?:store|key)Password\s*=\s*[\"'][^\"']"),
)
# Reported for transparency, not an offence: code that *handles* PEM blocks.
PEM_HEADER = re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----")

REQUIRED_IN_PUBLIC = (
    "README.md",
    "app/build.gradle.kts",
    "core/build.gradle.kts",
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradlew",
    "docs/TOOLS.md",
    "docs/ENCRYPTION_FORMAT.md",
    "docs/PRIVACY.md",
    "docs/QA_CHECKLIST.md",
    "docs/UNIVERSAL_DECODER.md",
    "docs/RELEASE_REPORT_%s.md" % VERSION,
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/values/strings.xml",
    "core/src/main/kotlin/com/texthub/core/detector/UniversalDecoder.kt",
    "app/src/main/kotlin/com/texthub/app/ui/DragReorder.kt",
)


def public_files():
    for folder, dirs, files in os.walk(ROOT):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS and not d.startswith("."))
        for name in sorted(files):
            rel = os.path.relpath(os.path.join(folder, name), ROOT)
            if rel in SKIP_NAMES or name in SKIP_NAMES:
                continue
            if name.endswith(SKIP_SUFFIXES):
                continue
            if SKIP_APK.search(name):
                continue
            if "signing" in name.lower() and name.endswith((".properties", ".json", ".txt")):
                continue
            yield rel


def write_zip(path, entries):
    """`entries` is a list of (relative path inside the archive, absolute source path)."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for inside, source in entries:
            zf.write(source, inside)


def main():
    # ---------------------------------------------------------------- public archive
    written = []
    entries = []
    for rel in public_files():
        entries.append((os.path.join("TextHub", rel), os.path.join(ROOT, rel)))
        written.append(rel)

    for required in REQUIRED_IN_PUBLIC:
        assert required in written, "missing from the source archive: " + required

    # Belt and braces: nothing in the set may be signing material.
    for rel in written:
        assert not rel.endswith(SKIP_SUFFIXES), "signing material in the archive: " + rel
        assert rel not in SKIP_NAMES, "signing material in the archive: " + rel
        assert "keystore" not in rel.lower(), "keystore in the archive: " + rel
        assert rel != "docs/SIGNING.md", "the signing document must not be published"

    write_zip(PUBLIC_ZIP, entries)

    # Scan the finished archive: a secret hidden inside a file (a Gradle file, a test fixture, a
    # document) would otherwise slip through the name-based checks.
    offences = []
    mentions = []
    with zipfile.ZipFile(PUBLIC_ZIP) as zf:
        for info in zf.infolist():
            if info.is_dir() or info.file_size > 4_000_000:
                continue
            blob = zf.read(info.filename)
            for pattern in SECRET_PATTERNS:
                match = pattern.search(blob)
                if match:
                    offences.append((info.filename, bytes(match.group(0)[:60])))
            if PEM_HEADER.search(blob):
                mentions.append(info.filename)
    assert not offences, "credential-looking content in the public archive: %s" % offences

    print("public  ", PUBLIC_ZIP)
    print("  size  ", os.path.getsize(PUBLIC_ZIP), "bytes", "md5", md5(PUBLIC_ZIP))
    print("  files ", len(written), "(", sum(1 for f in written if f.endswith(".kt")), "Kotlin,", 
          sum(1 for f in written if "/src/test/" in f), "test files )")
    print("  docs  ", [f for f in written if f.startswith("docs/")])
    print("  apk   ", [f for f in written if f.endswith(".apk")])
    print("  signing material present: NONE (checked by name, by suffix and by content scan)")
    print("  files that merely mention a PEM header (code that parses keys, dummy fixtures):")
    for name in mentions:
        print("    -", name)

    # ---------------------------------------------------------------- private archive
    keystore = os.path.join(ROOT, "texthub-release.jks")
    credentials = os.path.join(ROOT, "keystore.properties")
    signing_doc = os.path.join(ROOT, "docs", "SIGNING.md")
    gradle = os.path.join(ROOT, "app", "build.gradle.kts")
    private_entries = []
    for label, source in (
        ("texthub-release.jks", keystore),
        ("keystore.properties", credentials),
        ("SIGNING.md", signing_doc),
        ("app-build.gradle.kts", gradle),
    ):
        if os.path.isfile(source):
            private_entries.append((label, source))
        else:
            print("  note: %s is not present, skipped" % label)
    assert any(entry[0].endswith(".jks") for entry in private_entries), \
        "the signing archive must contain the keystore"
    write_zip(PRIVATE_ZIP, private_entries)

    print("private ", PRIVATE_ZIP)
    print("  size  ", os.path.getsize(PRIVATE_ZIP), "bytes", "md5", md5(PRIVATE_ZIP))
    print("  files ", [entry[0] for entry in private_entries])
    print("  ** do not upload, attach or commit this archive; it is not part of the release **")
    with zipfile.ZipFile(PUBLIC_ZIP) as zf:
        assert not any("signing-files" in n for n in zf.namelist())


def md5(path):
    digest = hashlib.md5()
    with io.open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


if __name__ == "__main__":
    main()
