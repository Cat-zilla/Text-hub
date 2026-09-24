package com.texthub.app.viewmodel

import com.texthub.core.ToolRegistry
import com.texthub.core.crypto.RsaHybrid
import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.defaultParams
import com.texthub.core.detector.SecretKind
import com.texthub.core.detector.UniversalDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Universal Decoder's key field as app state (1.6.6): the pure transitions behind typing,
 * pasting, clearing and choosing a saved key, and the derived flags the screen renders from.
 * Pure JVM - the same style as the drag/reorder tests.
 */
class AnalysisKeyStateTest {

    private val keys = RsaKeyGen.generatePair(2048)
    private val ciphertext = RsaHybrid.encrypt("hello", keys.publicPem)

    private fun universalState(input: String = ciphertext): HubUiState = HubUiState(
        toolId = UniversalDecoder.TOOL_ID,
        input = input,
        params = ToolRegistry.get(UniversalDecoder.TOOL_ID).defaultParams(),
        analysis = UniversalDecoder.analyze(input),
    )

    @Test fun aMultiLinePemIsStoredVerbatim() {
        val pasted = keys.privatePem.replace("\n", "\r\n")
        val next = applyAnalysisSecret(universalState(), pasted)
        assertEquals(pasted, next.analysisSecret)
        assertEquals(pasted.length, next.params.getValue(UniversalDecoder.PARAM_SECRET).length)
        assertNull("a typed key is a manual key", next.analysisSavedKeyName)
        assertFalse(next.analysisUsesSavedKey)
    }

    @Test fun clearingTheKeyKeepsTheDetectedCiphertextAndTheAnalysis() {
        val filled = applyAnalysisSecret(universalState(), keys.privatePem, savedKeyName = "Project Alpha")
        val cleared = clearAnalysisSecret(filled)
        assertEquals("", cleared.analysisSecret)
        assertNull(cleared.analysisSavedKeyName)
        assertEquals(ciphertext, cleared.input)
        assertEquals(filled.analysis, cleared.analysis)
        // Only the key parameter changed.
        assertEquals(filled.params - UniversalDecoder.PARAM_SECRET, cleared.params - UniversalDecoder.PARAM_SECRET)
    }

    @Test fun choosingASavedKeyUsesExactlyThatKeyAndIsLabelledWithItsName() {
        val next = applyAnalysisSecret(universalState(), keys.privatePem, savedKeyName = "Project Alpha")
        assertEquals(keys.privatePem, next.analysisSecret)
        assertEquals("Project Alpha", next.analysisSavedKeyName)
        assertTrue(next.analysisUsesSavedKey)
        // Switching to another saved key replaces both the key and the label.
        val other = RsaKeyGen.generatePair(2048)
        val switched = applyAnalysisSecret(next, other.privatePem, savedKeyName = "Website")
        assertEquals(other.privatePem, switched.analysisSecret)
        assertEquals("Website", switched.analysisSavedKeyName)
    }

    @Test fun editingALoadedKeyMakesItManualWithoutTouchingTheSavedName() {
        val loaded = applyAnalysisSecret(universalState(), keys.privatePem, savedKeyName = "Project Alpha")
        val edited = applyAnalysisSecret(loaded, keys.privatePem + "\n")
        assertNull(edited.analysisSavedKeyName)
        assertFalse(edited.analysisUsesSavedKey)
        // An unchanged value (recomposition echoing the same text) keeps the label.
        val same = applyAnalysisSecret(loaded, keys.privatePem)
        assertEquals("Project Alpha", same.analysisSavedKeyName)
    }

    @Test fun theEditorFollowsTheDetectedSecretKindNotThePresenceOfAKey() {
        val state = universalState()
        assertEquals(SecretKind.PRIVATE_KEY, state.analysisSecretKind)
        assertTrue("an RSA payload gets the RSA key editor before any key exists", state.analysisUsesRsaKeyEditor)
        // A payload that needs a password keeps the password editor - even with a PEM elsewhere.
        val aes = ToolRegistry.get("aes")
        val aesPayload = aes.process("hello", aes.defaultParams() + ("password" to "pw"), com.texthub.core.model.Direction.ENCODE)
        val aesState = universalState(aesPayload)
        assertEquals(SecretKind.PASSWORD, aesState.analysisSecretKind)
        assertFalse(aesState.analysisUsesRsaKeyEditor)
        // Plain text needs nothing.
        assertNull(universalState("just words").analysisSecretKind)
        assertFalse(universalState("just words").analysisUsesRsaKeyEditor)
        // A PEM already in the field keeps the editor while the input is being changed.
        val holding = applyAnalysisSecret(universalState("just words"), keys.privatePem)
        assertTrue(holding.analysisUsesRsaKeyEditor)
        // Other tools never see any of this.
        val base64 = HubUiState(toolId = "base64", params = mapOf(UniversalDecoder.PARAM_SECRET to keys.privatePem))
        assertEquals("", base64.analysisSecret)
        assertFalse(base64.analysisUsesRsaKeyEditor)
    }

    @Test fun theLookalikeCheckIsAPrefixTestOnly() {
        assertTrue(looksLikePemBlock("  \n-----BEGIN PRIVATE KEY-----\nabc"))
        assertTrue(looksLikePemBlock("-----BEGIN PUBLIC KEY-----"))
        assertFalse(looksLikePemBlock("password"))
        assertFalse(looksLikePemBlock("00112233"))
        assertFalse(looksLikePemBlock(""))
    }

    @Test fun theSecretIsNeverAmongTheRememberedParameters() {
        val state = applyAnalysisSecret(universalState(), keys.privatePem, savedKeyName = "Project Alpha")
        val remembered = state.meta.rememberedParams(state.params)
        assertFalse(remembered.containsKey(UniversalDecoder.PARAM_SECRET))
        remembered.values.forEach { assertFalse(it.contains("PRIVATE KEY")) }
    }
}
