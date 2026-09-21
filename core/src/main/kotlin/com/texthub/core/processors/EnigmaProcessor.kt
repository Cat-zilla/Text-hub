package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * Enigma machine engine (three-rotor Wehrmacht Enigma I, the M3 arrangement).
 *
 * Rotor and reflector wiring, notch positions and the stepping rules are the historical ones, so
 * the machine reproduces the published reference result: rotors I-II-III, reflector B, rings and
 * positions all at A, and `AAAAA` encrypts to `BDZGO`.
 *
 * Only three rotors are used, as in the basic Wehrmacht Enigma: the left (slow), middle and right
 * (fast) rotor. Stepping follows the real double-step anomaly: the middle rotor turns when the
 * right rotor leaves a notch position, and also when it is itself standing on its own notch.
 *
 * The engine works on a whole message at once (it is a text tool, not a keyboard), which means no
 * rotor positions are ever transmitted. Real operators had to send the starting positions
 * separately, and that procedural weakness is one of the things that broke Enigma in practice.
 */
internal class EnigmaMachine(
    rotorNames: List<String>,
    private val reflectorName: String,
    rings: String,
    private val positions: String,
    private val plugboard: String,
) {

    private val offsets = IntArray(3) // ring settings, 0 = A
    private val pos = IntArray(3) // current window position, 0 = A
    private val wires = arrayOfNulls<IntArray>(3)
    private val notches = IntArray(3)
    private val plug = IntArray(26) { it }

    init {
        for (i in 0 until 3) {
            val name = rotorNames[i].uppercase()
            val wiring = WIRING[name] ?: throw Errors.enigma()
            // Wiring is given as "which letter does A step into" but a signal enters at a contact,
            // so the table is inverted into a pass-through mapping.
            val forward = IntArray(26)
            wiring.forEachIndexed { index, c -> forward[index] = c - 'A' }
            wires[i] = forward
            notches[i] = NOTCH[name] ?: throw Errors.enigma()
            offsets[i] = rings[i] - 'A'
            pos[i] = positions[i] - 'A'
        }
        for (pair in plugboard.split(Regex("\\s+")).filter { it.isNotBlank() }) {
            if (pair.length != 2 || !pair[0].isLetter() || !pair[1].isLetter()) throw Errors.enigmaPlugboard()
            val a = pair[0].uppercaseChar() - 'A'
            val b = pair[1].uppercaseChar() - 'A'
            if (a == b) throw Errors.enigmaPlugboard()
            plug[a] = b
            plug[b] = a
        }
    }

    fun reset() {
        for (i in 0 until 3) pos[i] = positions[i] - 'A'
    }

    /** Encrypts one letter, stepping the rotors first exactly as the real machine did. */
    fun step(positionLetter: Char): Char {
        // 1. The right rotor always steps; the middle rotor steps twice when it is on its notch.
        if (pos[1] == notches[1]) {
            pos[1] = (pos[1] + 1) % 26
            pos[0] = (pos[0] + 1) % 26
        } else if (pos[2] == notches[2]) {
            pos[1] = (pos[1] + 1) % 26
        }
        pos[2] = (pos[2] + 1) % 26

        var c = positionLetter - 'A'
        c = plug[c]

        // 2. Through the rotors right to left (3 -> 2 -> 1).
        for (i in 2 downTo 0) c = through(i, c, forward = true)

        // 3. The reflector turns the signal around.
        val ukw = REFLECTORS[reflectorName] ?: throw Errors.enigma()
        c = ukw[c] - 'A'

        // 4. Back through the rotors left to right (1 -> 2 -> 3).
        for (i in 0..2) c = through(i, c, forward = false)

        c = plug[c]
        return ('A'.code + c).toChar()
    }

    /**
     * Passes a letter through one rotor in the machine's fixed frame.
     *
     * The wiring table lives in the rotor's own frame; the window position rotates it and the ring
     * setting slides the alphabet ring against it, so the net rotation is (position - ring). This
     * is the convention that reproduces the published reference vectors: rotor I with a B ring
     * setting encodes A as K, and rotors I-II-III with all settings at A turn AAAAA into BDZGO.
     */
    private fun through(rotor: Int, c: Int, forward: Boolean): Int {
        val shift = ((pos[rotor] - offsets[rotor]) % 26 + 26) % 26
        val shifted = (c + shift) % 26
        val wired = if (forward) wires[rotor]!![shifted] else wires[rotor]!!.indexOf(shifted)
        return ((wired - shift) % 26 + 26) % 26
    }

    companion object {
        val ROTORS = listOf("I", "II", "III", "IV", "V")

        /** Rotor wiring, written as the standard "A is wired to E, B to K, ..." table. */
        val WIRING = mapOf(
            "I" to "EKMFLGDQVZNTOWYHXUSPAIBRCJ",
            "II" to "AJDKSIRUXBLHWTMCQGZNPYFVOE",
            "III" to "BDFHJLCPRTXVZNYEIWGAKMUSQO",
            "IV" to "ESOVPZJAYQUIRHXLNFTGKDCMWB",
            "V" to "VZBRGITYUPSDNHLXAWMJQOFECK",
        )

        /** Turnover notch of each rotor, as a window position index. */
        val NOTCH = mapOf("I" to ('Q' - 'A'), "II" to ('E' - 'A'), "III" to ('V' - 'A'),
            "IV" to ('J' - 'A'), "V" to ('Z' - 'A'))

        val REFLECTORS = mapOf(
            "B" to "YRUHQSLDPXNGOKMIEBFZCWVJAT",
            "C" to "FVPJIAOYEDRZXWGCTKUQSBNMHL",
        )
    }
}

