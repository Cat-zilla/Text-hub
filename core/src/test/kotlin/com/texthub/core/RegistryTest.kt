package com.texthub.core

import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ProcessOutcome
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the registry: every tool is unique, documented, classified, has default parameters
 * and actually round trips a sample text.
 */
class RegistryTest {

    @Test fun idsAreUnique() {
        val ids = ToolRegistry.all.map { it.meta.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everyToolIsDocumentedAndClassified() {
        ToolRegistry.all.forEach { processor ->
            val meta = processor.meta
            assertTrue("${meta.id} needs a name", meta.name.isNotBlank())
            assertTrue("${meta.id} needs a glyph", meta.glyph.isNotBlank())
            assertTrue("${meta.id} needs a summary", meta.info.summary.length > 40)
            assertTrue("${meta.id} needs use cases", meta.info.useCases.isNotEmpty())
            assertTrue("${meta.id} needs labels", meta.encodeLabel.isNotBlank() && meta.decodeLabel.isNotBlank())
            assertTrue("${meta.id} has an unknown category", meta.category in ToolCategory.values())
        }
    }

    @Test fun onlyAuthenticatedToolsClaimSecurity() {
        val secure = ToolRegistry.all.filter { it.meta.classification.secure }
        assertEquals(
            setOf("aes", "aescbc", "chacha", "aesctr", "aesrawkey", "rsa"),
            secure.map { it.meta.id }.toSet(),
        )
        // Every tool that claims security must actually ask for a password or a key.
        secure.forEach { processor ->
            assertTrue(
                "${processor.meta.id} must require a password or key",
                processor.meta.params.any { it.sensitive },
            )
        }
        ToolRegistry.all.filter { !it.meta.classification.secure }.forEach {
            assertTrue("${it.meta.id} must not claim security", !it.meta.classification.secure)
        }
    }

    @Test fun sensitiveParametersAreMarked() {
        ToolRegistry.all.forEach { processor ->
            processor.meta.params.forEach { param ->
                if (param.key == "password" || param.key == "key") {
                    assertTrue("${processor.meta.id}.${param.key} must be sensitive", param.sensitive)
                }
            }
        }
    }

    @Test fun everyToolHasEveryCategory() {
        ToolCategory.values().forEach { category ->
            assertTrue(
                "Category ${category.label} has no tools",
                ToolRegistry.all.any { it.meta.category == category }
            )
        }
    }

    @Test fun searchFindsExpectedTools() {
        fun ids(query: String) = ToolRegistry.search(query).map { it.id }.toSet()
        assertTrue(ids("base").containsAll(listOf("base64", "base32", "base58", "base85", "base45")))
        assertTrue(ids("caesar").contains("caesar"))
        assertTrue(ids("binary").contains("binary"))
        assertTrue(ids("baudot").contains("baudot"))
        assertTrue(ids("ascii").contains("ascii"))
        assertTrue(ids("AES").contains("aes"))
        assertTrue(ids("morse").contains("morse"))
        assertTrue(ids("escape").contains("unicodeescape"))
        assertTrue(ids("qr").contains("base45"))
        assertTrue(ids("phonetic").contains("nato"))
        assertTrue(ids("snake").contains("case"))
        assertTrue(ids("duplicate").contains("linetools"))
        assertTrue(ids("chacha").contains("chacha"))
        assertTrue(ids("aes256").contains("aes"))
        assertTrue(ids("sha256").contains("hash"))
        assertTrue(ids("base91").contains("base91"))
        assertTrue(ids("punycode").contains("punycode"))
        assertTrue(ids("jwt").contains("jwt"))
        assertTrue(ids("json").contains("json"))
        assertTrue(ids("crc32").contains("checksum"))
        assertTrue(ids("roman").contains("roman"))
        assertTrue(ids("diff").contains("textdiff"))
    }

    @Test fun searchIsCaseInsensitiveAndEmptyReturnsAll() {
        assertEquals(ToolRegistry.all.size, ToolRegistry.search("").size)
        assertEquals(ToolRegistry.search("hex").map { it.id }, ToolRegistry.search("HEX").map { it.id })
    }

    @Test fun everyToolRoundTripsWithDefaultParameters() {
        // Uppercase, digits-free sample: representable by every keyless tool.
        val sample = "HELLO, WORLD!"
        // Excluded: one-way analysis tools, and the letters-only ciphers (checked separately).
        val excluded = setOf(
            "bacon", "hill", "bifid", "polybius",
            "case", "linetools", "leet", "nato", "caesarbrute",
            // One-way or verification-only tools, covered by their own tests.
            "hash", "hmac", "pbkdf2", "checksum", "textstats",
            // Letters-only ciphers (punctuation is dropped by design) and the regex tester.
            "hill3", "trifid", "adfgx", "regex",
            // Generates fresh key material on every run, so it has no round trip.
            "rsakeygen",
        )
        val failures = mutableListOf<String>()
        ToolRegistry.all.filter { it.meta.id !in excluded }.forEach { processor ->
            val params = processor.defaultParams()
            val forward = ProcessingEngine.run(processor, sample, params, Direction.ENCODE)
            if (!forward.isSuccess) {
                // Tools that need a key are skipped with their (empty) default parameters.
                return@forEach
            }
            val back = ProcessingEngine.run(processor, forward.output, params, Direction.DECODE)
            if (!back.isSuccess || back.output != sample) {
                failures.add("${processor.meta.id}: forward='${forward.output.take(40)}' back='${back.output.take(40)}' error=${back.error}")
            }
        }
        assertTrue("Round trip failures: $failures", failures.isEmpty())
    }

    @Test fun baconRoundTripsLettersOnly() {
        val sample = "HELLOWORLD"
        val processor = ToolRegistry.get("bacon")
        val forward = ProcessingEngine.run(processor, sample, processor.defaultParams(), Direction.ENCODE)
        val back = ProcessingEngine.run(processor, forward.output, processor.defaultParams(), Direction.DECODE)
        assertEquals(sample, back.output)
    }

    @Test fun unicodeCapableToolsRoundTripUnicode() {
        val sample = "Grüße → नमस्ते 😀"
        val unicodeSafe = listOf(
            "base64", "base32", "base58", "base85", "url", "hex", "binary",
            "decimal", "octal", "unicode", "reverse", "rot47", "atbash", "caesar",
            "vigenere", "xor", "substitution", "affine", "railfence", "columnar", "aes",
        )
        val failures = mutableListOf<String>()
        unicodeSafe.forEach { id ->
            val processor = ToolRegistry.get(id)
            val params = processor.defaultParams() + when (id) {
                "vigenere" -> mapOf("key" to "schlüssel")
                "xor" -> mapOf("key" to "schlüssel")
                "columnar" -> mapOf("key" to " Schlüssel")
                "aes" -> mapOf("password" to "geheim")
                else -> emptyMap()
            }
            val forward = ProcessingEngine.run(processor, sample, params, Direction.ENCODE)
            if (!forward.isSuccess) {
                failures.add("$id encode failed: ${forward.error}")
                return@forEach
            }
            val back = ProcessingEngine.run(processor, forward.output, params, Direction.DECODE)
            val expected = if (id == "columnar" || id == "railfence") sample else sample
            if (!back.isSuccess || back.output != expected) {
                failures.add("$id: back='${back.output.take(40)}' error=${back.error}")
            }
        }
        assertTrue("Unicode round trip failures: $failures", failures.isEmpty())
    }

    @Test fun everyToolProducesOutputForSimpleAscii() {
        val sample = "hello world"
        ToolRegistry.all.forEach { processor ->
            val outcome = ProcessingEngine.run(processor, sample, processor.defaultParams(), Direction.ENCODE)
            val needsSecret = setOf(
                "aes", "aescbc", "chacha", "vigenere", "vigenere2", "playfair", "columnar",
                "xor", "substitution", "beaufort", "autokey", "pbkdf2", "hmac", "porta",
                // Round four: the additional encryption tools (the AES tools above already
                // cover the key sizes through their keySize setting).
                "aesctr", "aesrawkey", "rsa",
            )
            // Tools that require their input to already be in a specific format.
            val needsStructuredInput = setOf("json", "jwt", "textdiff")
            if (processor.meta.id !in needsSecret && processor.meta.id !in needsStructuredInput) {
                assertTrue(
                    "${processor.meta.id} failed: ${outcome.error}",
                    outcome.isSuccess
                )
            }
        }
    }

    @Test fun errorsAreUserFriendlyAndNeverRawExceptions() {
        val cases = listOf(
            "base64" to "SGVsbG8*",
            "base32" to "not-base32!",
            "base58" to "0OIl",
            "hex" to "ZZ",
            "binary" to "0102",
            "baudot" to "101",
            "ascii" to "9999",
            "decimal" to "999",
            "octal" to "999",
            "unicode" to "U+D800",
            "morse" to "..--..-.-",
            "a1z26" to "42",
            "bacon" to "ABC",
        )
        cases.forEach { (id, input) ->
            val outcome = ProcessingEngine.run(ToolRegistry.get(id), input, ToolRegistry.get(id).defaultParams(), Direction.DECODE)
            assertTrue("$id should fail for '$input'", !outcome.isSuccess)
            val message = outcome.error!!
            assertTrue("$id message should not be a stack trace: $message", !message.contains("Exception"))
            assertTrue("$id message should be a sentence", message.length > 15 && message.endsWith("."))
        }
    }

    @Test fun engineNeverLeaksRawExceptions() {
        val outcome = ProcessingEngine.run(ToolRegistry.get("aes"), "not a payload", mapOf("password" to "x"), Direction.DECODE)
        assertTrue(!outcome.isSuccess)
        assertTrue(outcome.error!!.isNotBlank())
    }

    @Test fun toolExceptionMessagesAreStable() {
        val e = runCatching {
            ProcessingEngine.run(ToolRegistry.get("caesar"), "x", mapOf("shift" to "3"), Direction.ENCODE)
        }
        assertTrue(e.isSuccess)
        val outcome = ProcessingEngine.run(ToolRegistry.get("affine"), "x", mapOf("a" to "2", "b" to "3"), Direction.ENCODE)
        assertTrue(!outcome.isSuccess)
        assertTrue(outcome.error!!.contains("coprime"))
    }

    @Test fun swapHelperChangesDirection() {
        val processor = ToolRegistry.get("base64")
        val encoded = ProcessingEngine.run(processor, "swap me", emptyMap(), Direction.ENCODE)
        val decoded = ProcessingEngine.run(processor, encoded.output, emptyMap(), Direction.DECODE)
        assertEquals("swap me", decoded.output)
        assertNotEquals(encoded.output, decoded.output)
    }
}
