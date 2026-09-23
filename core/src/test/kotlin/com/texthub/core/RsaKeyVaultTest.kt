package com.texthub.core

import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.keys.RsaKeyCollection
import com.texthub.core.keys.RsaKeySession
import com.texthub.core.keys.SaveResult
import com.texthub.core.keys.SavedRsaKeyMeta
import com.texthub.core.keys.VaultCipher
import com.texthub.core.keys.VaultStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * The saved RSA key collection and the active-pair session, on the JVM.
 *
 * The collection here is exercised with a software AES-GCM cipher and an in-memory storage - the
 * same [VaultCipher]/[VaultStorage] boundaries the app fills with an **Android Keystore** cipher
 * and a private file. The Keystore itself is hardware/platform behaviour and needs an
 * instrumentation test on a device; what these tests pin is everything around it: the record
 * rules (names, duplicates, same-key detection), the sealed-at-rest property, the independence of
 * the records, and the session semantics of the active pair.
 *
 * No assertion message carries key material: failures describe structure, never PEM contents.
 */
class RsaKeyVaultTest {

    /** Software stand-in for the app's AndroidKeystore cipher: AES-GCM with a fresh IV per seal. */
    private class SoftwareCipher : VaultCipher {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        override fun seal(plain: ByteArray): ByteArray {
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return iv + cipher.doFinal(plain)
        }

        override fun unseal(blob: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            return cipher.doFinal(blob.copyOfRange(12, blob.size))
        }
    }

