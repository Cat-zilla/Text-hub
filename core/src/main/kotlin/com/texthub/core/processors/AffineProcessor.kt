package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.gcd
import com.texthub.core.util.modInverse
import com.texthub.core.util.modPositive

/**
 * Affine cipher: E(x) = (A*x + B) mod 26, D(x) = A⁻¹(x - B) mod 26.
 * A must be coprime with 26 so that the modular inverse exists.
 */
class AffineProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "affine",
        name = "Affine Cipher",
        glyph = "AFF",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "a",
                label = "A (multiplier)",
                kind = ParamKind.NUMBER,
                defaultValue = "5",
                min = 1,
                max = 25,
                helper = "Must be coprime with 26.",
            ),
            ParamSpec(
                key = "b",
                label = "B (shift)",
                kind = ParamKind.NUMBER,
                defaultValue = "8",
                min = 0,
                max = 25,
            ),
        ),
        info = ToolInfo(
            summary = "The Affine cipher combines multiplication and shifting: each letter is " +
                "mapped with E(x) = (A·x + B) mod 26.",
            requiresKey = true,
            useCases = listOf(
                "Modular arithmetic practice",
                "Classical cipher comparison",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "A must be coprime with 26 (1, 3, 5, 7, 9, 11, 15, 17, 19, 21, 23, 25).",
        ),
        keywords = listOf("affine", "modular", "multiplicative"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val a = params["a"]?.toIntOrNull() ?: 5
        val b = params["b"]?.toIntOrNull() ?: 8
        if (gcd(a, 26) != 1) throw Errors.affine()
        val aInv = modInverse(a, 26) ?: throw Errors.affine()
        val bNorm = b.modPositive(26)

        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in 'a'..'z' -> {
                    val x = c - 'a'
                    val v = if (direction == Direction.ENCODE) (a * x + bNorm) % 26
                    else (aInv * (x - bNorm)).modPositive(26)
                    sb.append(('a'.code + v).toChar())
                }
                in 'A'..'Z' -> {
                    val x = c - 'A'
                    val v = if (direction == Direction.ENCODE) (a * x + bNorm) % 26
                    else (aInv * (x - bNorm)).modPositive(26)
                    sb.append(('A'.code + v).toChar())
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
