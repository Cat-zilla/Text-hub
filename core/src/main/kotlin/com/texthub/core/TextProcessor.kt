package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolMeta

/**
 * Every algorithm is an independent processor. Adding a new method = write one class
 * in `com.texthub.core.processors` and register it in [com.texthub.core.ToolRegistry].
 *
 * Processors are pure: they receive text + parameters and return text. They must never
 * log plaintext, keys or ciphertext, and must throw
 * [com.texthub.core.model.ToolException] for user-correctable problems.
 */
interface TextProcessor {
    val meta: ToolMeta

    /**
     * @param input raw input text
     * @param params resolved parameter values (always contains every key of [ToolMeta.params])
     * @param direction forward or reverse operation
     */
    fun process(input: String, params: Map<String, String>, direction: Direction): String
}

/** Convenience: default parameter map for a processor. */
fun TextProcessor.defaultParams(): Map<String, String> =
    meta.params.associate { it.key to it.defaultValue }
