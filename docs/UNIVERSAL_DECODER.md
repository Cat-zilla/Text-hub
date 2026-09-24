# The Universal Decoder

*Text Hub 1.6.0, tool id `universal`, category **Smart Tools**.*

Paste something you did not encode yourself and Text Hub says what it recognises, how sure it is,
and what it would take to open it. Anything it can decode reliably is decoded for you — by the
ordinary tool for that format, with that tool's own validation and its own error messages.

> **It identifies and processes supported Text Hub formats where the format can be determined
> reliably.** It is not a "decrypt anything" button: it cannot open arbitrary unknown encrypted
> data, it never guesses or brute-forces a password or a key, and it never presents random bytes as
> a result.

---

## 1. How to use it

1. Pick **Universal Decoder** — it is the first entry in the tool list (see §8).
2. Paste the value and let the analysis run (or tap **Analyze** when automatic processing is off).
3. Read the **Analysis** card: what was recognised, the confidence, the reason, the chain of layers
   that were unwrapped, and — for an encrypted payload — exactly which secret is missing.
4. If a password or key is needed, type it in **Password / key** and the payload is opened once, by
   the tool that owns that format. Nothing is tried, nothing is remembered.
5. **Analyse result again** feeds the result back in as the input, which is how a nested payload
   (Base64 inside Base64 inside an encrypted message) is peeled layer by layer.

The result field holds the last decoded text, so **Copy**, **Share** and the usual result actions
work exactly as they do for every other tool.

---

## 2. How detection is organised: a matrix over the registry

There is **no list of tools inside the decoder**, and no `if tool == Base64` anywhere. `DetectionIndex`
builds its probe list from `ToolRegistry.all`, so every registered tool takes part - and a tool added
in a later version takes part immediately, without touching the decoder.

Each tool declares how its own input looks, in its own metadata: an alphabet, markers it must
contain, a prefix or suffix, a minimum length or a length rule, and - where the format carries them -
what the payload itself records (the envelope version, the key size, the JWE algorithm). A tool that
declares nothing is still considered: deterministic tools are probed generically, by decoding the
input and re-encoding the result, and the probe is accepted only if the round trip comes back and the
result is readable text.

Every candidate is then **validated by running that tool** through the production engine. A hint that
matched but whose tool refuses the input, or whose output is not readable text, produces no candidate
at all - nothing is reported that was not validated with the implementation that will do the work.

`DetectionIndex.accountedFor()` reports how each of the 74 registered tools takes part - *hint*,
*generic*, *symmetric* (a reversible transformation such as ROT13 or Atbash, which reads readable
text either way and therefore can never be evidence: it stays available through the manual override),
*keyed*, *one-way*, or *dispatcher* - and a test asserts that no deterministic tool can fall outside
that matrix unless it declares itself symmetric.

`"No supported format recognised"` is a complete answer. The decoder never guesses.

## 2.1 What it detects (stage 1: deterministic structure)

Structure that cannot be a coincidence: magic headers, version bytes, prefixes, delimiters,
self-describing envelopes, valid PEM blocks, JWE compact form, block sizes and character sets that
are exact. Only this stage can produce **high** confidence.

| Evidence | Recognised as | Handled by |
| --- | --- | --- |
| `0x01` version byte + valid envelope layout | AES-GCM payload (Text Hub) | AES-GCM Encryption (`aes`) |
| `0x02` version byte + 16-byte block size | AES-CBC + HMAC payload | AES-CBC + HMAC (`aescbc`) |
| `0x03` version byte | ChaCha20-Poly1305 payload | ChaCha20-Poly1305 (`chacha`) |
| `0x04` version byte | AES-CTR + HMAC payload | AES-CTR + HMAC (`aesctr`) |
| `0x05` version byte + raw-key header | AES-GCM with your own key | `aesrawkey` |
| `0x11` version byte | RSA-OAEP + AES-GCM hybrid | `rsa` |
| `-----BEGIN … PRIVATE KEY-----` | PEM private key (key material, not a message) | named, never decoded |
| `Salted__` + 16-byte block size | OpenSSL `enc` file (`-md`/`-iter` settings exposed) | `aescbc` (format: OpenSSL) |
| `eyJ…` five-part compact form | JWE (JSON Web Encryption) | `aesrawkey` for `alg=dir`, `rsa` for `RSA-OAEP*`, otherwise reported as unsupported with the algorithm named |
| `{ … }` that parses as JSON | JSON document | JSON Formatter (`json`) |
| `xn--` label | Internationalised domain label (punycode) | `punycode` |
| exact `\uXXXX` / `\xNN` escapes, `%XX` runs | Unicode escapes / URL encoding | `unicodeescape`, `url` |
| only `0`/`1` in whole bytes, Morse, Braille cells, Roman numerals | the matching encoding | `binary`, `morse`, `braille`, `roman` |

## 3. Candidate analysis (stage 2)

