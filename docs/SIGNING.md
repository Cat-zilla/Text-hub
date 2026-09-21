# Signing keys and publishing updates

This project ships with its **own** release keystore so that you can rebuild the app at any time
and publish updates over the existing installation. Everything you need to keep updating Text Hub
with the same key is in this file and in the archive it came with.

---

## 1. What is included

| File | What it is |
| --- | --- |
| `texthub-release.jks` | The release keystore (JKS): the private key that signs release APKs |
| `app/build.gradle.kts` | Already points at this keystore (`signingConfigs.release`) |
| `apk/TextHub-1.4.1-release.apk` | The release APK signed with this key |
| `apk/TextHub-1.4.1-debug.apk` | Debug APK (signed with the throwaway debug key — do not distribute) |
| `docs/SIGNING.md` | This document |

**Keystore details**

| Property | Value |
| --- | --- |
| File | `texthub-release.jks` |
| Type | JKS |
| Store password | `texthub` |
| Key alias | `texthub` |
| Key password | `texthub` |
| Key algorithm | RSA 2048-bit, `SHA256withRSA` |
| Valid from | 2026-09-21 |
| Valid until | 2056-09-13 (≈30 years) |
| Certificate SHA-256 | `CC:69:D4:D0:50:EC:FA:9F:7A:94:B9:6E:9D:EE:30:5F:C4:4B:7D:B8:CA:17:BB:E1:B1:93:CD:B4:18:29:82:B1` |
| Certificate SHA-1 | `B6:F0:DF:6C:A8:32:29:50:4C:5F:DE:B0:69:4A:B3:16:B2:71:E9:5B` |
| Application ID | `com.texthub.app` |

Check it yourself at any time:

```bash
keytool -list -v -keystore texthub-release.jks -storepass texthub
apksigner verify --print-certs apk/TextHub-1.4.1-release.apk
```

Both must print the SHA-256 fingerprint above. Android only accepts an update if the new APK is
signed with **the same key and the same certificate** and carries the same `applicationId`.

---

## 2. Back this keystore up before you publish anything

A keystore is a single file (2.7 kB) that cannot be regenerated. If you publish the app and then
lose it, **no one — including you — can ship an update to that installation**; the only option left
is publishing a new app under a new application ID, and existing users have to reinstall.

Keep at least two copies in different places, for example:

1. A password manager entry (1Password, Bitwarden, KeePassXC) — store the file and the passwords.
2. A private, encrypted archive on external storage or an external drive.
3. Optionally an encrypted cloud folder (Never a public GitHub repo, never a shared drive link.)

A safe text form for a password-manager note is the keystore encoded as Base64:

```bash
base64 -w0 texthub-release.jks > texthub-release.jks.base64     # paste into your vault
base64 -d texthub-release.jks.base64 > texthub-release.jks      # restore anywhere
```

> **This archive contains the keystore with the passwords documented above.** Anyone who has this
> zip can sign APKs that Android treats as updates to your app. Do not upload it to a public
> repository or file-sharing site, and do not attach it to a support ticket.

If you would rather start with your own key, generate a fresh one and update
`app/build.gradle.kts` before your first public release (see §5). Changing keys is only free
*before* the first install in the wild — after that, the key above is the one that must be used.

---

## 3. Building a signed update

The signing configuration is already wired up, so a release build is one command:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew :core:test              # all 246 tests must pass first
./gradlew :app:assembleRelease    # -> app/build/outputs/apk/release/app-release.apk
```

Verify what you are about to ship:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -3
```

You should see the version you intended, `package: name='com.texthub.app'` and the certificate
fingerprint from §1.

### The two rules for every update

1. **Raise `versionCode`** in `app/build.gradle.kts` by at least one (Android refuses to install an
   APK whose `versionCode` is not higher than the installed one).
2. **Never change `applicationId`** (`com.texthub.app`) and never change the signing key.

`versionName` is the human-readable label and can be anything; bump it together with the code, for
example:

```kotlin
versionCode = 5          // was 4
versionName = "1.5.0"
```

Then rebuild and install over the old version:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Because the key and the package name match, Android treats it as an update: user data,
preferences and favourites survive, and the app is replaced in place.

---

## 4. Signing manually (optional)

Gradle does this for you, but if you ever need to sign an APK by hand:

```bash
# zipalign first (fine to re-run on an already aligned APK)
zipalign -p -f 4 app-release-unsigned.apk app-release-aligned.apk

# then sign
apksigner sign \
  --ks texthub-release.jks \
  --ks-pass pass:texthub \
  --ks-key-alias texthub \
  --key-pass pass:texthub \
  --out app-release-signed.apk app-release-aligned.apk

apksigner verify --print-certs app-release-signed.apk
```

The tools live in `$ANDROID_HOME/build-tools/34.0.0/` (`apksigner`, `zipalign`, `aapt2`).

---

## 5. Using your own key instead

1. Create a new keystore:

   ```bash
   keytool -genkeypair -v -keystore my-release.jks -alias mykey \
     -keyalg RSA -keysize 4096 -validity 10000 \
     -dname "CN=Your Name, O=Your Org, C=IN"
   ```

2. Point Gradle at it in `app/build.gradle.kts`:

   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("../my-release.jks")
           storePassword = "…"
           keyAlias = "mykey"
           keyPassword = "…"
       }
   }
   ```

   Better still, keep the secrets out of the repository entirely and read them from
   `local.properties` or environment variables (and add `local.properties` / `*.jks` to
   `.gitignore` — the file already ignores `local.properties`).

3. Delete `texthub-release.jks` and this document's password section if the archive leaves your
   machine.

---

## 6. Distributing outside Google Play

- Users can install the signed APK directly (`adb install -r …`, or a file manager with
  *Install unknown apps* allowed). Updates install over the old version as long as the key and
  application ID are unchanged.
- If you later publish on Google Play, enrol in **Play App Signing**: Google keeps the app signing
  key and you keep an *upload key*. Keep this keystore safe either way — it is the upload key
  unless you deliberately reset it.
- The app requests no permissions beyond Android's own internal
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, and never uses the network, so nothing about signing
  affects its privacy behaviour.

---

## 7. Quick checklist

- [ ] `texthub-release.jks` backed up in at least two secure places (and the passwords too)
- [ ] `versionCode` increased for the new build
- [ ] `applicationId` still `com.texthub.app`
- [ ] `./gradlew :core:test` green
- [ ] `apksigner verify --print-certs` shows `CC:69:D4:…:82:B1`
- [ ] Old APK kept somewhere, so you can install last-known-good if a build misbehaves
