# Text Hub encryption payload format

Tool: **AES-GCM Encryption** (`AesProcessor`, `com.texthub.core.crypto.AesGcmPayload`)

This document is the compatibility contract: any future version of Text Hub must keep
decrypting payloads produced by this version, and must not silently change the meaning of the
existing header fields.

## 1. Summary

| Property | Value |
| --- | --- |
| Cipher | AES-128 / AES-192 / AES-256 in GCM mode (`AES/GCM/NoPadding`); AES-256 is the default |
| Other payloads | AES-CBC+HMAC `0x02`, ChaCha20-Poly1305 `0x03`, AES-CTR+HMAC `0x04`, raw-key AES-GCM `0x05`, RSA-OAEP hybrid `0x11` (see §6) |
| Tag length | 128 bits (16 bytes), included in the ciphertext field |
| Key derivation | PBKDF2 with HMAC-SHA256, 210 000 iterations; output length is the selected AES key size (16 / 24 / 32 bytes) |
| Salt | 16 bytes, `java.security.SecureRandom`, fresh per message |
| IV / nonce | 12 bytes, `java.security.SecureRandom`, fresh per message |
| Outer encoding | Base64 (standard alphabet, padded) |
| Provider | Platform JCE provider (Conscrypt on Android) — no custom crypto code |

## 2. Byte layout (before Base64)

| Offset | Size | Field | Value |
| --- | --- | --- | --- |
| 0 | 1 | `version` | `0x01` |
| 1 | 1 | `kdfId` | `0x02` = PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit key · `0x01` = legacy, key size not recorded (see §3.1) |
| 2 | 16 | `salt` | random |
| 18 | 12 | `iv` | random nonce for GCM |
| 30 | n | `ciphertext` | ciphertext followed by the 16-byte GCM authentication tag |

Total minimum length: `30 + 16 = 46` bytes (i.e. an empty message still produces a 46-byte
payload).

A concrete example produced by this implementation (`PAYLOAD_SIZE` assertions in
`AesGcmTest.payloadStructureIsDocumented`):

```
version = 01        kdfId = 01
salt    = 3f 9a ... (16 bytes)
iv      = b1 04 ... (12 bytes)
ciphertext + tag    (plaintext length + 16 bytes)
```

## 3. Algorithms

**Derivation**

```
key = PBKDF2-HMAC-SHA256(password = UTF-8(password), salt, 210000, 32 bytes)
```

`Pbkdf2` implements RFC 8018 directly on top of `javax.crypto.Mac("HmacSHA256")`. The reason is
portability: Android only guarantees `SecretKeyFactory("PBKDF2WithHmacSHA256")` from newer API
levels, while HMAC-SHA256 is available everywhere. A unit test cross-checks the output against
`SecretKeyFactory` to guarantee the two agree.

**Encryption**

```
cipher = AES/GCM/NoPadding
cipher.init(ENCRYPT_MODE, key = SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
ciphertext = cipher.doFinal(UTF-8(plaintext))
```

**Decryption**

```
cipher.init(DECRYPT_MODE, key, GCMParameterSpec(128, iv))
plaintext = UTF-8(cipher.doFinal(ciphertext))
```

Any `GeneralSecurityException` (wrong password, modified ciphertext, modified IV, truncated
payload) is reported as: *"Unable to decrypt. The password or encrypted data may be incorrect."*
Deliberately identical for all failure modes, so the error does not leak which part failed.

### 3.1 Key size (added in 1.3.0, made explicit in 1.4.2)

The key-size setting (AES-128, AES-192, AES-256) selects how many PBKDF2 bytes are used as the AES
key:

```
key = PBKDF2-HMAC-SHA256(password, salt, 210000, 16 | 24 | 32 bytes)
```

**The size is recorded in the `kdfId` byte**, which is what that field was reserved for. The
payload layout is unchanged — same version byte, same offsets:

| `kdfId` | Meaning |
| --- | --- |
| `0x01` | PBKDF2-HMAC-SHA256, 210 000 iterations — *legacy*: the key size was not recorded (written by 1.3.0–1.4.1) |
| `0x02` | PBKDF2-HMAC-SHA256, 210 000 iterations, **256-bit key** (written by 1.4.2 and later) |

Rules the implementation follows:

* **Decrypting uses exactly the recorded size.** It does not "try every key size" any more: if the
  KDF id says 256-bit, one key is derived and either the tag authenticates or the operation fails.
* **The Key size setting must match the message.** If it does not, the operation is refused with a
  message naming the size the message actually needs (*"This message was encrypted with a 128-bit
  AES key, but the key size selected here is different…"*). This is the behaviour users expect:
  changing the setting must not silently succeed.
* **Legacy payloads (`kdfId = 0x01`) stay readable.** They recorded nothing, so the size is
  detected from the authentication tag (256 first). If detection finds a size other than the
  selected setting, the same "select size X" message is shown instead of quietly decrypting — the
  user is told what to set, so no payload becomes unreadable.
* `keySizeOf()` returns the size a payload needs (recorded, or detected for legacy payloads).
* The same rule applies to AES-CBC + HMAC (§6.1) and AES-CTR + HMAC (§6.3), where the PBKDF2 output
  is `keyLength + 32` bytes: the AES key first, then an independent 32-byte MAC key.
* Payloads written *by this document's earlier builds* (1.4.0/1.4.1, including the short-lived
  AES-128/192/256 tools) are `kdfId = 0x01` and therefore still decrypt — but the app will tell the
  user which key size to select when the setting is wrong, rather than guessing silently.

