package com.texthub.core.util

import com.texthub.core.model.ToolException

/**
 * A small, dependency-free JSON parser and writer.
 *
 * Text Hub formats and inspects JSON locally, so it carries its own parser instead of pulling in
 * a library. Numbers are kept as the text they were written as, which means reformatting never
 * changes a value.
 */
object Json {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw parser.error("Unexpected text after the end of the document")
        return value
    }

    fun write(value: Any?, indent: String?): String {
        val sb = StringBuilder()
        writeValue(value, sb, indent, 0)
        return sb.toString()
    }

    /** Every key path in the document, in the order they appear, e.g. `user.address.city`. */
    fun keyPaths(value: Any?): List<String> {
        val out = ArrayList<String>()
        fun walk(node: Any?, prefix: String) {
            when (node) {
                is Map<*, *> -> node.forEach { (key, child) ->
                    val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                    out.add(path)
                    walk(child, path)
                }
                is List<*> -> node.forEachIndexed { index, child ->
                    walk(child, "$prefix[$index]")
                }
                else -> Unit
            }
        }
        walk(value, "")
        return out
    }

    private fun writeValue(value: Any?, sb: StringBuilder, indent: String?, depth: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(value)
            is NumberLiteral -> sb.append(value.text)
            is String -> sb.append(quote(value))
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                val newline = indent != null
                sb.append('{')
                var first = true
                value.forEach { (key, child) ->
                    if (!first) sb.append(',')
                    first = false
                    if (newline) {
                        sb.append('\n')
                        repeat(depth + 1) { sb.append(indent) }
                    } else if (!first) {
                        // no separator space in compact mode beyond the comma
                    } else Unit
                    sb.append(quote(key.toString()))
                    sb.append(':')
                    if (newline) sb.append(' ')
                    writeValue(child, sb, indent, depth + 1)
                }
                if (newline) {
                    sb.append('\n')
                    repeat(depth) { sb.append(indent) }
                }
                sb.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    sb.append("[]")
                    return
                }
                val newline = indent != null
                sb.append('[')
                value.forEachIndexed { index, child ->
                    if (index > 0) sb.append(',')
                    if (newline) {
                        sb.append('\n')
                        repeat(depth + 1) { sb.append(indent) }
                    }
                    writeValue(child, sb, indent, depth + 1)
                }
                if (newline) {
                    sb.append('\n')
                    repeat(depth) { sb.append(indent) }
                }
                sb.append(']')
            }
            else -> sb.append(value.toString())
        }
    }

    private fun quote(text: String): String {
        val sb = StringBuilder(text.length + 2)
        sb.append('"')
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** Numbers keep their original spelling so big integers and decimals survive a reformat. */
    private class NumberLiteral(val text: String) {
        override fun toString(): String = text
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun error(message: String): ToolException {
            val line = text.take(index).count { it == '\n' } + 1
            val column = index - (text.lastIndexOf('\n', index - 1) + 1) + 1
            return ToolException("$message at line $line, column $column.")
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw error("The document ends before a value")
            return when (val c = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> if (c == '-' || c.isDigit()) readNumber() else throw error("Unexpected character '$c'")
            }
        }

        private fun expect(word: String) {
            if (!text.startsWith(word, index)) throw error("Expected \"$word\"")
            index += word.length
        }

        private fun readObject(): Map<String, Any?> {
            index++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && text[index] == '}') {
                index++
                return map
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[index] != '"') throw error("Object keys must be quoted strings")
                val key = readString()
                skipWhitespace()
                if (atEnd() || text[index] != ':') throw error("Expected ':' after the key \"$key\"")
                index++
                map[key] = readValue()
                skipWhitespace()
                if (atEnd()) throw error("The object is missing its closing brace")
                when (text[index]) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return map
                    }
                    else -> throw error("Expected ',' or '}' in the object")
                }
            }
        }

        private fun readArray(): List<Any?> {
            index++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && text[index] == ']') {
                index++
                return list
            }
            while (true) {
                list.add(readValue())
                skipWhitespace()
                if (atEnd()) throw error("The array is missing its closing bracket")
                when (text[index]) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return list
                    }
                    else -> throw error("Expected ',' or ']' in the array")
                }
            }
        }

        private fun readString(): String {
            index++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw error("The string is not closed")
                val c = text[index++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw error("The string ends with an incomplete escape")
                        when (val escape = text[index++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw error("Incomplete \\u escape")
                                val hex = text.substring(index, index + 4)
                                val value = hex.toIntOrNull(16) ?: throw error("Invalid \\u escape: $hex")
                                sb.append(value.toChar())
                                index += 4
                            }
                            else -> throw error("Unknown escape \\$escape")
                        }
                    }
                    c < ' ' -> throw error("A raw control character is not allowed inside a string")
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): NumberLiteral {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && text[index].isDigit()) index++
            if (index < text.length && text[index] == '.') {
                index++
                while (index < text.length && text[index].isDigit()) index++
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                while (index < text.length && text[index].isDigit()) index++
            }
            val raw = text.substring(start, index)
            if (raw.isEmpty() || raw == "-") throw error("Expected a number")
            return NumberLiteral(raw)
        }
    }
}
