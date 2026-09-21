#!/usr/bin/env python3
"""Round 4 documentation pass: 79 tools, the dedicated AES tools, AES-CTR, raw-key AES-GCM,
the RSA hybrid and key generator, accent colours and the new icon."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path, pairs):
    p = ROOT / path
    s = p.read_text(encoding="utf-8")
    for old, new in pairs:
        if old in s:
            s = s.replace(old, new, 1)
        else:
            print(f"  !! anchor missing in {path}: {old[:70]!r}")
    p.write_text(s, encoding="utf-8")
    print(f"patched {path}")


# ------------------------------------------------------------------- TOOLS.md
new_secure_rows = """| AES-128-GCM | SEC | Password | Fixed 128-bit key; only reads 128-bit payloads |
| AES-192-GCM | SEC | Password | Fixed 192-bit key; only reads 192-bit payloads |
| AES-256-GCM | SEC | Password | Fixed 256-bit key; only reads 256-bit payloads |
| AES-128-CBC + HMAC | SEC | Password | Fixed 128-bit key, Encrypt-then-MAC |
| AES-192-CBC + HMAC | SEC | Password | Fixed 192-bit key, Encrypt-then-MAC |
| AES-256-CBC + HMAC | SEC | Password | Fixed 256-bit key, Encrypt-then-MAC |
| AES-CTR + HMAC | SEC | Password | Key size 128 / 192 / 256; random counter block per message |
| AES-GCM with your own key | SEC | Raw key | Key pasted as Base64 or hex (16, 24 or 32 bytes); no key derivation |
| RSA-OAEP + AES-GCM | SEC | RSA key (PEM) | Public key to encrypt, private key to decrypt; hybrid, any text length |
| RSA Key Pair Generator | KEYGEN | – | 2048 / 3072 / 4096 bits; prints both PEM blocks and a fingerprint |"""

new_examples = """AES-128-GCM   "hi" + password          -> Base64 payload; readable only by AES-128-GCM or AES-GCM
AES-256-GCM   "hi" + password          -> 256-bit payload; AES-128-GCM refuses it on purpose
AES-CTR+HMAC  "hi" + password          -> Base64 (0x04): salt, counter block, ciphertext, HMAC tag
AES-GCM rawkey key = 32 zero bytes (Base64 "AAAA...=")  -> 16-byte key: "AAA...=" (16 bytes)
RSA-OAEP      "hi" + public PEM        -> 0x11 payload: RSA-wrapped 32-byte AES key + GCM ciphertext
RSA keygen    2048-bit                 -> public PEM, private PEM, "fingerprint (SHA-256): …"
"""

patch(
    "docs/TOOLS.md",
    [
        ("Every one of the **69 methods** available in Text Hub",
         "Every one of the **79 methods** available in Text Hub"),
        ("`CKSUM` checksum (integrity only).",
         "`CKSUM` checksum (integrity only) · `KEYGEN` key material (not encryption)."),
        ("| ChaCha20-Poly1305 | SEC | Password | – (Android 9+; 256-bit key) |",
         "| ChaCha20-Poly1305 | SEC | Password | – (Android 9+; 256-bit key) |\n" + new_secure_rows),
        ("""JWT           header {"alg":"HS256"}…    -> header and payload decoded, alg shown with a note that
                                            the signature is never verified""",
         """JWT           header {"alg":"HS256"}…    -> header and payload decoded, alg shown with a note that
                                            the signature is never verified
""" + new_examples.rstrip()),
        ("* Enigma is reciprocal and never substitutes a letter for itself:",
         """* The fixed-size AES tools (AES-128/192/256-GCM and the CBC twins) are deliberately not
  interchangeable: each one only reads payloads of its own key size. Use *AES-GCM Encryption* or
  *AES-CBC + HMAC* when the key size should be detected instead.
* RSA-OAEP cannot encrypt more than a short block on its own, so this tool wraps a fresh 256-bit
  AES key with the public key and encrypts the text with AES-256-GCM. Only the matching private
  key can read the result, and nothing else can be decrypted with it.