/**
 * Enigma I (three rotors, reflectors B/C, plugboard). Included as a full historical simulation
 * with the real stepping behaviour rather than a simplified substitution.
 */
class EnigmaProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "enigma",
        name = "Enigma Machine",
        glyph = "ENI",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        symmetric = true,
        params = listOf(
            rotorParam("rotorLeft", "Left rotor (slow)", "I"),
            rotorParam("rotorMiddle", "Middle rotor", "II"),
            rotorParam("rotorRight", "Right rotor (fast)", "III"),
            ParamSpec(
                key = "reflector",
                label = "Reflector",
                kind = ParamKind.CHOICE,
                defaultValue = "B",
                choices = listOf(Choice("B", "UKW-B (standard)"), Choice("C", "UKW-C")),
            ),
            ParamSpec(
                key = "rings",
                label = "Ring settings",
                kind = ParamKind.TEXT,
                defaultValue = "AAA",
                hint = "Three letters, e.g. AAA",
                sensitive = true,
                helper = "Ringstellung: rotates the wiring inside each rotor.",
            ),
            ParamSpec(
                key = "positions",
                label = "Start positions",
                kind = ParamKind.TEXT,
                defaultValue = "AAA",
                hint = "Three letters, e.g. AAA",
                sensitive = true,
                helper = "Grundstellung: what the windows show before the first key.",
            ),
            ParamSpec(
                key = "plugboard",
                label = "Plugboard pairs",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Optional, e.g. AB CD EF",
                sensitive = true,
                helper = "Steckerbrett: up to 10 swapped letter pairs, separated by spaces.",
            ),
        ),
        info = ToolInfo(
            summary = "A working three-rotor Enigma I: historical rotor wiring I-V, reflectors " +
                "UKW-B and UKW-C, ring settings, start positions and the plugboard. Rotors step " +
                "on every letter, including the real double-step anomaly, so the same letter " +
                "encrypts differently each time.",
            requiresKey = true,
            useCases = listOf(
                "Learning how rotor machines actually stepped",
                "Reproducing the classic reference message AAAAA → BDZGO",
            ),
            warnings = listOf(
                "A classical cipher - not modern secure encryption. The reflector guarantees that " +
                    "no letter ever encrypts to itself, which is what let Bletchley Park break it.",
                "Both sides must use identical rotor order, rings, positions and plugboard.",
            ),
            convention = "Letters only; spaces, digits and punctuation are preserved unchanged.",
        ),
        keywords = listOf("enigma", "rotor", "wehrmacht", "plugboard", "reflector", "turing"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val rings = (params["rings"] ?: "").uppercase().filter { it in 'A'..'Z' }
        val positions = (params["positions"] ?: "").uppercase().filter { it in 'A'..'Z' }
        if (rings.length != 3 || positions.length != 3) throw Errors.enigma()
        val machine = EnigmaMachine(
            rotorNames = listOf(
                params["rotorLeft"] ?: "I",
                params["rotorMiddle"] ?: "II",
                params["rotorRight"] ?: "III",
            ),
            reflectorName = params["reflector"] ?: "B",
            rings = rings,
            positions = positions,
            plugboard = params["plugboard"] ?: "",
        )
        val sb = StringBuilder(input.length)
        for (raw in input) {
            val upper = raw.uppercaseChar()
            if (upper !in 'A'..'Z') {
                sb.append(raw)
                continue
            }
            val out = machine.step(upper)
            sb.append(if (raw.isUpperCase()) out else out.lowercaseChar())
        }
        return sb.toString()
    }

    private fun rotorParam(key: String, label: String, default: String) = ParamSpec(
        key = key,
        label = label,
        kind = ParamKind.CHOICE,
        defaultValue = default,
        choices = EnigmaMachine.ROTORS.map { Choice(it, "Rotor $it") },
    )
}