Whatever is left is a question of evidence, not of certainty, and the answer is a **list** of
candidates with a one-sentence reason each — never a coin flip.

Every candidate's description comes from the registry, not from the rule that found it. Whether it
needs a secret, **which** kind of secret it needs and which parameter carries it, whether it needs
cipher parameters, and whether the operation is one-way, are all derived from the registered tool's
own metadata. That is what makes it impossible for an encoding to ask for a password: a format whose
tool declares no key material simply has no secret to report, whatever matched it. It also means the
detection rules are only ever responsible for the evidence - never for what the operation requires. Base64 versus Base58 versus Base85,
hex versus a digest, UTF-16 units versus decimals: each is offered with the reason it fits, and
every candidate can be tapped to use it.

| Charset evidence | Candidates offered |
| --- | --- |
| `A–Z a–z 0–9 + / =` | Base64 (standard), Base64URL |
| `0–9 a–f` pairs, even length | Hexadecimal, and — with 32/40/64/128 characters — MD5 / SHA-1 / SHA-256 / SHA-512 as *possible* |
| Bitcoin / Ripple / Flickr alphabet | Base58 |
| `<~ ~>`, Z85 shaping | Base85 |
| `%` triplets | URL encoding |
| `&amp;`-style entities | HTML entities |
| `=XX` with 76-column wrapping | Quoted-Printable |
| dotted numbers, space-separated numbers | A1Z26, decimal/octal code points, UTF-16/UTF-32 units |

**A length or a character set is never presented as certainty.** A 64-character hexadecimal value
is reported as *Possible: SHA-256* with the sentence "64 hexadecimal characters — the length of a
SHA-256 digest — but a length alone proves nothing", and it is **not** decoded automatically.

## 4. Confidence

| Level | Meaning | Example |
| --- | --- | --- |
| **High** | Structural evidence: a magic byte, a version id, a self-describing envelope, a PEM header, a syntactically valid JWE. | Text Hub AES-GCM payload, `Salted__`, JWE |
| **Likely** | A strong, format-specific pattern. | Morse code, JSON, URL encoding, Base64 of a long value |
| **Possible** | Character-set or length evidence only. Never auto-decoded; offered as a candidate. | 64 hex → SHA-256, short `abc=` → Base64 |

## 5. Encryption: what it does with a key, and what it refuses to do

* **One secret, only when it is needed.** If the payload says it is encrypted, the analysis stops,
  names the format and the *kind* of secret it needs (password, raw key, RSA private key) and waits.
* **RSA key material is not a password (1.6.6).** When the detected format needs an RSA key, the
  key field becomes the multi-line PEM editor: the complete `-----BEGIN PRIVATE KEY-----` block is
  pasted whole (line breaks preserved, no length cap, nothing trimmed or re-wrapped) and reaches the
  existing RSA tool unchanged - the same `RsaPem` parser, the same formats (PKCS#8 private, X.509
  public). The label says which key the operation needs (*RSA private key* for decryption). *Clear*
  empties the key only; the ciphertext stays. *Use saved key* lists the named key pairs of the
  existing encrypted collection (name, size, fingerprint, date - never contents); choosing one
  decrypts exactly that record and fills the field with it, labelled "Using saved key: …". Editing
  the field afterwards makes it a manual key again; the saved record is never modified, and a
  pasted key is never saved. The decoder does not scan the collection and never tries more than
  the one key the user supplied.
* **Nothing is guessed, tried or brute-forced.** There is no password list, no dictionary, no
  "try the obvious ones", and no silent attempt with an empty password.
* **Parameters come from the payload, not from the user.** The key size recorded in a Text Hub
  payload (`kdfId` / version byte), the OpenSSL KDF and iteration count stored in the salt header,
  the JWE `alg` and `enc`, the RSA algorithm id and the raw-key length are all read from the data
  and passed to the owning tool. A message written at AES-128 is opened at AES-128; changing the
  setting cannot make it open at AES-256, and the refusal names the size the message needs.
* **A wrong key fails, it never returns garbage.** When the payload has an authentication tag, the
  tag decides: AES-GCM and ChaCha20-Poly1305 report "Decryption failed / Authentication check
  failed". CBC + HMAC verifies the HMAC over the ciphertext before decrypting and checks the
  padding; truncated payloads, bad envelope versions, impossible block sizes and malformed headers
  are refused with a sentence rather than a guess.
* **Old payloads stay readable.** 1.4.2-era payloads that could not record a 128-bit key size are
  read from their own tag, and the step note says the size came from the payload. The strict
  "enforce the setting on decrypt" rule (round 7) is unchanged for payloads that do record it.

## 6. Hashes are one-way, and stay one-way

A digest cannot be decrypted, and Text Hub never implies otherwise:

* the analysis result is `HashOnly`, not `Decoded`;
* the card and the report say "one-way digest — it can be compared with a text, never decrypted";
* the optional **Text to compare (hash)** field hashes your candidate with the same algorithm and
  reports whether it matches — the only operation a digest actually allows;
