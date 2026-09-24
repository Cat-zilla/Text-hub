package com.texthub.core

import com.texthub.core.model.ParamKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Clear sensitive fields": only parameters a tool declares sensitive are affected, every other
 * value stays, and the session copy kept while "clear when leaving a tool" is off holds exactly the
 * non-empty secrets. Exercised against the real registry, so a tool that forgets to declare a
 * password sensitive would show up here.
 */
class SensitiveFieldClearingTest {

    private val aes = ToolRegistry.metaOf("aes")
    private val rsa = ToolRegistry.metaOf("rsa")

    private fun sensitiveKey(toolId: String): String =
        ToolRegistry.metaOf(toolId).params.first { it.sensitive }.key

    @Test fun everyPasswordParameterInTheRegistryIsSensitive() {
        ToolRegistry.all.flatMap { it.meta.params }.filter { it.kind == ParamKind.PASSWORD }.forEach {
            assertTrue("${it.key} is a password and must be sensitive", it.sensitive)
        }
    }

    @Test fun clearingResetsOnlySensitiveParameters() {
        val key = sensitiveKey("aes")
        val other = aes.params.first { !it.sensitive }
        val before = mapOf(key to "hunter2", other.key to "custom")
        val after = aes.withSensitiveCleared(before)
        assertEquals(aes.params.first { it.key == key }.defaultValue, after[key])
        assertEquals("custom", after[other.key])
        assertEquals(before.keys, after.keys)
    }

    @Test fun clearingIsIdempotentAndSafeOnEmptyValues() {
        val key = sensitiveKey("aes")
        assertEquals(emptyMap<String, String>(), aes.withSensitiveCleared(emptyMap()))
        val once = aes.withSensitiveCleared(mapOf(key to "x"))
        assertEquals(once, aes.withSensitiveCleared(once))
    }

    @Test fun unknownKeysAreLeftAlone() {
        val after = aes.withSensitiveCleared(mapOf("not_a_param" to "value"))
        assertEquals("value", after["not_a_param"])
    }

    @Test fun sessionCopyHoldsExactlyTheNonEmptySecrets() {
        val key = sensitiveKey("rsa")
        val other = rsa.params.first { !it.sensitive }
        assertEquals(mapOf(key to "-----BEGIN PRIVATE KEY-----"), rsa.sensitiveValues(mapOf(key to "-----BEGIN PRIVATE KEY-----", other.key to "v")))
        assertTrue(rsa.sensitiveValues(mapOf(key to "", other.key to "v")).isEmpty())
    }

    @Test fun clearedValuesContainNoTraceOfTheSecret() {
        val secret = "correct horse battery staple"
        ToolRegistry.all.map { it.meta }.filter { m -> m.params.any { it.sensitive } }.forEach { meta ->
            val filled = meta.params.associate { it.key to if (it.sensitive) secret else it.defaultValue }
            val cleared = meta.withSensitiveCleared(filled)
            cleared.values.forEach { assertFalse("${meta.id} leaked a secret after clearing", it.contains(secret)) }
        }
    }
}
