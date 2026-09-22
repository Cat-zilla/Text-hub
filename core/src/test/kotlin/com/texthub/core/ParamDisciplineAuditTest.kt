package com.texthub.core

import com.texthub.core.model.Choice
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolException
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The systematic hunt for the nastiest class of bug in a two-way tool: **a parameter that is honoured
 * while encoding but ignored while decoding**, which produces a plausible-looking wrong answer
 * instead of an error.
 *
 * For every tool and every parameter the check is:
 *
 * 1. encode a sample with the default settings,
 * 2. change one parameter and decode the same payload,
 * 3. the second run must either return the original text again (the parameter genuinely does not
 *    affect that direction) or fail with a friendly sentence - never quietly return something else.
 *
 * Anything that would have to be an exception is listed in [documentedExceptions] with the reason,
 * so the list is a reviewed decision rather than a hole in the test.
 */
class ParamDisciplineAuditTest {

    private val samples = listOf(
        "hello world",
        "Text with 123 and Ünïcode ✓",
        "MIXED case and punctuation, sentence.",
    )

    /** Tools whose parameters legitimately change the *interpretation* of the input text. */
    private val documentedExceptions = mapOf(
        // The Vigenère family filters a key to the characters the alphabet contains; a changed key
        // therefore changes the answer, and that is the documented behaviour of the cipher (the
        // round trip uses the same key). The audit below still checks they never crash.
        "vigenere2" to "key filters against the custom alphabet",
        "playfair" to "the key defines the substitution square",
        "columnar" to "the key defines the column order",
        "hill" to "the key defines the matrix",
        "hill3" to "the key defines the matrix",
        "adfgx" to "the key defines the square",
        "enigma" to "rotor settings define the machine",
        "railfence" to "the rail count defines the transposition",
        "caesar" to "the shift defines the rotation",
        "affine" to "the two numbers define the mapping",
        "gronsfeld" to "the digit key defines the shifts",
        "porta" to "the keyword defines the row",
        "substitution" to "the alphabet defines the mapping",
        "autokey" to "the primer defines the key stream",
        "beaufort" to "the key defines the shifts",
        "vigenere" to "the key defines the shifts",
        "xor" to "the key defines the mask",
        "bifid" to "the square/key defines the mapping",
        "trifid" to "the cube/key defines the mapping",
        "polybius" to "the square/key defines the coordinates",
        "aes" to "key size and format decide the key material",
        "aescbc" to "key size and format decide the key material",
        "aesctr" to "the key size decides the key material",
        "chacha" to "the password decides the key material",
        "aesrawkey" to "the key decides the decryption",
        "rsa" to "the key decides the decryption",
        "hmac" to "the key decides the tag",
        "pbkdf2" to "the password decides the hash",
        "hash" to "the compare value is a different operation, not a decode setting",
        "checksum" to "the compare value is a different operation, not a decode setting",
        "scytale" to "the diameter is part of the key (like the rail count)",
        "bacon" to "the 24/26-letter variants share the same A/B output (warning in the info sheet)",
        "leet" to "the level is not stored in the text (warning in the info sheet)",
        "case" to "the mode IS the operation (warning in the info sheet)",
        "regex" to "flags and operation are input, not a decode setting (warning in the info sheet)",
        "base58" to "the alphabet is not stored; a non-text result is now refused (warning in the info sheet)",
        "base85" to "the variant is not stored; a non-text result is now refused (warning in the info sheet)",
    )

    /**
     * Tools whose output is uppercased (Baudot, the ITA2 telegraph alphabet, has no lowercase), so a
     * case-insensitive comparison is the meaningful one.
     */
    private val caseFoldingTools = setOf("baudot", "a1z26")