* the length of the digest is treated as *possible* evidence (32/40/64/128 hex characters suggest
  MD5/SHA-1/SHA-256/SHA-512), never as proof.

## 7. Nested payloads, chains and limits

* Layers are unwrapped one at a time and each is listed in the chain with the tool that did it:
  `1. URL encoding (url) — %XX triplets` → `2. Base64 (base64, URL-safe) — …`.
* Maximum layers: **4** by default, adjustable 1–8 under *Additional settings*.
* Cycles are detected: an encoding whose output is its own input (`TQ==` and friends) stops the
  chain instead of looping, and the depth limit ends anything longer.
* A layer that needs a secret stops the chain *there*: Text Hub never applies a cipher without the
  key that belongs to it.

## 8. Ordering: why it is always first

* The Universal Decoder is registered with a permanent **top-priority** flag, and the registry
  sorts on it. It is first in the tool list, in search results, in the category list and after a
  restart, after clearing temporary data, after favouriting something and after any reordering.
* It is an ordinary registered tool with an id, a name, a glyph, a description, search keywords and
  a category — not an item drawn on top of the list by the UI.
* **It cannot be moved by the user.** Its position is not stored anywhere a user action can reach.
* **Favourites are a second, independent order.** The Universal Decoder can be favourited like any
  other tool, and inside *Favourites* it can be dragged anywhere; that changes the favourites order
  only and never its position in the main list.

## 9. Limits — what it deliberately does not do

* It does not recognise formats Text Hub has no tool for. An unknown blob gets
  "No supported format was recognised" — not a guess.
* It does not break ciphers. Classical ciphers can be *identified* (they are character-set
  patterns), but nothing here recovers a key by cryptanalysis; the separate **Caesar Brute Force**
  tool remains the only brute-force helper, and it only covers 25 shifts.
* It does not verify signatures, and it says so: JWT payloads are decoded, never trusted.
* It does not check passwords against anything, does not keep them, and does not write them,
  the input or the output to disk or to any log.
* Very large inputs are analysed off the main thread (the same 20 000-character threshold the rest
  of the app uses), so the UI never freezes.

## 10. Tests

`core/src/test/kotlin/com/texthub/core/UniversalDecoderTest.kt` covers the whole contract:

* registration, pinning and the absence of any cryptography of its own (it must not claim to be
  secure, and it must not be classified as encryption);
* every deterministic format decodes through its own tool, and every Text Hub envelope is
  recognised (AES-GCM, AES-CBC+HMAC, ChaCha20, AES-CTR+HMAC, raw-key GCM, RSA hybrid, OpenSSL,
  JWE `dir` and `RSA-OAEP`);
* wrong password, truncated payload, invalid envelope, wrong key size and authentication failure all
  fail with a friendly sentence and never return output;
* digests are reported as one-way, ambiguity is offered as candidates, nested chains are unwrapped
  with cycle and depth protection;
* empty, random, malformed and oversized inputs; large input off the UI thread;
* no secret, no plaintext and no password ever appears in a diagnosis, a report or a step note.

`core/src/test/kotlin/com/texthub/core/UniversalDecoderDispatchTest.kt` (added in 1.6.1) checks the
same promises on the path the app actually takes - the registered tool, the registry and the
processing engine - rather than through the detector's internals:

* encoding "Hello TextHub" and analysing it decodes it, and the report mentions no cipher setting and
  no password;
* an encoding is never asked for a secret, and a typed password cannot change a decoding;
* no input in the corpus makes any decoding path report a cipher-settings problem, and the decoder
  itself never fails with an unexpected error;
* parameter torture over every encoding, transformation and classical cipher: an awkward parameter
  must produce a sentence that explains it, never an unexplained failure;
* the registry matrix: every registered tool is accounted for, a candidate can only name a registered
  tool, the dispatcher can never be a candidate, and forcing a tool never blames the cipher settings;
* the manual override is session-only and never written to disk.

### The message that no longer exists

1.6.0 could report **"These cipher settings are not valid. Please check the supplied key and
parameters."** in front of someone who had pasted an encoded string. The cause was not the wording:
an *unforeseen* error anywhere in a processor was reported as a problem with the cipher settings, so
a decoding defect surfaced as a complaint about a key the user had never typed. In 1.6.1 the generic
message is removed from the codebase entirely, every cipher failure names what is actually wrong
(a key that needs letters, a text whose length is not a multiple of the block size, a key size that
does not match the payload), and an unforeseen error is reported as exactly that - while the fuzzing
tests in `UniversalDecoderDispatchTest` make sure there are none left in the first place.

---

*See also:* [TOOLS.md](TOOLS.md) for the tool reference, [ENCRYPTION_FORMAT.md](ENCRYPTION_FORMAT.md)
for the exact payload formats, [PRIVACY.md](PRIVACY.md) for what is (and is never) stored.