## 4. Validation rules on decrypt

1. Base64 must decode (otherwise *"This does not look like Text Hub encrypted data…"*).
2. Length must be at least 46 bytes.
3. `version` must equal `0x01`.
4. `kdfId` must equal `0x01`.
5. The GCM tag must verify.

## 5. Properties guaranteed by tests (for AES-GCM and the two payloads in section 6)

* A payload written with one key size is refused by the other sizes with a message naming the
  correct one (AES-GCM, AES-CBC + HMAC and AES-CTR + HMAC are all covered).
* Legacy payloads (`kdfId = 0x01`) still decrypt, and report the size they actually use.
* Encrypting the same plaintext twice with the same password produces **different** payloads
  (different salt *and* different IV), and both decrypt correctly.
* Decryption with a wrong password fails.
* Flipping one bit of the ciphertext fails authentication.
* Flipping one bit of the IV fails authentication.
* Truncating the payload fails.
* Tampering with any ciphertext byte of the AES-CBC payload fails, because the HMAC tag covers the
  ciphertext.
* The three tools are the only ones classified *Authenticated encryption*; a registry test asserts
  that no other tool can claim it.
* Empty plaintext is rejected on the encrypt side (nothing to protect), Unicode plaintext
  round trips, and long text (several kB) round trips.

## 6. The two other authenticated payloads

Both use the same PBKDF2-HMAC-SHA256 (210 000 iterations, 16-byte random salt) key derivation and
the same Base64 transport as AES-GCM; only the construction after the salt differs.

### 6.1 AES-CBC + HMAC (tool id `aescbc`, `version = 0x02`)

```
byte 0        version        0x02
byte 1        kdfId          0x01  (PBKDF2-HMAC-SHA256, 210 000 iterations, key length + 32 bytes)
bytes 2..17   salt           16 random bytes
bytes 18..33  IV             16 random bytes
bytes 34..n   ciphertext     AES-256-CBC with PKCS#5 padding on the UTF-8 plaintext
bytes n..n+32 HMAC-SHA256    tag over every preceding byte
```

* PBKDF2 output is **split**: the first `keyLength` bytes (16, 24 or 32) are the AES key, the
  following 32 bytes are the HMAC key. The two purposes never share key material.
* This is **Encrypt-then-MAC**: the tag is verified with a constant-time comparison
  (`MessageDigest.isEqual`) *before* any decryption, so padding-oracle style attacks do not apply.
* The tag is not truncated: 32 bytes, checked in full.
* Minimum payload length: 2 + 16 + 16 + 16 + 32 = 82 bytes.

### 6.2 ChaCha20-Poly1305 (tool id `chacha`, `version = 0x03`)

```
byte 0        version        0x03
byte 1        kdfId          0x01  (PBKDF2-HMAC-SHA256, 210 000 iterations, 32 bytes)
bytes 2..17   salt           16 random bytes
bytes 18..29  nonce          12 random bytes
bytes 30..n   ciphertext     ChaCha20 stream ciphertext followed by the 16-byte Poly1305 tag
```

* The provider is the platform's `ChaCha20-Poly1305` (RFC 8439) implementation; Text Hub never
  implements the cipher itself.
* Android 9 (API 28) and newer provide it. On older devices the tool reports that clearly and
  points at AES-GCM instead of silently falling back to a weaker scheme.
* Minimum payload length: 2 + 16 + 12 + 16 = 46 bytes.

### 6.3 AES-CTR + HMAC (tool id `aesctr`, `version = 0x04`)

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

## 7. Versioning rules for future implementations

* Never change the meaning of `version = 0x01`.
* A new construction gets a new `version` byte (or a new `kdfId`), and the decoder keeps
  supporting the old values.
* Earlier 1.4.0 builds also shipped AES-128/192/256 tools that pinned the key size; they wrote the
  same payloads as the general tools and were removed in 1.4.1 for exactly that reason, so no
  payload written by them became unreadable. The key size stays a parameter of the two AES tools.
* The `kdfId` byte is where KDF parameters live. If the iteration count changes, add a new
  `kdfId` value; do not change the count behind `0x01`.
* Never reuse an (key, IV) pair: always draw a fresh 12-byte IV per message.
* The key size lives in the `kdfId` byte, never in a new field: `0x02` means "256-bit key" and any
  future size gets its own value (for example `0x03` for a 128-bit key if the app ever needs to
  write one).
* Never store, transmit or log the password, the derived key, or the plaintext.

## 8. What Text Hub will never do

* No "encryption" that is really character shifting or XOR presented as secure.
* No hard-coded or embedded keys.
* No recovery mechanism for lost passwords.
* No claim that the scheme is unbreakable.
