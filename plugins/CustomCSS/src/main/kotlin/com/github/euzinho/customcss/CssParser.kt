package com.github.euzinho.customcss

/** Parte de um seletor: `Tipo#id.classe.outra` (qualquer parte é opcional). `*` = tudo. */
class Compound(val type: String?, val id: String?, val classes: List<String>) {
    val isUniversal: Boolean get() = type == null && id == null && classes.isEmpty()
    val specificity: Int
        get() = (if (id != null) 100 else 0) + classes.size * 10 + (if (type != null) 1 else 0)
}

/** Seletor completo. Várias partes separadas por espaço = descendente (`.a TextView`). */
class Selector(val parts: List<Compound>) {
    val specificity: Int = parts.sumOf { it.specificity }
}

class Declaration(val property: String, val value: String, val important: Boolean)

class Rule(val selectors: List<Selector>, val declarations: List<Declaration>, val order: Int, val line: Int)

class ParseError(val line: Int, val message: String, val warning: Boolean = false)

class ParseResult(val rules: List<Rule>, val errors: List<ParseError>) {
    val hasErrors: Boolean get() = errors.any { !it.warning }
}

/**
 * Parser de um subconjunto de CSS:
 *
 *   :root { --cor: #5865f2; }
 *   .message-author, TextView#algum_id { color: var(--cor); font-weight: bold; }
 *
 * Sem regras aninhadas, sem @-regras, sem pseudo-classes.
 */
object CssParser {
    val KNOWN_PROPERTIES = setOf(
        "color", "background", "background-color", "opacity",
        "font-size", "font-weight", "font-style", "font-family", "letter-spacing",
        "border-radius", "border",
        "padding", "padding-left", "padding-right", "padding-top", "padding-bottom",
        "display", "visibility", "tint",
    )

    private val PART = Regex("^(\\*|[A-Za-z_][\\w-]*)?((?:[#.][\\w-]+)*)$")
    private val TOKEN = Regex("([#.])([\\w-]+)")
    private val VAR = Regex("var\\(\\s*(--[\\w-]+)\\s*(?:,\\s*([^)]*))?\\)")

    private class Block(val selector: String, val body: String, val selectorLine: Int, val bodyOffset: Int)
    private class RawDecl(val name: String, val value: String, val line: Int)

    fun parse(source: String): ParseResult {
        val errors = ArrayList<ParseError>()
        val text = stripComments(source)
        val blocks = ArrayList<Block>()

        var i = 0
        while (i < text.length) {
            val open = text.indexOf('{', i)
            if (open == -1) {
                if (text.substring(i).isNotBlank()) {
                    errors.add(ParseError(lineAt(text, firstNonSpace(text, i)), "Texto fora de uma regra (faltou '{'?)"))
                }
                break
            }
            val close = text.indexOf('}', open + 1)
            if (close == -1) {
                errors.add(ParseError(lineAt(text, open), "Falta fechar a regra com '}'"))
                break
            }
            blocks.add(
                Block(
                    text.substring(i, open).trim(),
                    text.substring(open + 1, close),
                    lineAt(text, firstNonSpace(text, i)),
                    open + 1,
                ),
            )
            i = close + 1
        }

        // 1) variáveis do :root
        val vars = LinkedHashMap<String, String>()
        for (b in blocks) {
            if (b.selector != ":root") continue
            for (d in rawDecls(text, b, ArrayList())) {
                if (d.name.startsWith("--")) vars[d.name] = d.value
            }
        }

        // 2) regras
        val rules = ArrayList<Rule>()
        var order = 0
        for (b in blocks) {
            if (b.selector == ":root") continue
            if (b.body.contains('{')) {
                errors.add(ParseError(b.selectorLine, "Regras aninhadas não são suportadas"))
                continue
            }
            if (b.selector.isEmpty()) {
                errors.add(ParseError(b.selectorLine, "Regra sem seletor"))
                continue
            }
            val selectors = ArrayList<Selector>()
            for (s in b.selector.split(',')) {
                val sel = parseSelector(s.trim())
                if (sel == null) {
                    errors.add(ParseError(b.selectorLine, "Seletor inválido: '${s.trim()}'"))
                } else {
                    selectors.add(sel)
                }
            }
            if (selectors.isEmpty()) continue

            val universal = selectors.any { it.parts.size == 1 && it.parts[0].isUniversal }
            val decls = ArrayList<Declaration>()
            for (d in rawDecls(text, b, errors)) {
                if (d.name.startsWith("--")) continue
                if (d.name !in KNOWN_PROPERTIES) {
                    errors.add(ParseError(d.line, "Propriedade desconhecida: '${d.name}'", true))
                    continue
                }
                var value = resolveVars(d.value, vars)
                var important = false
                if (value.contains("!important")) {
                    important = true
                    value = value.replace("!important", "").trim()
                }
                val problem = validate(d.name, value)
                if (problem != null) {
                    errors.add(ParseError(d.line, problem, true))
                    continue
                }
                if (universal && (d.name == "display" || d.name == "visibility")) {
                    errors.add(ParseError(d.line, "'${d.name}' não é permitido no seletor '*' (esconderia o app inteiro)", true))
                    continue
                }
                decls.add(Declaration(d.name, value, important))
            }
            rules.add(Rule(selectors, decls, order++, b.selectorLine))
        }
        return ParseResult(rules, errors)
    }

