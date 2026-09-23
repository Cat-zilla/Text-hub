package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ProcessOutcome
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import com.texthub.core.model.ToolMeta
import com.texthub.core.processors.A1Z26Processor
import com.texthub.core.processors.AesCbcProcessor
import com.texthub.core.processors.AesCtrProcessor
import com.texthub.core.processors.AesRawKeyProcessor
import com.texthub.core.processors.AesProcessor
import com.texthub.core.processors.AffineProcessor
import com.texthub.core.processors.AsciiProcessor
import com.texthub.core.processors.AtbashProcessor
import com.texthub.core.processors.BaconProcessor
import com.texthub.core.processors.AutokeyProcessor
import com.texthub.core.processors.Base45Processor
import com.texthub.core.processors.BeaufortProcessor
import com.texthub.core.processors.BifidProcessor
import com.texthub.core.processors.CaesarBruteForceProcessor
import com.texthub.core.processors.CaseConverterProcessor
import com.texthub.core.processors.ChaChaProcessor
import com.texthub.core.processors.GronsfeldProcessor
import com.texthub.core.processors.HillCipherProcessor
import com.texthub.core.processors.HtmlEntityProcessor
import com.texthub.core.processors.LeetProcessor
import com.texthub.core.processors.LineToolsProcessor
import com.texthub.core.processors.NatoPhoneticProcessor
import com.texthub.core.processors.PolybiusProcessor
import com.texthub.core.processors.QuotedPrintableProcessor
import com.texthub.core.processors.UnicodeEscapeProcessor
import com.texthub.core.processors.AdfgxProcessor
import com.texthub.core.processors.Base91Processor
import com.texthub.core.processors.BrailleProcessor
import com.texthub.core.processors.ChecksumProcessor
import com.texthub.core.processors.EnigmaProcessor
import com.texthub.core.processors.HashProcessor
import com.texthub.core.processors.HexDumpProcessor
import com.texthub.core.processors.Hill3Processor
import com.texthub.core.processors.HmacProcessor
import com.texthub.core.processors.JsonProcessor
import com.texthub.core.processors.JwtProcessor
import com.texthub.core.processors.KeyedAlphabetProcessor
import com.texthub.core.processors.Pbkdf2Processor
import com.texthub.core.processors.PortaProcessor
import com.texthub.core.processors.PunycodeProcessor
import com.texthub.core.processors.RegexProcessor
import com.texthub.core.processors.RsaKeyGenProcessor
import com.texthub.core.processors.RsaProcessor
import com.texthub.core.processors.RomanProcessor
import com.texthub.core.processors.ScytaleProcessor
import com.texthub.core.processors.TextDiffProcessor
import com.texthub.core.processors.TextStatsProcessor
import com.texthub.core.processors.TrifidProcessor
import com.texthub.core.processors.Utf16Processor
import com.texthub.core.processors.UniversalDecoderProcessor
import com.texthub.core.processors.Utf32Processor
import com.texthub.core.processors.Base32Processor
import com.texthub.core.processors.Base58Processor
import com.texthub.core.processors.Base64Processor
import com.texthub.core.processors.Base85Processor
import com.texthub.core.processors.BaudotProcessor
import com.texthub.core.processors.BinaryProcessor
import com.texthub.core.processors.CaesarProcessor
import com.texthub.core.processors.ColumnarTranspositionProcessor
import com.texthub.core.processors.DecimalProcessor
import com.texthub.core.processors.HexProcessor
import com.texthub.core.processors.MorseProcessor
import com.texthub.core.processors.OctalProcessor
import com.texthub.core.processors.PlayfairProcessor
import com.texthub.core.processors.RailFenceProcessor
import com.texthub.core.processors.ReverseProcessor
import com.texthub.core.processors.Rot13Processor
import com.texthub.core.processors.Rot18Processor
import com.texthub.core.processors.Rot47Processor
import com.texthub.core.processors.SubstitutionProcessor
import com.texthub.core.processors.UnicodeProcessor
import com.texthub.core.processors.UrlProcessor
import com.texthub.core.processors.VigenereProcessor
import com.texthub.core.processors.XorProcessor

/**
 * Registry of every available method.
 *
 * To add a tool: write a [TextProcessor] and add it to [all] - nothing else has to change.
 */
object ToolRegistry {