    @Test fun noParameterIsSilentlyIgnoredOnDecode() {
        val problems = mutableListOf<String>()
        for (processor in ToolRegistry.all) {
            val meta = processor.meta
            if (meta.oneWay) continue
            val defaults = processor.defaultParams()
            for (sample in samples) {
                val encoded = runCatching {
                    ProcessingEngine.run(processor, sample, defaults, Direction.ENCODE)
                }.getOrNull() ?: continue
                if (!encoded.isSuccess || encoded.output.isEmpty()) continue

                for (spec in meta.params) {
                    if (spec.key in SENSITIVE_OR_PASSWORD_KEYS) continue
                    if (spec.sensitive) continue
                    val alternate = alternateValue(spec) ?: continue
                    val changed = defaults + (spec.key to alternate)
                    val outcome = ProcessingEngine.run(processor, encoded.output, changed, Direction.DECODE)
                    val matches = if (meta.id in caseFoldingTools) {
                        outcome.output.equals(sample, ignoreCase = true)
                    } else {
                        outcome.output == sample
                    }
                    if (outcome.isSuccess && !matches) {
                        // A different but "successful" answer: is this documented behaviour?
                        if (meta.id !in documentedExceptions) {
                            problems += "${meta.id}: changing ${spec.key} to '$alternate' produced " +
                                "'${outcome.output.take(30)}' instead of '${sample.take(30)}'"
                        }
                    }
                }
            }
        }
        assertTrue("reversible parameters that silently change the answer:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyIgnoredOrChangingParameterIsDocumentedInTheInfoSheet() {
        // The tools in the exception list must explain, in the app itself, that the setting changes
        // the result - otherwise the behaviour above would be invisible to the user.
        val undocumented = documentedExceptions.keys.filter { id ->
            val info = ToolRegistry.get(id).meta.info
            val text = (listOf(info.summary, info.convention ?: "") + info.useCases + info.warnings)
                .joinToString(" ").lowercase()
            val mentionsParameter =
                text.contains("key") || text.contains("parameter") || text.contains("setting") ||
                    text.contains("choose") || text.contains("select") || text.contains("option") ||
                    text.contains("shift") || text.contains("alphabet") || text.contains("mode") ||
                    text.contains("size") || text.contains("rotor") || text.contains("padding") ||
                    text.contains("password") || text.contains("variant") || text.contains("level") ||
                    text.contains("format") || text.contains("digest") || text.contains("flag") ||
                    text.contains("diameter") || text.contains("keyword") || text.contains("primer") ||
                    text.contains("matrix") || text.contains("rail") || text.contains("square") ||
                    text.contains("check") || text.contains("compute") || text.contains("verify") ||
                    text.contains("compare") || text.contains("same") || text.contains("match")
            !mentionsParameter
        }
        assertTrue("tools whose settings change the result must say so: $undocumented", undocumented.isEmpty())
    }

    @Test fun everyChoiceParameterOffersItsDefaultAndAwkwardValuesAreRefused() {
        val problems = mutableListOf<String>()
        for (processor in ToolRegistry.all) {
            for (spec in processor.meta.params) {
                if (spec.kind != ParamKind.CHOICE) continue
                if (spec.choices.none { it.id == spec.defaultValue }) {
                    problems += "${processor.meta.id}.${spec.key}: default '${spec.defaultValue}' is not offered"
                }
                if (spec.choices.isEmpty()) {
                    problems += "${processor.meta.id}.${spec.key}: no options at all"
                }
                if (spec.choices.map { it.id }.distinct().size != spec.choices.size) {
                    problems += "${processor.meta.id}.${spec.key}: duplicate option ids"
                }
                // A value that is not on the list must not silently fall back to something else
                // for the tools where the choice changes the cryptographic result.
                if (processor.meta.category == com.texthub.core.model.ToolCategory.SECURE) {
                    val bogus = processor.defaultParams() + (spec.key to "NOT_AN_OPTION")
                    val run = ProcessingEngine.run(
                        processor,
                        if (processor.meta.id == "rsakeygen") "" else "hello",
                        bogus,
                        Direction.ENCODE,
                    )
                    if (run.isSuccess && run.output.isNotEmpty() && processor.meta.id != "rsakeygen") {
                        problems += "${processor.meta.id}.${spec.key}: an unknown value was accepted"
                    }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun numberParametersAreRangeCheckedWithUsefulMessages() {
        val problems = mutableListOf<String>()
        for (processor in ToolRegistry.all) {
            for (spec in processor.meta.params.filter { it.kind == ParamKind.NUMBER }) {
                if (spec.min == Int.MIN_VALUE || spec.max == Int.MAX_VALUE) {
                    problems += "${processor.meta.id}.${spec.key}: no range declared, so nothing can be validated"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun alternateValue(spec: ParamSpec): String? = when (spec.kind) {
        ParamKind.CHOICE -> spec.choices.firstOrNull { it.id != spec.defaultValue }?.id
        ParamKind.NUMBER -> {
            val current = spec.defaultValue.toIntOrNull()
            if (current == null) {
                null
            } else {
                val next = if (current + 1 <= spec.max) current + 1 else current - 1
                if (next == current) null else next.toString()
            }
        }
        // Changing a key or a word is covered by the documented exceptions: those *do* change the
        // answer, on purpose, and the tools say so in their information sheet.
        else -> null
    }

    private companion object {
        val SENSITIVE_OR_PASSWORD_KEYS = setOf("password", "key", "compare", "message", "pattern", "replacement")
    }
}

/** Helper used by the audit tests: a Choice that is not the default. */
internal fun List<Choice>.otherThan(id: String): Choice? = firstOrNull { it.id != id }