    private fun parseSelector(text: String): Selector? {
        if (text.isEmpty()) return null
        val parts = ArrayList<Compound>()
        for (raw in text.replace(">", " ").split(' ', '\t', '\n', '\r')) {
            if (raw.isEmpty()) continue
            parts.add(parseCompound(raw) ?: return null)
        }
        return if (parts.isEmpty()) null else Selector(parts)
    }

    private fun parseCompound(p: String): Compound? {
        val m = PART.matchEntire(p) ?: return null
        val typeRaw = m.groupValues[1]
        val type = if (typeRaw.isEmpty() || typeRaw == "*") null else typeRaw
        var id: String? = null
        val classes = ArrayList<String>()
        for (t in TOKEN.findAll(m.groupValues[2])) {
            if (t.groupValues[1] == "#") id = t.groupValues[2] else classes.add(t.groupValues[2])
        }
        if (type == null && id == null && classes.isEmpty() && typeRaw != "*") return null
        return Compound(type, id, classes)
    }

    private fun rawDecls(text: String, b: Block, errors: ArrayList<ParseError>): List<RawDecl> {
        val out = ArrayList<RawDecl>()
        var off = b.bodyOffset
        for (chunk in b.body.split(';')) {
            val start = off
            off += chunk.length + 1
            if (chunk.isBlank()) continue
            val line = lineAt(text, start + (chunk.length - chunk.trimStart().length))
            val colon = chunk.indexOf(':')
            if (colon <= 0) {
                errors.add(ParseError(line, "Declaração inválida: '${chunk.trim()}' (use propriedade: valor;)"))
                continue
            }
            out.add(RawDecl(chunk.substring(0, colon).trim().lowercase(), chunk.substring(colon + 1).trim(), line))
        }
        return out
    }

    private fun resolveVars(value: String, vars: Map<String, String>): String {
        var v = value
        var guard = 0
        while (guard++ < 6 && v.contains("var(")) {
            v = VAR.replace(v) { m ->
                val name = m.groupValues[1].lowercase()
                vars[name] ?: m.groupValues[2].trim()
            }
        }
        return v
    }

