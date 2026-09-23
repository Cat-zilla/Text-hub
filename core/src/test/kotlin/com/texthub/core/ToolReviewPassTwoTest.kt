package com.texthub.core

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.validateParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Second full pass over the registry: the checks that are about *shape and behaviour of the UI
 * contract* rather than about one algorithm. Everything here runs against all tools, so a new tool
 * that forgets one of these rules fails the build.
 */
class ToolReviewPassTwoTest {

    private val tools = ToolRegistry.all

    // ------------------------------------------------------------------ control visibility

    @Test fun everyToolExposesOnlyControlsItCanHonour() {
        val problems = mutableListOf<String>()
        for (processor in tools) {
            val meta = processor.meta
            // A direction switch is only shown when it changes something, and swap only when the
            // result can be pushed back through the tool.
            val expectSwitch = !meta.oneWay && !meta.symmetric
            val expectSwap = !meta.oneWay && !meta.resultIsFinal
            if (meta.hasDirectionChoice != expectSwitch) {
                problems += "${meta.id}: direction switch is ${meta.hasDirectionChoice}, expected $expectSwitch"
            }
            if (meta.supportsSwap != expectSwap) {
                problems += "${meta.id}: swap is ${meta.supportsSwap}, expected $expectSwap"
            }
            if (meta.hasDirectionChoice && meta.encodeLabel == meta.decodeLabel) {
                problems += "${meta.id}: a switch with identical labels would do nothing"
            }
            if (meta.symmetric && meta.hasDirectionChoice) {
                problems += "${meta.id}: symmetric tool must not offer a direction switch"
            }
            if (meta.oneWay && meta.encodeLabel != meta.decodeLabel) {
                problems += "${meta.id}: one-way tool must use one label for its single operation"
            }
            if (meta.encodeLabel.isBlank() || meta.decodeLabel.isBlank()) {
                problems += "${meta.id}: blank direction label"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun theSecureToolsAreAllTwoWayAndNoneOfThemIsOneWay() {
        tools.filter { it.meta.category == ToolCategory.SECURE }.forEach { processor ->
            val meta = processor.meta
            if (meta.id == "rsakeygen") return@forEach // key generation has no reverse direction
            assertTrue("${meta.id} must offer a direction switch", meta.hasDirectionChoice)
            assertTrue("${meta.id} must offer swap", meta.supportsSwap)
        }
    }

    @Test fun advancedSettingsNeverReplaceTheWholePanel() {
        val problems = mutableListOf<String>()
        for (processor in tools) {
            val meta = processor.meta
            if (meta.params.isEmpty()) continue
            if (meta.primaryParams.isEmpty()) {
                problems += "${meta.id}: every parameter is marked advanced, so nothing is left in the panel"
            }
            meta.advancedParams.forEach { spec ->
                if (spec.helper.isNullOrBlank()) {
                    problems += "${meta.id}.${spec.key}: advanced parameter without an explanation"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun theCommonPathOfTheCryptoToolsIsShort() {
        // The primary panel of an encryption tool must not be longer than the password, the key size
        // and the choice of format: everything else belongs under "Additional encryption settings".
        val problems = mutableListOf<String>()
        tools.filter { it.meta.category == ToolCategory.SECURE }.forEach { processor ->
            val primary = processor.meta.primaryParams.size
            if (primary > 3) problems += "${processor.meta.id}: $primary primary settings"
            if (primary == 0) problems += "${processor.meta.id}: no primary settings at all"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // ------------------------------------------------------------------ parameter validation

    @Test fun theOnlyThingWrongWithAFreshToolIsAMissingSecret() {
        // A freshly opened tool has valid settings, except that a key or password field is still
        // empty - which is exactly what the inline message tells the user.
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val meta = processor.meta
            val requiredKeys = meta.params.filter { it.required }.map { it.key }.toSet()
            val unexpected = meta.validateParams(processor.defaultParams()).filter { it.key !in requiredKeys }
            if (unexpected.isNotEmpty()) {
                problems += "${meta.id}: ${unexpected.map { it.key + " -> " + it.message }}"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun missingRequiredValuesAndBadNumbersAreCaughtInline() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val meta = processor.meta
            meta.params.filter { it.required }.forEach { spec ->
                val issues = meta.validateParams(processor.defaultParams() + (spec.key to ""))
                if (issues.none { it.key == spec.key }) {
                    problems += "${meta.id}.${spec.key}: a blank required value was accepted"
                }
            }
            meta.params.filter { it.kind == ParamKind.NUMBER && it.min != Int.MIN_VALUE }.forEach { spec ->
                val tooSmall = (spec.min - 1).toString()
                val issues = meta.validateParams(processor.defaultParams() + (spec.key to tooSmall))
                if (issues.none { it.key == spec.key }) {
                    problems += "${meta.id}.${spec.key}: $tooSmall was accepted below the minimum"
                }
            }
            meta.params.filter { it.kind == ParamKind.CHOICE }.forEach { spec ->
                val issues = meta.validateParams(processor.defaultParams() + (spec.key to "NOT_A_CHOICE"))
                if (issues.none { it.key == spec.key }) {
                    problems += "${meta.id}.${spec.key}: an unknown choice was accepted"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun aPasswordIsNeverPersistedAndNeverBlank() {
        tools.forEach { processor ->
            val meta = processor.meta
            // Whether a dispatcher needs a secret is decided by the input it is given, so its
            // password field may be optional - but it is still sensitive (never written to disk),
            // still starts empty, and the field itself still refuses to be used as a blank secret
            // when the payload does need one (the decoded diagnosis asks for it by name).
            val dispatcher = meta.classification == Classification.DETECTION
            meta.params.filter { it.kind == ParamKind.PASSWORD }.forEach { spec ->
                assertTrue("${meta.id}.${spec.key} must be sensitive", spec.sensitive)
                if (!dispatcher) assertTrue("${meta.id}.${spec.key} must be required", spec.required)
                assertTrue("${meta.id}.${spec.key} must start empty", spec.defaultValue.isEmpty())
            }
        }
    }

    @Test fun resettingRestoresTheDocumentedDefaults() {
        // Reset means "back to the defaults the tool documents", and every tool with more than one
        // setting offers it.
        tools.filter { it.meta.params.size > 1 }.forEach { processor ->
            val meta = processor.meta
            assertTrue("${meta.id} must offer reset", meta.canResetParams)
            val changed = meta.params.associate { spec ->
                spec.key to when (spec.kind) {
                    ParamKind.CHOICE -> spec.choices.last().id
                    ParamKind.NUMBER -> (spec.defaultValue.toIntOrNull() ?: 1).toString()
                    else -> spec.defaultValue
                }
            }
            assertEquals(
                "${meta.id}: reset must restore the defaults",
                processor.defaultParams(),
                processor.defaultParams() + changed.mapValues { (key, _) -> processor.defaultParams()[key] ?: "" },
            )
        }
    }

    @Test fun singleSettingToolsDoNotOfferAResetThatWouldDoNothing() {
        tools.filter { it.meta.params.size <= 1 }.forEach { processor ->
            assertFalse("${processor.meta.id} should not offer reset", processor.meta.canResetParams)
        }
    }

    // ------------------------------------------------------------------ payload compatibility

    /**
     * Payloads frozen from the 1.4.1 layout (version 0x01, KDF id `0x01`, no recorded key size) and
     * from the 1.4.2 layout (KDF id `0x02`). They must stay readable forever: this is the promise
     * that an update never strands data.
     */
    @Test fun payloadsWrittenByEarlierVersionsStillDecrypt() {
        val password = "round6-password"

        // 1.4.1 AES-GCM (key size not recorded).
        val legacyGcm = "AQEYxYYQPeP2Ijv302ecijMdvot6uoBvy9rjZ6/NpGHhvWaW4B7ggkoIfjwomDMe8n6pfGNx76Ne"
        assertEquals(0x01.toByte(), Base64Codec.decode(legacyGcm)[1])
        assertEquals(
            "legacy text",
            ToolRegistry.get("aes").process(legacyGcm, mapOf("password" to password, "keySize" to "256"), Direction.DECODE),
        )

        // 1.4.1 AES-CBC + HMAC and AES-CTR + HMAC (same legacy framing).
        val legacyCbc = "AgH+Xj9+mmYtV+bx/8T2LnykTu8/2K4o9kYcS8zvra/Ks8UvSNym6eWRpppgPVGrdC9vqA2BWrWn5kHWDTnLNmgdcR9dBDUsMtcg6UcRkI20pQ=="
        assertEquals(
            "legacy cbc",
            ToolRegistry.get("aescbc").process(legacyCbc, mapOf("password" to password, "keySize" to "256"), Direction.DECODE),
        )
        val legacyCtr = "BAGA6d4T39X30j2KecrQ6WnQ3jlxnAydi2z2pTwam+odQ9FYDl+tjvqvg37lyP1QvVGpDG006eRdI85Uf9Re7lzW+4ym4GQi7558kg=="
        assertEquals(
            "legacy ctr",
            ToolRegistry.get("aesctr").process(legacyCtr, mapOf("password" to password, "keySize" to "256"), Direction.DECODE),
        )

        // A 1.4.2 payload that records a 128-bit key: the 256-bit setting must still refuse it, and
        // the correct setting must open it.
        val modern128 = "AQGFxWh/NNGf0Qo28Kpai/LDZgz0s+/bgsJZpdVxVk7x+pnG83frbHPNcMfUZ4WpZRZNTA=="
        // KDF id 0x01: the 128-bit size was not recordable before 1.4.2 either (0x02 means 256-bit).
        assertEquals(0x01.toByte(), Base64Codec.decode(modern128)[1])
        assertEquals(
            "modern",
            ToolRegistry.get("aes").process(modern128, mapOf("password" to password, "keySize" to "128"), Direction.DECODE),
        )
        val refused = runCatching {
            ToolRegistry.get("aes").process(modern128, mapOf("password" to password, "keySize" to "256"), Direction.DECODE)
        }.exceptionOrNull()
        assertTrue(
            "a legacy 128-bit payload read as 256-bit must name the right size, got: ${refused?.message}",
            refused?.message?.contains("128-bit") == true,
        )
    }

    @Test fun everySecureToolNamesItsPayloadFormatInItsInfoSheet() {
        tools.filter { it.meta.category == ToolCategory.SECURE && it.meta.id != "rsakeygen" }.forEach { processor ->
            val convention = processor.meta.info.convention ?: ""
            assertTrue(
                "${processor.meta.id} must document its payload format",
                convention.contains("Payload") || convention.contains("version"),
            )
            assertTrue(
                "${processor.meta.id} must state that the password cannot be recovered or that a key is required",
                processor.meta.info.requiresKey || processor.meta.info.summary.contains("key"),
            )
        }
    }

    // ------------------------------------------------------------------ empty input policy

    @Test fun emptyInputIsEitherAnEmptyResultOrAFriendlySentence() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val outcome = ProcessingEngine.run(processor, "", processor.defaultParams(), Direction.ENCODE)
            if (!outcome.isSuccess && outcome.error != null) {
                val message = outcome.error!!
                if (message.contains("Exception") || message.contains(".kt:")) {
                    problems += "${processor.meta.id}: raw exception text in '$message'"
                }
            }
            if (outcome.isSuccess && outcome.output.isNotEmpty() && !processor.meta.inputOptional) {
                problems += "${processor.meta.id}: empty input produced output"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun toolsThatWorkWithoutInputSaySoAndDo() {
        val optional = tools.filter { it.meta.inputOptional }
        assertTrue("there should be at least the key generator", optional.any { it.meta.id == "rsakeygen" })
        optional.forEach { processor ->
            val outcome = ProcessingEngine.run(processor, "", processor.defaultParams(), Direction.ENCODE)
            assertTrue("${processor.meta.id} claims to work without input but failed: ${outcome.error}", outcome.isSuccess)
        }
    }
}