    /**
     * Every registered tool in presentation order.
     *
     * The list is sorted by [ToolMeta.pinnedFirst], so a tool that must lead the picker does so
     * because of its own metadata rather than because of where it happens to sit in this file. The
     * sort is stable, so the order of the remaining tools is exactly the order below.
     */
    private val registered: List<TextProcessor> = listOf(
        // Smart tools: the dispatcher that recognises a format and hands the work to the tool for
        // it. It leads the list, and favourites order never affects this list.
        UniversalDecoderProcessor(),
        // Secure encryption - all authenticated constructions using platform primitives.
        // The AES tools take the key size (128/192/256) as a setting and detect it again on
        // decrypt; then AES-CTR, raw-key AES-GCM and the RSA hybrid.
        AesProcessor(),
        AesCbcProcessor(),
        ChaChaProcessor(),
        AesCtrProcessor(),
        AesRawKeyProcessor(),
        RsaProcessor(),
        RsaKeyGenProcessor(),
        HashProcessor(),
        HmacProcessor(),
        Pbkdf2Processor(),
        ChecksumProcessor(),
        // Classical ciphers
        CaesarProcessor(),
        VigenereProcessor(),
        AtbashProcessor(),
        SubstitutionProcessor(),
        Rot13Processor(),
        Rot47Processor(),
        Rot18Processor(),
        AffineProcessor(),
        PlayfairProcessor(),
        RailFenceProcessor(),
        ColumnarTranspositionProcessor(),
        BaconProcessor(),
        A1Z26Processor(),
        BeaufortProcessor(),
        AutokeyProcessor(),
        GronsfeldProcessor(),
        HillCipherProcessor(),
        BifidProcessor(),
        PolybiusProcessor(),
        CaesarBruteForceProcessor(),
        Hill3Processor(),
        PortaProcessor(),
        TrifidProcessor(),
        ScytaleProcessor(),
        AdfgxProcessor(),
        EnigmaProcessor(),
        KeyedAlphabetProcessor(),
        // Encodings
        Base64Processor(),
        Base32Processor(),
        Base58Processor(),
        Base85Processor(),
        UrlProcessor(),
        HexProcessor(),
        BinaryProcessor(),
        AsciiProcessor(),
        DecimalProcessor(),
        OctalProcessor(),
        UnicodeProcessor(),
        MorseProcessor(),
        BaudotProcessor(),
        Base45Processor(),
        QuotedPrintableProcessor(),
        HtmlEntityProcessor(),
        UnicodeEscapeProcessor(),
        NatoPhoneticProcessor(),
        Base91Processor(),
        PunycodeProcessor(),
        BrailleProcessor(),
        RomanProcessor(),
        Utf16Processor(),
        Utf32Processor(),
        HexDumpProcessor(),
        // Transformations
        ReverseProcessor(),
        CaseConverterProcessor(),
        LineToolsProcessor(),
        LeetProcessor(),
        JsonProcessor(),
        RegexProcessor(),
        TextDiffProcessor(),
        TextStatsProcessor(),
        JwtProcessor(),
        XorProcessor(),
    )

    val all: List<TextProcessor> = registered.sortedWith(compareByDescending { it.meta.pinnedFirst })

    private val byId: Map<String, TextProcessor> = all.associateBy { it.meta.id }

    fun get(id: String): TextProcessor = byId[id] ?: byId.getValue("base64")

    fun metaOf(id: String): ToolMeta = get(id).meta

    fun metas(): List<ToolMeta> = all.map { it.meta }

    fun byCategory(category: ToolCategory): List<ToolMeta> =
        all.map { it.meta }.filter { it.category == category }

    /** Case-insensitive instant search over name, keywords, category and classification. */
    fun search(query: String): List<ToolMeta> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return metas()
        return all.map { it.meta }.filter { it.searchIndex.contains(q) }
    }
}

/** Executes a processor and converts any failure into a friendly message. */
object ProcessingEngine {

    fun run(
        processor: TextProcessor,
        input: String,
        params: Map<String, String>,
        direction: Direction,
    ): ProcessOutcome {
        val start = System.nanoTime()
        return try {
            val output = processor.process(input, params, direction)
            ProcessOutcome(
                output = output,
                error = null,
                durationMs = (System.nanoTime() - start) / 1_000_000,
                inputChars = input.length,
            )
        } catch (e: ToolException) {
            ProcessOutcome(error = e.message ?: "That input could not be processed.", durationMs = 0)
        } catch (e: Exception) {
            // Never surface a raw stack trace to the user, and never blame the cipher settings:
            // this branch means the processor failed for a reason it did not describe, which has
            // nothing to do with keys or parameters. (Before 1.6.1 every unexpected failure was
            // reported as "these cipher settings are not valid", which is how a decoding bug in one
            // format looked like a problem with the password the user had typed into another one.)
            ProcessOutcome(
                error = Errors.unexpectedFailure().message,
                durationMs = 0,
            )
        }
    }
}