* The RSA Key Pair Generator produces key material, not ciphertext. It is one-way by nature:
  generating again always produces a different pair.
* AES-GCM with your own key does no key stretching - unlike the password tools, a weak key stays
  weak, which is exactly why the password tools exist.
* Enigma is reciprocal and never substitutes a letter for itself:"""),
        ('* That any of its output is "unbreakable".',
         """* That any of its output is "unbreakable".
* That RSA-OAEP replaces a password. It removes the need to share a secret in advance, but it also
  means the private key becomes the single thing that must never leak. Text Hub does not manage,
  escrow or recover keys.
* That a key generated here is stored anywhere: nothing is written to disk. Losing the text means
  losing the key."""),
    ],
)

# --------------------------------------------------- docs/ENCRYPTION_FORMAT.md
payloads = """### 6.3 AES-CTR + HMAC (tool id `aesctr`, `version = 0x04`)

```
byte 0        version        0x04
byte 1        kdfId          0x01  (PBKDF2-HMAC-SHA256, 210 000 iterations, key length + 32 bytes)
bytes 2..17   salt           16 random bytes
bytes 18..33  counter block  16 random bytes - the initial CTR counter, never reused
bytes 34..n   ciphertext     AES-CTR keystream output (no padding, length preserving)
bytes n..n+32 HMAC-SHA256    tag over every preceding byte
```

* CTR is a stream mode: it provides **no** integrity on its own and repeating a counter block with
  the same key is catastrophic. Both risks are closed here - a fresh random counter block per
  message, and Encrypt-then-MAC with a separate 32-byte HMAC key from the same PBKDF2 output.
* The tag is verified with `MessageDigest.isEqual` before decryption, so no plaintext is produced
  from unauthenticated data.
* Offered for interoperability; AES-GCM is the recommended default.

### 6.4 AES-GCM with a raw key (tool id `aesrawkey`, `version = 0x05`)

```
byte 0        version        0x05
byte 1        key length     16, 24 or 32 (AES-128 / AES-192 / AES-256)
bytes 2..13   IV / nonce     12 random bytes
bytes 14..n   ciphertext     AES-GCM ciphertext followed by the 16-byte tag
```

* There is **no salt and no KDF**: the key is used exactly as pasted (Base64 or hex). The header
  stores the key *length*, never the key.
* A payload announces its key length, so decrypting with a key of the wrong size is reported as a
  key problem rather than as a mysterious failure.
* No key stretching means a weak key stays weak - use the password tools unless the key already
  exists somewhere else.

### 6.5 RSA-OAEP + AES-GCM hybrid (tool id `rsa`, `version = 0x11`)

```
byte 0         version        0x11
byte 1         algorithm      0x01 (RSA-OAEP-SHA256 wrapping AES-256-GCM)
bytes 2..3     wrapped length big-endian, 256 / 384 / 512 bytes for 2048 / 3072 / 4096-bit keys
bytes 4..k     wrapped key    RSA-OAEP encryption of a fresh random 32-byte AES key
bytes k..k+11  IV / nonce     12 random bytes
bytes k+12..n  ciphertext     AES-GCM ciphertext followed by the 16-byte tag
```

* Public-key encryption, so the sender needs no shared secret: encrypt with the recipient's
  `-----BEGIN PUBLIC KEY-----` block, decrypt with the matching `-----BEGIN PRIVATE KEY-----`
  block. Keys may be pasted with surrounding text; the PEM block is located inside it.
* Hybrid by necessity: a 2048-bit RSA key can only carry 190 bytes with OAEP-SHA256. Wrapping a
  key and encrypting the text with AES-GCM is the same construction TLS and PGP use, and it keeps
  the message authenticated (tampering with either half fails).
* OAEP uses SHA-256 with MGF1 as the platform provider implements it (the OpenSSL/Conscrypt
  default); Text Hub never re-implements the padding.
* A payload knows its wrapped-key length, so the tool can report which RSA key size it was made
  for. The key itself is never stored.
* Only PKCS#8 (`BEGIN PRIVATE KEY`) and X.509 (`BEGIN PUBLIC KEY`) blocks are accepted. Older
  PKCS#1 blocks are detected and reported with a clear message instead of a cryptic failure.

