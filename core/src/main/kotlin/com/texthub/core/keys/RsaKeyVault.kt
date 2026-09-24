package com.texthub.core.keys

import com.texthub.core.crypto.RsaKeyGen

/**
 * The saved RSA key collection: named key pairs, encrypted at rest, entirely local.
 *
 * Two boundaries keep this testable and honest about what is secure:
 *
 *  * [VaultCipher] seals and opens the record payloads. The app provides an implementation backed
 *    by the **Android Keystore** (an AES-GCM key that never leaves the hardware keystore), so the
 *    encrypted bytes on disk cannot be opened by another app or another installation. The
 *    semantics of the collection do not depend on which implementation is behind the interface -
 *    that is what the unit tests pin, with a software cipher.
 *  * [VaultStorage] reads and writes the sealed file. The app stores it in the app's private
 *    files directory; it is not a preference, so neither "Clear temporary data" nor "Restore
 *    defaults" can ever touch it.
 *
 * What is secret here: both PEM halves of every saved pair travel only inside sealed blobs. The
 * listing metadata (name, key size, fingerprint, creation time) is not secret - it is what the
 * saved-key list shows without ever decrypting a private key.
 */
data class SavedRsaKeyMeta(
    /** The user's name for the pair. Unique in the collection after trimming. */
    val name: String,
    /** Key size in bits, as generated. */
    val bits: Int,
    /** Colon-separated SHA-256 fingerprint of the public key's DER encoding. */
    val fingerprint: String,
    /** When the pair was saved, as epoch milliseconds. */
    val createdAt: Long,
)

/** One stored record: its non-secret metadata plus the sealed payload of both PEM halves. */
data class StoredRsaKey(val meta: SavedRsaKeyMeta, val blob: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is StoredRsaKey && other.meta == meta && other.blob.contentEquals(blob)
    override fun hashCode(): Int = meta.hashCode() * 31 + blob.contentHashCode()
}

/** Seals and opens record payloads. The app's implementation uses the Android Keystore. */
interface VaultCipher {
    fun seal(plain: ByteArray): ByteArray
    fun unseal(blob: ByteArray): ByteArray
}

/** Reads and writes the sealed collection file (null when nothing has been saved yet). */
interface VaultStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

/** What one save attempt decided. */
sealed class SaveResult {
    /** The pair is stored under [name]; [replaced] is true when an existing record was replaced. */
    data class Saved(val name: String, val replaced: Boolean) : SaveResult()

    /** The name is not usable (blank). Trim first; the UI asks for a name. */
    data object InvalidName : SaveResult()

    /** A record with this name already exists - never silently overwritten. */
    data class NameConflict(val name: String) : SaveResult()

    /**
     * The same cryptographic key (identical public-key fingerprint) is already saved under
     * [name]: a second record would pretend two identical keys were different keys.
     */
    data class SameKeyExists(val name: String) : SaveResult()
}

/**
 * The named collection of saved RSA key pairs, over sealed storage.
 *
 * All operations are explicit and independent: saving never deletes, deleting never touches other
 * records, loading never generates. The collection is rebuilt from storage on construction, so a
 * newly constructed instance over the same storage is exactly the collection after an app restart.
 */
