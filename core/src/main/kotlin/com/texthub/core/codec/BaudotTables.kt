package com.texthub.core.codec

/**
 * Baudot / International Telegraph Alphabet No. 2 (ITA2) tables.
 *
 * Values are the standard CCITT (1932) ITA2 assignments together with the American
 * Teletypewriter (US-TTY) figure set, which differs for a handful of code points.
 * Sources: CCITT/ITU-T S.1 (ITA2) and the widely published US-TTY variant.
 *
 * Control characters keep their real code values (NUL, BEL, ENQ, CR, LF) so that a
 * round trip through the app reproduces the original text exactly. For typing
 * convenience the *encoder* also accepts the names <NUL>, <BEL>, <ENQ>, <CR>, <LF>.
 */
object BaudotTables {

    const val NUL = '\u0000'
    const val BEL = '\u0007'
    const val ENQ = '\u0005'
    const val CR = '\r'
    const val LF = '\n'
    const val SP = ' '

    const val LTRS_CODE = 0b11111 // 31
    const val FIGS_CODE = 0b11011 // 27

    enum class Variant(val id: String, val label: String) {
        ITA2("ita2", "ITA2 (International)"),
        US_TTY("ustty", "US TTY (American)"),
    }

    fun variantFor(id: String): Variant = Variant.values().firstOrNull { it.id == id } ?: Variant.ITA2

    /** Letter shift page - identical for ITA2 and US-TTY. */
    val LETTERS: Array<Char> = arrayOf(
        NUL, 'E', LF, 'A', SP, 'S', 'I', 'U',
        CR, 'D', 'R', 'J', 'N', 'F', 'C', 'K',
        'T', 'Z', 'L', 'W', 'H', 'Y', 'P', 'Q',
        'O', 'B', 'G', NUL /*FIGS*/, 'M', 'X', 'V', NUL /*LTRS*/,
    )

    /** ITA2 figure shift page. */
    private val FIGURES_ITA2: Array<Char> = arrayOf(
        NUL, '3', LF, '-', SP, '\'', '8', '7',
        CR, ENQ, '4', BEL, ',', '!', ':', '(',
        '5', '+', ')', '2', '£', '6', '0', '1',
        '9', '?', '&', NUL /*FIGS*/, '.', '/', '=', NUL /*LTRS*/,
    )

    /** US-TTY figure shift page: 0x05/0x0B and a few symbols differ from ITA2. */
    private val FIGURES_US: Array<Char> = arrayOf(
        NUL, '3', LF, '-', SP, BEL, '8', '7',
        CR, '$', '4', '\'', ',', '!', ':', '(',
        '5', '"', ')', '2', '#', '6', '0', '1',
        '9', '?', '&', NUL /*FIGS*/, '.', '/', ';', NUL /*LTRS*/,
    )

    fun figures(variant: Variant): Array<Char> =
        if (variant == Variant.US_TTY) FIGURES_US else FIGURES_ITA2

    /** Named forms accepted on the text -> Baudot side for the control characters. */
    fun namedControl(text: String): Char? = when (text.uppercase()) {
        "<NUL>", "<NULL>" -> NUL
        "<BEL>", "<BELL>" -> BEL
        "<ENQ>" -> ENQ
        "<CR>" -> CR
        "<LF>" -> LF
        "<SP>", "<SPACE>" -> SP
        else -> null
    }

    fun displayName(c: Char): String = when (c) {
        NUL -> "<NUL>"
        BEL -> "<BEL>"
        ENQ -> "<ENQ>"
        CR -> "<CR>"
        LF -> "<LF>"
        else -> c.toString()
    }
}