    private fun validate(name: String, value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return "Valor vazio em '$name'"
        return when (name) {
            "color", "background-color", "tint" ->
                if (CssValues.parseColor(v) == null) "Cor inválida: '$v'" else null
            "background" ->
                if (v.lowercase() == "none" || CssValues.parseColor(v) != null || CssValues.parseGradient(v) != null) {
                    null
                } else {
                    "Valor inválido para background: '$v' (use cor ou linear-gradient(...))"
                }
            "opacity" -> if (CssValues.parseNumber(v) == null) "Número inválido: '$v'" else null
            "font-size", "letter-spacing", "padding-left", "padding-right", "padding-top", "padding-bottom" ->
                if (CssValues.parseLength(v) == null) "Tamanho inválido: '$v'" else null
            "border-radius" -> if (CssValues.parseRadius(v) == null) "Raio inválido: '$v'" else null
            "padding" -> if (CssValues.parseBox(v) == null) "Padding inválido: '$v'" else null
            "border" ->
                if (v.lowercase() == "none" || CssValues.parseBorder(v) != null) null else "Borda inválida: '$v' (ex: 2px solid #fff)"
            "display" -> null
            "visibility" -> null
            "font-weight" -> if (CssValues.isBold(v) == null) "font-weight inválido: '$v'" else null
            else -> null
        }
    }

    private fun stripComments(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s.startsWith("/*", i)) {
                val end = s.indexOf("*/", i + 2)
                val stop = if (end == -1) s.length else end + 2
                for (j in i until stop) sb.append(if (s[j] == '\n') '\n' else ' ')
                i = stop
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun firstNonSpace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return if (i >= text.length) from else i
    }

    private fun lineAt(text: String, index: Int): Int {
        var line = 1
        val end = minOf(index, text.length)
        for (k in 0 until end) if (text[k] == '\n') line++
        return line
    }
}

class Gradient(val angle: Int, val colors: IntArray) {
    val signature: String get() = "$angle:" + colors.joinToString(",")
}

/** Conversão de valores CSS (cores, tamanhos, gradientes...). Sem dependência de Android. */
object CssValues {
    private val NAMED = mapOf(
        "black" to 0xFF000000.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF008000.toInt(),
        "lime" to 0xFF00FF00.toInt(),
        "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(),
        "cyan" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(),
        "gray" to 0xFF808080.toInt(),
        "grey" to 0xFF808080.toInt(),
        "orange" to 0xFFFFA500.toInt(),
        "purple" to 0xFF800080.toInt(),
        "pink" to 0xFFFFC0CB.toInt(),
        "transparent" to 0x00000000,
    )
    private val NUM = Regex("[-+]?\\d*\\.?\\d+%?")
    private val LENGTH = Regex("^(-?\\d*\\.?\\d+)(px|dp|dip|sp|em|pt)?$")

    fun splitTop(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val cur = StringBuilder()
        for (ch in s) {
            when {
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth--; cur.append(ch) }
                ch == sep && depth == 0 -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString())
        return out
    }

    private fun splitSpaces(s: String): List<String> =
        splitTop(s.replace('\t', ' ').replace('\n', ' '), ' ').filter { it.isNotEmpty() }

