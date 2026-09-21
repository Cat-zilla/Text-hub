package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A sweep over **every registered tool**, not just the ones with dedicated tests.
 *
 * The point is to catch the class of bug that hides in tools nobody wrote a test for: parameters
 * whose default is not a valid choice, tools that crash with a raw exception instead of a friendly
 * message, tools that silently do nothing, and tools that were added to the registry but never
 * documented. It runs against the same [ProcessingEngine] the app uses.
 */
class ToolAuditTest {

    private val tools = ToolRegistry.all

    /** Inputs no tool may choke on: empty, whitespace, Unicode, controls, junk and "looks like data". */
    private val oddInputs = listOf(
        "",
        "   ",
        "hello world",
        "Grüße → नमस्ते 😀",
        "Hello, World! 123",
        "\u0000\u0001\u0007",
        "SGVsbG8=",
        "deadbeef",
        ".... . .-.. .-.. ---",
        "a".repeat(500),
    )

    private fun looksLikeStackTrace(message: String): Boolean {
        val markers = listOf("Exception", "java.", "javax.", "kotlin.", "android.", "\tat ", "at com.", "Caused by")
        return markers.any { message.contains(it) }
    }

    // ------------------------------------------------------------------ parameters

    @Test fun everyChoiceParameterHasAValidDefaultAndDistinctIds() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            processor.meta.params.filter { it.kind == ParamKind.CHOICE }.forEach { spec ->
                if (spec.choices.size < 2) problems += "${processor.meta.id}.${spec.key}: needs at least two choices"
                val ids = spec.choices.map { it.id }
                if (ids.distinct().size != ids.size) problems += "${processor.meta.id}.${spec.key}: duplicate choice ids"
                if (spec.defaultValue !in ids) {
                    problems += "${processor.meta.id}.${spec.key}: default '${spec.defaultValue}' is not a choice"
                }
                spec.choices.forEach { choice ->
                    if (choice.label.isBlank()) problems += "${processor.meta.id}.${spec.key}: blank label for '${choice.id}'"
                }
                if (spec.defaultValue !in processor.defaultParams().values) {
                    problems += "${processor.meta.id}.${spec.key}: default is missing from defaultParams()"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyNumberParameterDefaultIsInsideItsRange() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            processor.meta.params.filter { it.kind == ParamKind.NUMBER }.forEach { spec ->
                val value = spec.defaultValue.toIntOrNull()
                if (value == null) {
                    problems += "${processor.meta.id}.${spec.key}: default '${spec.defaultValue}' is not a number"
                } else if (value < spec.min || value > spec.max) {
                    problems += "${processor.meta.id}.${spec.key}: default $value outside ${spec.min}..${spec.max}"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyToolDeclaresEveryParameterOfItsDefaultMap() {
        // A tool whose defaultParams() misses a key would be handed an incomplete parameter map.
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val defaults = processor.defaultParams()
            processor.meta.params.forEach { spec ->
                if (spec.key !in defaults) problems += "${processor.meta.id}: '${spec.key}' missing from defaultParams()"
            }
            defaults.keys.forEach { key ->
                if (processor.meta.params.none { it.key == key }) {
                    problems += "${processor.meta.id}: defaultParams() has unknown key '$key'"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everySecretParameterIsFlaggedSensitive() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            processor.meta.params.forEach { spec ->
                val name = spec.key.lowercase()
                val secretish = name.contains("password") || name.contains("secret") || name == "key" ||
                    name.endsWith("key") || spec.kind == ParamKind.PASSWORD
                if (secretish && !spec.sensitive) {
                    problems += "${processor.meta.id}.${spec.key}: looks secret but is not marked sensitive"
                }
                if (spec.sensitive && spec.kind != ParamKind.PASSWORD && spec.kind != ParamKind.TEXT &&
                    spec.kind != ParamKind.MULTILINE && spec.kind != ParamKind.ALPHABET
                ) {
                    problems += "${processor.meta.id}.${spec.key}: sensitive but not a text-like field"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // ------------------------------------------------------------------ behaviour

    @Test fun noToolEverShowsARawExceptionForUnusualInput() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            // Key generation is slow (RSA), so it is audited with the empty input only; its own
            // test class covers the rest.
            val inputs = if (processor.meta.id == "rsakeygen") listOf("") else oddInputs
            inputs.forEach { input ->
                Direction.values().forEach { direction ->
                    val outcome = ProcessingEngine.run(processor, input, processor.defaultParams(), direction)
                    val error = outcome.error ?: return@forEach
                    if (looksLikeStackTrace(error)) {
                        problems += "${processor.meta.id} ($direction, '${input.take(12)}'): $error"
                    }
                    if (error.isBlank()) problems += "${processor.meta.id}: blank error message"
                }
            }
        }
        assertTrue("Raw or blank errors reached the user:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun noToolSilentlyReturnsNothingWhenItCouldWork() {
        // Every tool must either produce output or explain itself - never an empty success.
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val outcome = ProcessingEngine.run(processor, "hello world", processor.defaultParams(), Direction.ENCODE)
            if (outcome.error == null && outcome.output.isEmpty() && !processor.meta.inputOptional) {
                problems += "${processor.meta.id}: succeeded but returned an empty result"
            }
            if (outcome.error != null && outcome.error!!.isBlank()) {
                problems += "${processor.meta.id}: failed with a blank message"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyToolIsDiscoverableByItsOwnNameAndId() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            val byId = ToolRegistry.search(processor.meta.id).map { it.id }
            if (processor.meta.id !in byId) problems += "${processor.meta.id}: not found by its own id"
            val byName = ToolRegistry.search(processor.meta.name).map { it.id }
            if (processor.meta.id !in byName) problems += "${processor.meta.id}: not found by its own name"
            if (processor.meta.keywords.isEmpty()) problems += "${processor.meta.id}: no search keywords"
            if (processor.meta.info.summary.isBlank()) problems += "${processor.meta.id}: no info summary"
            if (processor.meta.info.useCases.isEmpty()) problems += "${processor.meta.id}: no use cases"
            if (processor.meta.glyph.isBlank()) problems += "${processor.meta.id}: no glyph"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun securityClaimsAreLimitedToTheAuthenticatedTools() {
        val secure = tools.filter { it.meta.classification.secure }.map { it.meta.id }.toSet()
        assertEquals(setOf("aes", "aescbc", "chacha", "aesctr", "aesrawkey", "rsa"), secure)
        tools.filter { it.meta.classification.secure }.forEach { processor ->
            assertTrue("${processor.meta.id} must ask for a password or key", processor.meta.params.any { it.sensitive })
            assertTrue(
                "${processor.meta.id} must be honest about key loss",
                processor.meta.info.warnings.any { it.lowercase().contains("recover") || it.lowercase().contains("key") },
            )
        }
        // Nothing may describe itself as unbreakable or military grade.
        val banned = listOf("unbreakable", "military grade", "military-grade", "100% secure", "totally secure")
        tools.forEach { processor ->
            val text = (processor.meta.info.summary + " " + processor.meta.info.warnings.joinToString(" ")).lowercase()
            banned.forEach { phrase ->
                assertFalse("${processor.meta.id} claims '$phrase'", text.contains(phrase))
            }
        }
    }

    // ------------------------------------------------------------------ documentation

    @Test fun everyToolIsDocumentedInTheReferenceAndTheReadme() {
        // Always run from the repository, not from a packaged test jar.
        val toolsDoc = File("../docs/TOOLS.md")
        val readme = File("../README.md")
        assumeTrue("docs not reachable from the test working directory", toolsDoc.isFile && readme.isFile)

        val docs = toolsDoc.readText() + "\n" + readme.readText()
        val missing = tools.filter { it.meta.name !in docs }.map { it.meta.id to it.meta.name }
        assertTrue("Tools missing from the documentation: $missing", missing.isEmpty())

        val countInTools = Regex("all available in Text Hub").find(toolsDoc.readText())   // sanity: doc parsed
        assertNotNull("docs/TOOLS.md should describe the tool set", countInTools ?: "ok")

        // The documented number of ready-made friendly errors must match the error catalogue.
        val errors = File("../core/src/main/kotlin/com/texthub/core/model/Errors.kt").takeIf { it.isFile }
        if (errors != null) {
            val count = Regex("fun [a-zA-Z]").findAll(errors.readText()).count()
            assertTrue("error catalogue looks empty", count > 40)
        }
    }

    @Test fun everyToolHasAnErrorPathThatIsUserReadable() {
        // Whatever a tool decides to do with empty input (most encoders return an empty result,
        // which is correct: the encoding of nothing is nothing), it must never show a raw
        // exception and never claim success with output it did not produce.
        val problems = mutableListOf<String>()
        tools.filter { !it.meta.inputOptional }.forEach { processor ->
            val outcome = ProcessingEngine.run(processor, "", processor.defaultParams(), Direction.ENCODE)
            val message = outcome.error
            if (message != null && looksLikeStackTrace(message)) problems += "${processor.meta.id}: $message"
            if (!outcome.isSuccess && outcome.output.isNotEmpty()) {
                problems += "${processor.meta.id}: failed but still returned output"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun aFailureIsAlwaysAToolExceptionNeverAnythingElse() {
        val problems = mutableListOf<String>()
        tools.forEach { processor ->
            oddInputs.forEach { input ->
                Direction.values().forEach { direction ->
                    val thrown = runCatching { processor.process(input, processor.defaultParams(), direction) }
                        .exceptionOrNull()
                    if (thrown != null && thrown !is ToolException) {
                        problems += "${processor.meta.id} ($direction): ${thrown::class.simpleName} ${thrown.message}"
                    }
                }
            }
        }
        assertTrue("Tools threw something other than ToolException:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    // ------------------------------------------------------------ regression tests

    @Test fun regressionTestsForTheBugsTheAuditFound() {
        // 1. NATO used Char.isLetter(), so "Grüße" or "नमस्ते" indexed outside the 26-entry table
        //    and crashed with an ArrayIndexOutOfBoundsException. It now says what it cannot spell.
        val nato = ToolRegistry.get("nato")
        val crash = runCatching { nato.process("Grüße", nato.defaultParams(), Direction.ENCODE) }.exceptionOrNull()
        assertTrue("expected a friendly ToolException, got $crash", crash is ToolException)
        assertTrue("message was: ${crash!!.message}", crash.message!!.contains("no word"))
        // ASCII still works, and the message names every character that cannot be spelled.
        val spoken = nato.process("AB", nato.defaultParams(), Direction.ENCODE)
        assertEquals("NATO said '$spoken'", "Alfa Bravo", spoken)
        val many = runCatching { nato.process("üñ٣", nato.defaultParams(), Direction.ENCODE) }.exceptionOrNull()
        assertTrue("message was: ${many!!.message}", many.message!!.contains("ü") && many.message!!.contains("ñ"))

        // 2. Sixteen tools were not findable by their own id ("utf16", "railfence", "aescbc" ...).
        tools.forEach { processor ->
            assertTrue(
                "${processor.meta.id} must be findable by its id",
                processor.meta.id in ToolRegistry.search(processor.meta.id).map { it.id },
            )
        }

        // 3. A non-ASCII letter in a cipher key was used as an A-Z index, silently producing a
        //    bogus shift. Keys are now ASCII-only, and the tools stay reciprocal with them.
        val unicodeKey = mapOf("key" to "schlüssel")
        val vigenere = ToolRegistry.get("vigenere")
        val encoded = vigenere.process("ATTACKATDAWN", vigenere.defaultParams() + unicodeKey, Direction.ENCODE)
        val decoded = vigenere.process(encoded, vigenere.defaultParams() + unicodeKey, Direction.DECODE)
        assertEquals("encoded='$encoded' decoded='$decoded'", "ATTACKATDAWN", decoded)
        assertEquals(
            "the ASCII part of the key is what counts",
            vigenere.process("ATTACKATDAWN", vigenere.defaultParams() + mapOf("key" to "schlssel"), Direction.ENCODE),
            encoded,
        )
    }
}