/**
 * Vigenère and Gronsfeld with an arbitrary alphabet. Both variants and both directions are
 * rolled into one tool so the difference is one switch instead of a second tool.
 */
class KeyedAlphabetProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "vigenere2",
        name = "Vigenère / Beaufort (custom)",
        glyph = "V2",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Variant",
                kind = ParamKind.CHOICE,
                defaultValue = "vigenere",
                choices = listOf(
                    Choice("vigenere", "Vigenère (C = P + K)"),
                    Choice("beaufort", "Beaufort (C = K − P)"),
                    Choice("variant", "Variant Beaufort (C = P − K)"),
                ),
            ),
            ParamSpec(
                key = "key",
                label = "Secret key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Letters only, e.g. LEMON",
                sensitive = true,
            ),
            ParamSpec(
                key = "alphabet",
                label = "Alphabet",
                kind = ParamKind.TEXT,
                defaultValue = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                hint = "Any order, e.g. QWERTYUIOPASDFGHJKLZXCVBNM",
                helper = "Rearranging the alphabet shifts the whole keystream, which is a classic extra secret.",
            ),
        ),
        info = ToolInfo(
            summary = "The same polyalphabetic engine with a custom alphabet order, so a keyword " +
                "and a scrambled alphabet are both needed to read the message. Covers the three " +
                "classic forms: Vigenère, Beaufort and Variant Beaufort.",
            requiresKey = true,
            useCases = listOf(
                "Taking the Vigenère family beyond the plain A-Z alphabet",
                "Comparing the three related variants side by side",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "A scrambled keyed alphabet behaves like a substitution layer under a Vigenère cascade.",
        ),
        keywords = listOf("vigenere", "beaufort", "keyed alphabet", "custom alphabet", "polyalphabetic"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val alphabet = (params["alphabet"] ?: "")
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .toList()
            .distinct()
            .joinToString("")
        if (alphabet.length < 2) throw Errors.cipherParams()
        val key = (params["key"] ?: "").uppercase().filter { it in alphabet }
        if (key.isEmpty()) throw Errors.missingKey()
        val variant = params["variant"] ?: "vigenere"
        var i = 0
        val sb = StringBuilder(input.length)
        for (c in input) {
            val upper = c.uppercaseChar()
            val p = alphabet.indexOf(upper)
            if (p < 0) {
                sb.append(c)
                continue
            }
            val k = alphabet.indexOf(key[i % key.length])
            val size = alphabet.length
            val value = when {
                variant == "beaufort" -> (k - p).mod(size)
                variant == "variant" && direction == Direction.ENCODE -> (p - k).mod(size)
                variant == "variant" -> (p + k) % size
                direction == Direction.ENCODE -> (p + k) % size
                else -> (p - k).mod(size)
            }
            val mapped = alphabet[value]
            sb.append(if (c.isUpperCase()) mapped else mapped.lowercaseChar())
            i++
        }
        return sb.toString()
    }

    private fun Int.mod(size: Int): Int = ((this % size) + size) % size
}