    /** In-memory stand-in for the app's private file. */
    private class MemoryStorage : VaultStorage {
        var bytes: ByteArray? = null
        override fun read(): ByteArray? = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes }
    }

    private fun collection(storage: MemoryStorage = MemoryStorage()): RsaKeyCollection =
        RsaKeyCollection(storage, SoftwareCipher())

    private val pairA by lazy { RsaKeyGen.generatePair(2048) }
    private val pairB by lazy { RsaKeyGen.generatePair(2048) }

    // ------------------------------------------------------------------ saving and loading

    @Test
    fun aBlankNameIsRefused() {
        val keys = collection()
        assertEquals(SaveResult.InvalidName, keys.save("   ", pairA, createdAt = 1))
        assertTrue(keys.keys().isEmpty())
    }

    @Test
    fun aNameIsTrimmedBeforeItIsStored() {
        val keys = collection()
        assertEquals(SaveResult.Saved("Project Alpha", replaced = false), keys.save("  Project Alpha  ", pairA, 1))
        assertTrue(keys.contains("Project Alpha"))
        assertEquals("Project Alpha", keys.keys().single().name)
    }

    @Test
    fun saveThenLoadReturnsExactlyTheSamePair() {
        val keys = collection()
        keys.save("Website encryption", pairA, 1)
        val loaded = keys.load("Website encryption")
        assertNotNull(loaded)
        assertEquals(pairA, loaded)
    }

    @Test
    fun theFingerprintSurvivesSaveAndLoadUnchanged() {
        val keys = collection()
        keys.save("k", pairA, 1)
        assertEquals(pairA.fingerprint, keys.load("k")!!.fingerprint)
        assertEquals(pairA.fingerprint, keys.keys().single().fingerprint)
    }

    @Test
    fun multiplePairsAreStoredIndependently() {
        val keys = collection()
        keys.save("Alpha", pairA, 1)
        keys.save("Beta", pairB, 2)
        assertEquals(pairA, keys.load("Alpha"))
        assertEquals(pairB, keys.load("Beta"))
        assertEquals(setOf("Alpha", "Beta"), keys.keys().map { it.name }.toSet())
    }

    @Test
    fun aDuplicateNameIsNeverSilentlyOverwritten() {
        val keys = collection()
        keys.save("Alpha", pairA, 1)
        val result = keys.save("Alpha", pairB, 2)
        assertTrue("expected a NameConflict, got $result", result is SaveResult.NameConflict)
        assertEquals("Alpha", (result as SaveResult.NameConflict).name)
        // The stored pair is still the original one.
        assertEquals(pairA, keys.load("Alpha"))
    }

    @Test
    fun anExplicitReplaceReplacesTheRecord() {
        val keys = collection()
        keys.save("Alpha", pairA, 1)
        val result = keys.save("Alpha", pairB, 2, replace = true)
        assertEquals(SaveResult.Saved("Alpha", replaced = true), result)
        assertEquals(pairB, keys.load("Alpha"))
        assertEquals(1, keys.keys().size)
    }

    @Test
    fun anIdenticalFingerprintIsReportedAsTheSameKey() {
        val keys = collection()
        keys.save("Alpha", pairA, 1)
        // The same cryptographic key saved under a new name would pretend two keys are different.
        val result = keys.save("Alpha again", pairA, 2)
        assertTrue(result is SaveResult.SameKeyExists)
        assertEquals("Alpha", (result as SaveResult.SameKeyExists).name)
        assertEquals(1, keys.keys().size)
    }

    @Test
    fun loadingAnUnknownNameYieldsNothing() {
        val keys = collection()
        assertNull(keys.load("nope"))
        keys.save("Alpha", pairA, 1)
        assertNull(keys.load("nope"))
    }

    // ------------------------------------------------------------------ deletion

    @Test
    fun deletingOnePairLeavesTheOthersUntouched() {
        val keys = collection()
        keys.save("Alpha", pairA, 1)
        keys.save("Beta", pairB, 2)
        assertTrue(keys.delete("Alpha"))
        assertNull(keys.load("Alpha"))
        assertEquals(pairB, keys.load("Beta"))
        assertEquals(listOf("Beta"), keys.keys().map { it.name })
    }

    @Test
    fun deletionRemovesThePersistedPrivateMaterial() {
        val storage = MemoryStorage()
        val keys = collection(storage)
        keys.save("Alpha", pairA, 1)
        val blobBefore = keys.storedBytes().single()
        keys.delete("Alpha")
        // The storage no longer carries the record's sealed payload.
        val document = storage.bytes!!.decodeToString()
        val sealed = Base64.getEncoder().encodeToString(blobBefore)
        assertFalse("the deleted record's sealed bytes must be gone from storage", document.contains(sealed))
        assertTrue("nothing may remain of the deleted record", keys.storedBytes().isEmpty())
    }

    // ------------------------------------------------------------------ persistence across restart

    @Test
    fun savedPairsSurviveANewCollectionOverTheSameStorage() {
        val storage = MemoryStorage()
        // One cipher for both "app runs": in the app this is exactly the guarantee the Android
        // Keystore gives - the same wrapping key before and after a restart.
        val cipher = SoftwareCipher()
        val first = RsaKeyCollection(storage, cipher)
        first.save("Alpha", pairA, 1)
        first.save("Beta", pairB, 2)

        // "Restart": a brand-new collection over the same storage with the same keystore key.
        val reopened = RsaKeyCollection(storage, cipher)
        assertEquals(setOf("Alpha", "Beta"), reopened.keys().map { it.name }.toSet())
        assertEquals(pairA, reopened.load("Alpha"))
        assertEquals(pairB, reopened.load("Beta"))
    }

    @Test
    fun aDamagedStoreDegradesToAnEmptyCollectionInsteadOfCrashing() {
        val storage = MemoryStorage()
        storage.write("this is not the collection format".encodeToByteArray())
        val keys = RsaKeyCollection(storage, SoftwareCipher())
        assertTrue(keys.keys().isEmpty())
        // And it saves again cleanly.
        assertEquals(SaveResult.Saved("Alpha", replaced = false), keys.save("Alpha", pairA, 1))
        assertEquals(pairA, keys.load("Alpha"))
    }

    @Test
    fun storedBlobsDoNotCarryPlaintextPem() {
        val storage = MemoryStorage()
        val keys = collection(storage)
        keys.save("Alpha", pairA, 1)
        val document = storage.bytes!!.decodeToString()
        assertFalse("the public half must not sit in plaintext", document.contains(pairA.publicPem))
        assertFalse("the private half must not sit in plaintext", document.contains(pairA.privatePem))
        assertFalse(document.contains("BEGIN PUBLIC KEY"))
        assertFalse(document.contains("BEGIN PRIVATE KEY"))
        // The non-secret listing metadata is what the saved list shows: the fingerprint is
        // readable, and the name is present in its stored (Base64) form.
        assertTrue(document.contains(pairA.fingerprint))
        assertTrue(document.contains(Base64.getEncoder().encodeToString("Alpha".encodeToByteArray())))
    }

    // ------------------------------------------------------------------ the active-pair session

    @Test
    fun theSessionStartsEmpty() {
        val session = RsaKeySession()
        assertNull(session.active)
        assertNull(session.savedName)
        assertFalse(session.activeIsUnsaved)
    }

    @Test
    fun generatingSetsAnUnsavedActivePair() {
        val session = RsaKeySession().generate(pairA)
        assertEquals(pairA, session.active)
        assertNull(session.savedName)
        assertTrue("a fresh pair is unsaved work", session.activeIsUnsaved)
    }

    @Test
    fun generatingAgainReplacesTheActivePairAndKeepsSavedPairsAlone() {
        val session = RsaKeySession().generate(pairA).savedAs("Alpha")
        val after = session.generate(pairB)
        assertEquals(pairB, after.active)
        assertNull(after.savedName)
        assertTrue(after.activeIsUnsaved)
        // The session knows nothing about the collection: "generate" cannot delete saved pairs.
    }

    @Test
    fun savingNamesTheActivePairWithoutChangingIt() {
        val session = RsaKeySession().generate(pairA).savedAs("Alpha")
        assertEquals(pairA, session.active)
        assertEquals("Alpha", session.savedName)
        assertFalse("a saved pair is no longer unsaved work", session.activeIsUnsaved)
    }

    @Test
    fun clearingRemovesTheActivePairOnly() {
        val session = RsaKeySession().generate(pairA).savedAs("Alpha")
        val cleared = session.clear()
        assertNull(cleared.active)
        assertNull(cleared.savedName)
        assertFalse(cleared.activeIsUnsaved)
    }

    @Test
    fun loadingReplacesTheActivePairWithTheSavedOne() {
        val session = RsaKeySession().generate(pairA)
        val loaded = session.loaded(pairB, "Beta")
        assertEquals(pairB, loaded.active)
        assertEquals("Beta", loaded.savedName)
        assertFalse(loaded.activeIsUnsaved)
    }

    @Test
    fun deletingASavedRecordUnlinksItFromTheActivePairButKeepsThePairOnScreen() {
        val session = RsaKeySession().generate(pairA).savedAs("Alpha")
        val after = session.savedRecordDeleted("Alpha")
        assertEquals("the pair on screen survives; only its persisted half is gone", pairA, after.active)
        assertNull(after.savedName)
        assertTrue(after.activeIsUnsaved)

        // Deleting a different record does not touch the active pairing.
        val other = RsaKeySession().generate(pairA).savedAs("Alpha")
        assertEquals("Alpha", other.savedRecordDeleted("Beta").savedName)
    }
}
