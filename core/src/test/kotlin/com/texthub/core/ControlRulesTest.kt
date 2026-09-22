package com.texthub.core

import com.texthub.core.model.ParamKind
import com.texthub.core.model.ToolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind the controls on the main screen, and the settings panel.
 *
 * Requirements 3, 6 and 9 of round 7 ask for controls that come from what a tool can actually do:
 * no Apply button that repeats the same operation, no Swap where the result cannot be pushed back,
 * no direction switch for a tool that only has one direction or the same one both ways, common
 * settings in view and the rest under "Additional encryption settings", and a Reset that restores
 * the documented default. All of those rules are data on [com.texthub.core.model.ToolMeta], so
 * they are checked here for **all 73 tools** - a new tool that gets one of them wrong fails the
 * build instead of shipping a control that does nothing.
 */
class ControlRulesTest {

    private val tools = ToolRegistry.all

    @Test fun oneOperationIsOneButtonAndOneLabel() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val meta = processor.meta
            if (meta.oneWay && meta.encodeLabel != meta.decodeLabel) {
                problems += "${meta.id}: one-way tool with two labels (${meta.encodeLabel}/${meta.decodeLabel})"
            }
            if (meta.symmetric && meta.hasDirectionChoice) {
                problems += "${meta.id}: symmetric tool with a direction switch"
            }
            if (meta.hasDirectionChoice && meta.encodeLabel == meta.decodeLabel) {
                problems += "${meta.id}: direction switch that switches nothing"
            }
            if ((meta.oneWay || meta.symmetric) && meta.supportsSwap && meta.hasDirectionChoice) {
                problems += "${meta.id}: inconsistent control flags"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun swapAppearsExactlyWhereTheResultCanBeReused() {
        // The rule, spelled out: Swap needs a reverse direction and a result that is a message
        // rather than a final answer (a digest, a measurement, a comparison, an inspection).
        tools.forEach { processor ->
            val meta = processor.meta
            val expected = !meta.oneWay && !meta.resultIsFinal
            assertEquals("${meta.id}: swap visibility", expected, meta.supportsSwap)
        }
    }

    @Test fun theControlFlagsOfTheImportantToolsAreWhatTheUserExpects() {
        data class Expectation(val switch: Boolean, val swap: Boolean)

        val expected = mapOf(
            // Two-way message tools: a real direction and a useful round trip.
            "aes" to Expectation(true, true),
            "aescbc" to Expectation(true, true),
            "aesctr" to Expectation(true, true),
            "aesrawkey" to Expectation(true, true),
            "rsa" to Expectation(true, true),
            "chacha" to Expectation(true, true),
            "base64" to Expectation(true, true),
            "morse" to Expectation(true, true),
            // Hash/HMAC/checksum: the reverse direction is verification, but a digest is not input.
            "hash" to Expectation(true, false),
            "hmac" to Expectation(true, false),
            "checksum" to Expectation(true, false),
            "pbkdf2" to Expectation(true, false),
            // Symmetric: nothing to switch, nothing to swap into.
            "rot13" to Expectation(false, true),
            "atbash" to Expectation(false, true),
            "enigma" to Expectation(false, true),
            // One operation only: no switch, no swap.
            "caesarbrute" to Expectation(false, false),
            "rsakeygen" to Expectation(false, false),
            "textstats" to Expectation(false, false),
            "textdiff" to Expectation(false, false),
            "regex" to Expectation(false, false),
            "jwt" to Expectation(false, false),
        )
        expected.forEach { (id, want) ->
            val meta = ToolRegistry.metaOf(id)
            assertEquals("$id: direction switch", want.switch, meta.hasDirectionChoice)
            assertEquals("$id: swap", want.swap, meta.supportsSwap)
        }
    }

    @Test fun symmetricToolsSayThatBothDirectionsAreTheSame() {
        val symmetric = tools.filter { it.meta.symmetric }
        assertTrue("there are symmetric tools to check", symmetric.isNotEmpty())
        symmetric.forEach { processor ->
            // No switch, and the one operation is named - which is what the main screen shows.
            assertFalse("${processor.meta.id}: symmetric tools have no switch", processor.meta.hasDirectionChoice)
            assertTrue("${processor.meta.id}: needs a single label", processor.meta.encodeLabel.isNotBlank())
        }
    }

    // ------------------------------------------------------------------ settings panel

    /** Settings that only matter for advanced or unusual configurations. */
    private val advancedOnlyKeys = setOf(
        "opensslKdf", "opensslDigest", "opensslIterations", "jweEnc",
    )

    @Test fun theEverydayPathOfEveryToolStaysShort() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val meta = processor.meta
            if (meta.params.isEmpty()) return@forEach
            val primary = meta.primaryParams
            if (primary.isEmpty()) {
                problems += "${meta.id}: nothing left in the settings panel"
            }
            if (primary.size > 6) {
                problems += "${meta.id}: ${primary.size} settings in view (${primary.map { it.key }})"
            }
            if (primary.size > 4 && meta.advancedParams.isEmpty()) {
                problems += "${meta.id}: ${primary.size} settings in view and nothing collapsed"
            }
            advancedOnlyKeys.forEach { key ->
                val spec = meta.params.firstOrNull { it.key == key } ?: return@forEach
                if (!spec.advanced) problems += "${meta.id}.$key should live under Additional encryption settings"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun aRequiredSettingIsNeverHiddenUnderAdditionalSettings() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            processor.meta.params.filter { it.required && it.advanced }.forEach { spec ->
                problems += "${processor.meta.id}.${spec.key}: required but collapsed"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun secretsAreAlwaysInViewAndNeverOptional() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            processor.meta.params.filter { it.kind == ParamKind.PASSWORD }.forEach { spec ->
                if (spec.advanced) problems += "${processor.meta.id}.${spec.key}: a secret under advanced settings"
                if (!spec.required) problems += "${processor.meta.id}.${spec.key}: a secret that is optional"
                if (spec.defaultValue.isNotEmpty()) {
                    problems += "${processor.meta.id}.${spec.key}: a secret with a default value"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyAdvancedSettingExplainsItself() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            processor.meta.advancedParams.forEach { spec ->
                if (spec.helper.isNullOrBlank()) {
                    problems += "${processor.meta.id}.${spec.key}: advanced setting without an explanation"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun resetHasSomethingToRestoreExactlyWhenItIsOffered() {
        tools.forEach { processor ->
            val meta = processor.meta
            // Reset appears once there is more than one setting; a single setting has no "changed"
            // state worth a button, and the screen would only be cluttered by one.
            assertEquals("${meta.id}: reset visibility", meta.params.size > 1, meta.canResetParams)
            // A choice or a number always has a concrete default: an empty one would be a setting
            // that starts in a state the user cannot see. Free-text inputs (a password, a key, the
            // text a digest is verified against) legitimately start empty.
            meta.params.filter { it.kind == ParamKind.CHOICE || it.kind == ParamKind.NUMBER }.forEach { spec ->
                assertTrue(
                    "${meta.id}.${spec.key}: a ${spec.kind.name.lowercase()} setting must have a default",
                    spec.defaultValue.isNotEmpty(),
                )
            }
        }
    }

    // ------------------------------------------------------------------ help text

    @Test fun everyToolExplainsWhatItIsAndWhenToUseIt() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val info = processor.meta.info
            if (info.summary.length < 40) problems += "${processor.meta.id}: summary too short to be useful"
            if (info.useCases.isEmpty()) problems += "${processor.meta.id}: no use cases"
            if (processor.meta.category != ToolCategory.ENCODING && info.warnings.isEmpty()) {
                problems += "${processor.meta.id}: no warnings for a non-encoding tool"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