"""

patch(
    "docs/ENCRYPTION_FORMAT.md",
    [
        ("## 7. Versioning rules for future implementations",
         payloads + "## 7. Versioning rules for future implementations"),
        ("| Cipher | AES-128 / AES-192 / AES-256 in GCM mode (`AES/GCM/NoPadding`); AES-256 is the default |",
         "| Cipher | AES-128 / AES-192 / AES-256 in GCM mode (`AES/GCM/NoPadding`); AES-256 is the default |\n"
         "| Other payloads | AES-CBC+HMAC `0x02`, ChaCha20-Poly1305 `0x03`, AES-CTR+HMAC `0x04`, raw-key AES-GCM `0x05`, RSA-OAEP hybrid `0x11` (see §6) |"),
        ("* The `kdfId` byte is where KDF parameters live.",
         "* The dedicated AES-128/192/256 tools write the **same** payloads as the general tools; they only\n"
         "  lock the key size they accept on decrypt. Nothing about the format changes.\n"
         "* The `kdfId` byte is where KDF parameters live."),
    ],
)

# -------------------------------------------------------------------- README.md
secure_rows = """| AES-128-GCM / AES-192-GCM / AES-256-GCM | Encrypt / Decrypt | Password | **Authenticated encryption** — key size pinned per tool |
| AES-128-CBC + HMAC / 192 / 256 | Encrypt / Decrypt | Password | **Authenticated encryption** — key size pinned per tool |
| AES-CTR + HMAC | Encrypt / Decrypt | Password + key size | **Authenticated encryption** — CTR is authenticated here |
| AES-GCM with your own key | Encrypt / Decrypt | Raw key (Base64 or hex) | **Authenticated encryption** — no KDF, key used as pasted |
| RSA-OAEP + AES-GCM | Encrypt / Decrypt | RSA key pair (PEM) | **Authenticated encryption** — hybrid, any text length |
| RSA Key Pair Generator | Generate | – | Key material (not encryption) |"""

patch(
    "README.md",
    [
        ("**A tiny, extremely polished Swiss-army knife for text — 69 encoding, cipher, hash and encryption tools in one offline Android app.**",
         "**A tiny, extremely polished Swiss-army knife for text — 79 encoding, cipher, hash and encryption tools in one offline Android app.**"),
        ("69 tools, grouped exactly as they appear in the app.",
         "79 tools, grouped exactly as they appear in the app."),
        ("| ChaCha20-Poly1305 | Encrypt / Decrypt | Password | **Authenticated encryption** (Android 9+) |",
         "| ChaCha20-Poly1305 | Encrypt / Decrypt | Password | **Authenticated encryption** (Android 9+) |\n" + secure_rows),
        ("| Themes | System / Dark / AMOLED (true black) / Light, one restrained teal accent |",
         "| Themes | System / Dark / AMOLED (true black) / Light, plus a choice of eight accent colours |"),
        ("`./gradlew :core:test` runs **231 unit tests** (`core/src/test/kotlin/com/texthub/core/`):",
         "`./gradlew :core:test` runs **246 unit tests** (`core/src/test/kotlin/com/texthub/core/`):"),
        ("| `NewTools2Test` (round 3) |",
         "| `SecureToolsTest` (round 4) | Fixed-key-size AES-GCM and CBC tools (round trip, cross-size refusal, tag verification), AES-CTR (all key sizes, tamper, wrong password, non-determinism), raw-key AES-GCM (Base64 and hex keys, wrong length, wrong value), RSA hybrid (2048-bit round trip, 2 kB of text, wrong private key, tampered payload, PEM kind mistakes, PKCS#1 message), key generation (usable PEM pair, fresh keys, fingerprint) |\n| `NewTools2Test` (round 3) |"),
        ("""* The pre-built APKs from this repository are in [`apk/`](apk/):
  `TextHub-1.3.0-release.apk` (signed, 9.5 MB, md5 `29566bd297bc9279cff47cb1b9f82d8d`) and
  `TextHub-1.3.0-debug.apk` (14.3 MB, md5 `176bead5253acfaf2f38c82d72f85ad0`). Both declare no
  permissions other than Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.""",
         """* The pre-built APKs from this repository are in [`apk/`](apk/):
  `TextHub-1.4.0-release.apk` (signed, 9,523,848 B, md5 `4a1bd7b3ec8744c07df3293b8d8c5ac1`) and
  `TextHub-1.4.0-debug.apk` (14,300,004 B, md5 `1aa6e3caf59603c708fc01e86fd4fcad`). Both declare no
  permissions other than Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`."""),
        ("│   ├── processors/              one class per algorithm (69 of them)",
         "│   ├── processors/              one class per algorithm (79 of them)"),
    ],
)

# ---------------------------------------------------------------- PRIVACY.md
patch(
    "docs/PRIVACY.md",
    [("## Round 3 additions (version 1.3.0)",
      """## Round 4 additions (version 1.4.0)

* The dedicated AES tools, AES-CTR, raw-key AES-GCM, the RSA hybrid and the RSA key generator add
  no new data handling: everything runs in the same local core library, with no network code and
  no `INTERNET` permission.
* A pasted raw AES key or RSA private key is marked sensitive like a password: it is never written
  to preferences, never logged and never leaves the process.
* The key generator writes its output to the result box only. Nothing is saved to storage, the
  clipboard or a file unless you copy it yourself.
* The accent colour is a single string (`teal`, `blue`, …) in the same preference file as the
  theme. It contains no information about your text.

## Round 3 additions (version 1.3.0)""")],
)

# ------------------------------------------------------------ QA_CHECKLIST.md
patch(
    "docs/QA_CHECKLIST.md",
    [("## Round 3 checks (version 1.3.0)",
      """## Round 4 checks (version 1.4.0)

| [x] | `:core:test` → **246 tests, 0 failures** (round 1: 148, round 2: 185, round 3: 231) |
| [x] | Dedicated AES-128/192/256 tools: round trip per tool, cross-size payloads refused, general tools still read every size (key-size detection unchanged) |
| [x] | AES-CTR + HMAC: round trip at 128/192/256, wrong password fails, one flipped byte fails the tag, two runs of the same text differ (random counter block) |
| [x] | Raw-key AES-GCM: Base64 and hex keys agree, wrong key value fails, wrong key length reports a key problem, payload header carries the key *length* only |
| [x] | RSA-OAEP hybrid: 2048-bit pair generated on device encrypts and decrypts (including ~2 kB of text), a different private key fails, tampering fails, PEM/PKCS#1 mistakes produce specific messages |
| [x] | Key generator: output contains both PEM blocks, the pair works with the RSA tool, two runs differ, the class is `KEY_MATERIAL` (not secure) and needs no input text |
| [x] | Only the twelve real encryption tools claim the secure flag (test-enforced); every one of them requires a sensitive password or key parameter |
| [x] | Accent colour: eight options in Settings, only primary/secondary roles are recoloured, neutrals (including AMOLED black) are untouched, the choice persists across restarts |
| [x] | Long PEM keys are edited in a multi-line monospace field instead of one endless line |
| [x] | Launcher icon replaced: adaptive vector foreground/background plus a monochrome layer for themed icons, and regenerated PNGs for API 24–25 in all five densities |
| [x] | About screen shows Developer: **Catzilla** |
| [x] | Release APK `apk/TextHub-1.4.0-release.apk` (9,523,848 B, md5 `4a1bd7b3ec8744c07df3293b8d8c5ac1`) — `apksigner verify` passes, `aapt2 dump permissions` shows no `INTERNET` |
| [x] | Debug APK `apk/TextHub-1.4.0-debug.apk` (14,300,004 B, md5 `1aa6e3caf59603c708fc01e86fd4fcad`); both report `versionName 1.4.0`, `versionCode 4`, `minSdk 24`, `targetSdk 34` |
| [ ] | On-device pass (no emulator in this environment): open the tool picker, switch accents, generate a key pair and check the icon on the home screen |

## Round 3 checks (version 1.3.0)""")],
)

print("round 4 docs done")
