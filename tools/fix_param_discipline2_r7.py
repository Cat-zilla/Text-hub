import io

# ============================================================ A1Z26: tolerant, strict decode
p = "core/src/main/kotlin/com/texthub/core/processors/A1Z26Processor.kt"
s = io.open(p, encoding="utf-8").read()
start = s.index("        // Numbers become letters. Number separators are dropped, a word separator becomes a")
end = s.index("        return sb.toString().trimEnd()\n    }\n}", start) + len("        return sb.toString().trimEnd()\n    }\n}")
new = '''        // Numbers become letters. The decode side deliberately ignores the separator settings and
        // accepts every separator people actually use (spaces, dashes, commas, dots, slashes,
        // pipes, semicolons): a separator is presentation, not data, so a mismatched setting must
        // not change the answer. A number outside 1-26 is refused instead of being guessed at.
        val sb = StringBuilder()
        var atWordBreak = false
        for (m in Regex("\\\\d+|[^\\\\d]+").findAll(input)) {
            val token = m.value
            if (token.all { it.isDigit() }) {
                val value = token.toIntOrNull() ?: throw Errors.a1z26()
                if (value !in 1..26) throw Errors.a1z26()
                if (atWordBreak && sb.isNotEmpty()) sb.append(' ')
                atWordBreak = false
                sb.append(('A'.code + value - 1).toChar())
            } else {
                if (token.any { it == '/' || it == '|' || it == '\\n' }) atWordBreak = true
                val literal = token.filterNot { it.isDigit() || it.isWhitespace() || it in "-.,;:/|" || it == '\\n' }
                if (literal.isNotEmpty()) {
                    if (atWordBreak && sb.isNotEmpty()) sb.append(' ')
                    atWordBreak = false
                    sb.append(literal.replace(Regex("\\\\s+"), " "))
                }
            }
        }
        return sb.toString().trimEnd()
    }
}'''
s = s[:start] + new + s[end:]
io.open(p, "w", encoding="utf-8").write(s)
print("a1z26 decode made separator-tolerant")

# ============================================================ base58 / base85: text-only results
p = "core/src/main/kotlin/com/texthub/core/processors/BaseFamilyProcessors.kt"
if not io.os.path.exists(p):
    import glob
    candidates = [f for f in glob.glob("core/src/main/kotlin/com/texthub/core/processors/*.kt")
                  if "Base58Codec" in io.open(f, encoding="utf-8").read()]
    p = candidates[0]
s = io.open(p, encoding="utf-8").read()
print("base58/85 processor file:", p)
print([l for l in s.splitlines() if "Base58Codec.decode" in l or "Base85Codec.decode" in l])
io.open(p, "w", encoding="utf-8").write(s)
