# Privacy behaviour

**Short version: your text is processed locally on this device.** Nothing you type is sent
anywhere, because the app has no ability to send anything.

## Permissions

`app/src/main/AndroidManifest.xml` declares **no permissions**. The template `INTERNET`
permission is explicitly removed:

```xml
<uses-permission
    android:name="android.permission.INTERNET"
    tools:node="remove" />
```

Verified on both built APKs (`aapt dump permissions` reports only the
auto-generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that AndroidX adds for
internal broadcast receivers — it is not a network or data permission).

## The Universal Decoder (1.6.0)

The analysis card that Text Hub shows for the Universal Decoder is derived from the text you pasted
and lives in memory exactly as long as that text does. Nothing about it is written to disk:

* the diagnosis (detected format, confidence, reason, chain of layers) exists only while the input
  is on screen and is discarded when the input is cleared, the tool changes or the app closes;
* the manual override is an ordinary, non-secret parameter (`prefer`) with the same lifetime as any
  other remembered setting — clearing temporary data removes it;
* a password or key typed into the decoder is handled like every other secret: never persisted,
  never logged, never included in the report or in any step note (`UniversalDecoderTest` asserts
  that no diagnosis text ever contains the supplied secret or the recovered plaintext);
* no message, no input and no result is sent anywhere: the decoder dispatches to the tools that are
  already on the device.

## Network code

None. The project contains no `HttpURLConnection`, `URLConnection`, OkHttp, Retrofit, WebView or
socket usage. `grep -rn "Http\|WebView\|OkHttp\|Retrofit" core/src app/src` returns nothing.
The app is fully functional in airplane mode.

## Analytics, ads, trackers

None. No SDK of any kind beyond AndroidX/Jetpack Compose is included, and the app itself logs
nothing: there are no `android.util.Log`, `println` or `printStackTrace` calls in the codebase.

## What is stored on the device

`SharedPreferences` file `texthub_preferences.xml`:

| Key | Example value | Sensitive? |
| --- | --- | --- |
| `theme` | `amoled` | no |
| `auto_process` | `true` | no |
| `copy_confirmation` | `true` | no |
| `last_tool` | `base64` | no |
| `favorites` | `["base64","aes","hex"]` | no |
| `recents` | `base64\|hex\|aes` | no |
| `params_<toolId>` | `{"shift":"3","variant":"standard"}` | no — sensitive parameters are filtered out |

Not stored, ever: input text, output text, AES passwords, cipher keys, clipboard contents,
history of processed text.

Three values are deliberately **session-only**, so they are never written even to that file: a
sensitive parameter of any tool (`ToolMeta.persistable` filters it), and the Universal Decoder's
manual override, which is marked `sessionOnly` in its own metadata. Forcing a tool therefore applies
to the analysis in front of you and is gone when the app is closed - a stale override can neither
change a later result nor leave a record on the device.

The filter is in `HubViewModel.persistParams()`: parameters whose `ParamSpec.sensitive` flag is
`true` (all `key`/`password` fields) are removed before writing. A unit test enforces that every
key/password parameter in the registry is marked sensitive.

## Backups

`backup_rules.xml` / `data_extraction_rules.xml` include exactly one item — the preference file
above. Because it holds no text and no secrets, a device backup cannot leak user content.

## History

Text Hub has **no history feature**. There is no list of previously processed texts to clear,
because none is recorded. The settings screen states this explicitly under *Privacy*.

## Clearing data

*Settings → Privacy → Clear temporary data* deletes all `params_*` entries, favourites and
recents. Appearance settings survive. (Or uninstall the app / clear app data in Android
settings.)

## Password handling in memory

* Passwords are held as `CharArray` and zeroed with `fill('\u0000')` in a `finally` block after
  every encryption or decryption (`AesProcessor`).
* The derived AES key exists only as a local `SecretKeySpec` for the duration of one operation.
* No password, key or plaintext is included in any error message.
* The password field is rendered with `PasswordVisualTransformation`, and the operating system's
  keyboard is used as usual — Text Hub does not implement its own input handling.

## Round 6 change (version 1.4.2)

* The Key size setting on the AES tools is now enforced on decryption instead of being ignored, and
  the size is recorded inside the encrypted message (KDF id `0x02`). Nothing new is stored on the
  device: the payload format is unchanged apart from that one header byte, and no key or password is
  written anywhere.
* The tool audit added in this round reads and reformats text locally like every other test; it
  stores nothing and uses no network.

## Round 4 additions (version 1.4.0)

* The AES tools (with their key-size setting), AES-CTR, raw-key AES-GCM, the RSA hybrid and the RSA key generator add
  no new data handling: everything runs in the same local core library, with no network code and
  no `INTERNET` permission.
* A pasted raw AES key or RSA private key is marked sensitive like a password: it is never written
  to preferences, never logged and never leaves the process.
* The key generator writes its output to the result box only. Nothing is saved to storage, the
  clipboard or a file unless you copy it yourself.
* The accent colour is a single string (`teal`, `blue`, …) in the same preference file as the
  theme. It contains no information about your text.

## Round 3 additions (version 1.3.0)

The 23 tools added in 1.3.0 change nothing about data handling:

* Hashes, HMAC, PBKDF2, checksums, the Enigma machine, Porta, Trifid, Scytale, ADFGX/ADFGVX, Hill
  3×3, Base91, Punycode, Braille, Roman numerals, UTF-16/UTF-32 views, hex dump, JSON formatting,
  regex, text diff, statistics and the JWT inspector all run in the same local core library. There
  is still no network code and still no `INTERNET` permission.
* Passwords and keys typed into the new tools (HMAC key, PBKDF2 password, AES key size) are marked
  sensitive and are filtered out before preferences are written, exactly like the AES password.
* The JWT Inspector decodes a token that you paste. It never contacts a server, never verifies a
  signature and never stores the token.
* Nothing about a hash, an HMAC or a checksum is written to disk; only the non-secret parameter
  choices (algorithm, output format, mode) are remembered.

## Limits and honest caveats

* Text Hub is not audited software; it is a small, readable, dependency-light app.
* Anything already on your clipboard can be read by other apps on unpatched or malicious
  systems — that is an Android platform property, not something Text Hub can change.
* Screenshots and the recent-apps thumbnail can show your text. Text Hub does not set
  `FLAG_SECURE`, because that would also block the copy/paste workflow it is built around.

---

## Temporary data (1.5.0)

The only disposable data the app holds is:

* remembered tool settings (parameter values per tool id),
* the recent-tools list.

Settings shows the current size of that store in B / KB / MB and offers a single *Clear* action with
a confirmation that lists what will be removed and what will be kept. Everything else is
configuration the user chose and is **kept**: favourites and their order, theme, accent colour and
the currently selected tool. Text, passwords and keys are never stored in the first place, so
clearing cannot delete - or preserve - them; they exist only in memory for the duration of the
action.
