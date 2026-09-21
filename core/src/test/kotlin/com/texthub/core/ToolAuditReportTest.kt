package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import org.junit.Test
import java.io.File

/**
 * Writes a full per-tool report to `build/tool-audit.txt` so the whole registry (all 73 tools,
 * their parameters, their behaviour on a sample input and whether a round trip works) can be
 * reviewed in one place. Always passes unless a tool is undocumented or unfindable.
 */
class ToolAuditReportTest {

    @Test fun writeTheReport() {
        val lines = mutableListOf<String>()
        var fullyDocumented = 0
        val problems = mutableListOf<String>()
        for (processor in ToolRegistry.all) {
            val meta = processor.meta
            val defaults = processor.defaultParams()
            val documented = meta.info.summary.isNotBlank() && meta.info.useCases.isNotEmpty()
            val findable = ToolRegistry.search(meta.id).any { it.id == meta.id } &&
                ToolRegistry.search(meta.name).any { it.id == meta.id }
            if (documented && findable) fullyDocumented++ else problems += meta.id
            val sample = "hello"
            val enc = ProcessingEngine.run(processor, sample, defaults, Direction.ENCODE)
            val dec = if (enc.isSuccess && enc.output.isNotEmpty()) {
                ProcessingEngine.run(processor, enc.output, defaults, Direction.DECODE)
            } else {
                null
            }
            lines += "%-16s %-9s rt=%-3s  %s".format(
                meta.id, meta.category.name, if (dec?.isSuccess == true && dec.output == sample) "yes" else "-",
                if (enc.isSuccess) "out=${enc.output.take(28)}" else "err=${enc.error?.take(40)}",
            )
        }
        val report = buildString {
            appendLine("Text Hub tool audit — ${ToolRegistry.all.size} tools")
            appendLine("documented + findable: $fullyDocumented/${ToolRegistry.all.size}")
            appendLine("-".repeat(120))
            lines.forEach { appendLine(it) }
            if (problems.isNotEmpty()) {
                appendLine()
                appendLine("PROBLEMS: " + problems.joinToString(", "))
            }
        }
        File("build/tool-audit.txt").writeText(report)
        org.junit.Assert.assertTrue("undocumented or unfindable tools: $problems", problems.isEmpty())
    }
}