class RsaKeyCollection(
    private val storage: VaultStorage,
    private val cipher: VaultCipher,
) {

    private val records: MutableList<StoredRsaKey> = load()

    /** The non-secret listing metadata, newest first - what the saved-key list shows. */
    fun keys(): List<SavedRsaKeyMeta> = records
        .map { it.meta }
        .sortedByDescending { it.createdAt }

    fun contains(name: String): Boolean = records.any { it.meta.name == name }

    /**
     * Stores [pair] under [name]. The name is trimmed; a blank name is refused. A name that is
     * taken is never silently overwritten - the caller decides, with [replace] = true, to replace
     * it. A pair whose fingerprint is already saved under another name is reported as the same
     * key rather than being stored twice.
     */
    fun save(name: String, pair: RsaKeyGen.Generated, createdAt: Long, replace: Boolean = false): SaveResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveResult.InvalidName
        val sameKey = records.firstOrNull { it.meta.fingerprint == pair.fingerprint }
        if (sameKey != null) return SaveResult.SameKeyExists(sameKey.meta.name)
        val existing = records.firstOrNull { it.meta.name == trimmed }
        if (existing != null && !replace) return SaveResult.NameConflict(trimmed)
        val payload = (pair.publicPem + PAYLOAD_SEPARATOR + pair.privatePem).encodeToByteArray()
        val record = StoredRsaKey(
            meta = SavedRsaKeyMeta(trimmed, pair.bits, pair.fingerprint, createdAt),
            blob = cipher.seal(payload),
        )
        if (existing != null) records[records.indexOf(existing)] = record else records += record
        persist()
        return SaveResult.Saved(trimmed, replaced = existing != null)
    }

    /**
     * Opens the named pair. Returns null when no record carries the name. Only this call decrypts
     * this one pair - listing the collection never touches private material.
     */
    fun load(name: String): RsaKeyGen.Generated? {
        val record = records.firstOrNull { it.meta.name == name.trim() } ?: return null
        val payload = try {
            cipher.unseal(record.blob)
        } catch (e: Exception) {
            return null
        }
        val text = payload.decodeToString()
        val cut = text.indexOf(PAYLOAD_SEPARATOR)
        if (cut < 0) return null
        return RsaKeyGen.Generated(
            publicPem = text.substring(0, cut),
            privatePem = text.substring(cut + PAYLOAD_SEPARATOR.length),
            bits = record.meta.bits,
            fingerprint = record.meta.fingerprint,
        )
    }

    /**
     * Removes one record - only that one. True when something was removed. The caller decides
     * what happens to an active copy of the same pair (the session keeps it; see [RsaKeySession]).
     */
    fun delete(name: String): Boolean {
        val record = records.firstOrNull { it.meta.name == name.trim() } ?: return false
        records.remove(record)
        persist()
        return true
    }

    /**
     * "Clear saved RSA keys": removes every record. This is the one operation that empties the
     * collection, and it exists only behind the explicit, separately confirmed action in Settings -
     * neither "Restore app preferences" nor any other reset calls it. Safe on an already empty or
     * damaged collection; returns how many records were removed.
     */
    fun deleteAll(): Int {
        val count = records.size
        if (count == 0) return 0
        records.clear()
        persist()
        return count
    }

    /** The sealed records of the whole collection, for tests and for the security review. */
    fun storedBytes(): List<ByteArray> = records.map { it.blob }

    // ------------------------------------------------------------------ persistence

    /**
     * Writes the collection as one record per line. Every field is Base64, so a name with any
     * character is safe, and nothing but the non-secret listing metadata is readable: the sealed
     * payload exists only as its ciphertext. This file is the app's own opaque store, not a
     * document anyone edits.
     */
    private fun persist() {
        val encoder = java.util.Base64.getEncoder()
        val text = records.joinToString("\n") { record ->
            listOf(
                encoder.encodeToString(record.meta.name.encodeToByteArray()),
                record.meta.bits.toString(),
                record.meta.fingerprint,
                record.meta.createdAt.toString(),
                encoder.encodeToString(record.blob),
            ).joinToString(FIELD_SEPARATOR)
        }
        storage.write(text.encodeToByteArray())
    }

    private fun load(): MutableList<StoredRsaKey> {
        val bytes = storage.read() ?: return mutableListOf()
        val decoder = java.util.Base64.getDecoder()
        return try {
            bytes.decodeToString().split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
                val fields = line.split(FIELD_SEPARATOR)
                if (fields.size != 5) return@mapNotNull null
                StoredRsaKey(
                    meta = SavedRsaKeyMeta(
                        name = fields[0].decodeBase64(),
                        bits = fields[1].toIntOrNull() ?: return@mapNotNull null,
                        fingerprint = fields[2],
                        createdAt = fields[3].toLongOrNull() ?: return@mapNotNull null,
                    ),
                    blob = decoder.decode(fields[4]),
                )
            }.toMutableList()
        } catch (e: Exception) {
            // A damaged or unreadable store must not crash the app; it degrades to an empty
            // collection (and the next save rewrites a fresh file). Records are never logged.
            mutableListOf()
        }
    }

    companion object {
        /** Joins the two PEM halves inside a sealed payload (never present in a PEM body). */
        private const val PAYLOAD_SEPARATOR = "\n\n----\n\n"

        /** Separates the fields of one stored record. */
        private const val FIELD_SEPARATOR = "|"

        private fun String.decodeBase64(): String =
            java.util.Base64.getDecoder().decode(this).decodeToString()
    }
}

/**
 * The active pair's state, separate from the saved collection - as a pure value, so the session
 * rules can be unit tested without Android.
 *
 * *Generating* sets the active pair (unsaved). *Saving* names it. *Loading* replaces the active
 * pair with a saved one. *Clearing* removes it. *Deleting a saved record* only unlinks the active
 * copy if it was that record - the pair on screen survives, it simply becomes unsaved, which is
 * the honest state: its persisted half is gone.
 */
data class RsaKeySession(
    val active: RsaKeyGen.Generated? = null,
    /** The saved record the active pair came from, or null while it is unsaved. */
    val savedName: String? = null,
) {

    /** True when replacing or leaving the active pair loses user work (the confirmation cases). */
    val activeIsUnsaved: Boolean get() = active != null && savedName == null

    fun generate(pair: RsaKeyGen.Generated): RsaKeySession = RsaKeySession(active = pair, savedName = null)

    fun clear(): RsaKeySession = RsaKeySession(active = null, savedName = null)

    fun savedAs(name: String): RsaKeySession = copy(savedName = name.trim())

    fun loaded(pair: RsaKeyGen.Generated, name: String): RsaKeySession =
        RsaKeySession(active = pair, savedName = name.trim())

    /** Unlinks the active pair from a deleted record; other records cannot be the active one. */
    fun savedRecordDeleted(name: String): RsaKeySession =
        if (savedName == name.trim()) copy(savedName = null) else this
}