    fun parseColor(raw: String): Int? {
        val s = raw.trim().lowercase()
        NAMED[s]?.let { return it }
        if (s.startsWith("#")) {
            var h = s.substring(1)
            if (h.isEmpty() || !h.all { it in '0'..'9' || it in 'a'..'f' }) return null
            if (h.length == 3 || h.length == 4) h = h.map { "$it$it" }.joinToString("")
            return when (h.length) {
                6 -> (0xFF000000L or h.toLong(16)).toInt()
                8 -> {
                    val v = h.toLong(16)
                    (((v and 0xFF) shl 24) or (v shr 8)).toInt()
                }
                else -> null
            }
        }
        if (s.startsWith("rgb") && s.contains('(')) {
            val nums = NUM.findAll(s.substringAfter('(')).map { it.value }.toList()
            if (nums.size < 3) return null
            val c = IntArray(3)
            for (k in 0..2) {
                val t = nums[k]
                val n = t.removeSuffix("%").toFloatOrNull() ?: return null
                c[k] = (if (t.endsWith("%")) n * 2.55f else n).toInt().coerceIn(0, 255)
            }
            var a = 255
            if (nums.size >= 4) {
                val t = nums[3]
                val n = t.removeSuffix("%").toFloatOrNull() ?: return null
                a = ((if (t.endsWith("%")) n / 100f else n) * 255f).toInt().coerceIn(0, 255)
            }
            return (a shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
        }
        return null
    }

    /** "12", "12px", "12dp", "12sp", "0.05em" -> número (a unidade é tratada pela engine). */
    fun parseLength(raw: String): Float? {
        val m = LENGTH.matchEntire(raw.trim().lowercase()) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }

    /** Raio: número em dp; '%' vira "pílula" (50% = círculo). */
    fun parseRadius(raw: String): Float? {
        val s = raw.trim()
        if (s.endsWith("%")) return if (s.removeSuffix("%").trim().toFloatOrNull() != null) 9999f else null
        return parseLength(s)
    }

    fun parseNumber(raw: String): Float? {
        val s = raw.trim()
        if (s.endsWith("%")) return s.removeSuffix("%").toFloatOrNull()?.div(100f)
        return s.toFloatOrNull()
    }

    /** padding: 1 a 4 valores -> [top, right, bottom, left] */
    fun parseBox(raw: String): FloatArray? {
        val parts = splitSpaces(raw).map { parseLength(it) ?: return null }
        return when (parts.size) {
            1 -> floatArrayOf(parts[0], parts[0], parts[0], parts[0])
            2 -> floatArrayOf(parts[0], parts[1], parts[0], parts[1])
            3 -> floatArrayOf(parts[0], parts[1], parts[2], parts[1])
            4 -> floatArrayOf(parts[0], parts[1], parts[2], parts[3])
            else -> null
        }
    }

    /** "2px solid #fff" -> (largura, cor). */
    fun parseBorder(raw: String): Pair<Float, Int>? {
        var width: Float? = null
        var color: Int? = null
        for (t in splitSpaces(raw)) {
            val low = t.lowercase()
            if (low == "solid") continue
            val len = parseLength(t)
            val col = parseColor(t)
            when {
                len != null && width == null -> width = len
                col != null && color == null -> color = col
                else -> return null
            }
        }
        val w = width ?: return null
        return Pair(w, color ?: 0xFF888888.toInt())
    }

    /** true = bold, false = normal, null = inválido. */
    fun isBold(raw: String): Boolean? {
        val s = raw.trim().lowercase()
        return when {
            s == "bold" || s == "bolder" -> true
            s == "normal" || s == "lighter" -> false
            else -> s.toIntOrNull()?.let { it >= 600 }
        }
    }

    fun parseGradient(raw: String): Gradient? {
        val s = raw.trim()
        if (!s.lowercase().startsWith("linear-gradient(") || !s.endsWith(")")) return null
        val inner = s.substring(s.indexOf('(') + 1, s.length - 1)
        val args = splitTop(inner, ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (args.size < 2) return null

        var angle = 180
        var first = 0
        val head = args[0].lowercase()
        if (head.startsWith("to ")) {
            val words = head.substring(3).split(' ').filter { it.isNotEmpty() }.toSet()
            angle = when (words) {
                setOf("top") -> 0
                setOf("right") -> 90
                setOf("bottom") -> 180
                setOf("left") -> 270
                setOf("top", "right") -> 45
                setOf("bottom", "right") -> 135
                setOf("bottom", "left") -> 225
                setOf("top", "left") -> 315
                else -> return null
            }
            first = 1
        } else if (head.endsWith("deg")) {
            angle = head.removeSuffix("deg").toFloatOrNull()?.toInt() ?: return null
            first = 1
        }
        angle = ((angle % 360) + 360) % 360

        val colors = ArrayList<Int>()
        for (a in args.drop(first)) {
            val token = splitSpaces(a).firstOrNull() ?: return null
            colors.add(parseColor(token) ?: return null)
        }
        if (colors.size < 2) return null
        return Gradient(angle, colors.toIntArray())
    }
}
