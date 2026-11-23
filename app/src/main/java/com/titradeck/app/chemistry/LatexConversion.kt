package com.titradeck.app.chemistry

/** A fragment of original text and, where needed, its equivalent TeX math expression. */
data class LatexPart(val text: String, val latex: String? = null)

private val subscriptCharacters = mapOf(
    '₀' to "0", '₁' to "1", '₂' to "2", '₃' to "3", '₄' to "4", '₅' to "5", '₆' to "6", '₇' to "7", '₈' to "8", '₉' to "9",
    'ₐ' to "a", 'ₑ' to "e", 'ₕ' to "h", 'ᵢ' to "i", 'ⱼ' to "j", 'ₖ' to "k", 'ₗ' to "l", 'ₘ' to "m", 'ₙ' to "n", 'ₒ' to "o", 'ₚ' to "p", 'ᵣ' to "r", 'ₛ' to "s", 'ₜ' to "t", 'ᵤ' to "u", 'ᵥ' to "v", 'ₓ' to "x", 'ᵦ' to "\\beta", 'ᵧ' to "\\gamma",
)
private val superscriptCharacters = mapOf(
    '⁰' to "0", '¹' to "1", '²' to "2", '³' to "3", '⁴' to "4", '⁵' to "5", '⁶' to "6", '⁷' to "7", '⁸' to "8", '⁹' to "9", '⁺' to "+", '⁻' to "-", '⁽' to "(", '⁾' to ")", 'ⁿ' to "n",
)
private val mathSymbols = mapOf(
    'α' to "\\alpha ", 'β' to "\\beta ", 'γ' to "\\gamma ", 'Δ' to "\\Delta ", 'δ' to "\\delta ",
    'η' to "\\eta ", 'λ' to "\\lambda ", 'ν' to "\\nu ", 'ξ' to "\\xi ", 'π' to "\\pi ", 'φ' to "\\phi ",
    'Σ' to "\\Sigma ", '∑' to "\\sum ", '∏' to "\\prod ", '∞' to "\\infty ", '√' to "\\surd ",
    '×' to "\\times ", '÷' to "\\div ", '·' to "\\cdot ", '−' to "-", '±' to "\\pm ",
    '≈' to "\\approx ", '≤' to "\\leq ", '≥' to "\\geq ", '∝' to "\\propto ",
    '→' to "\\rightarrow ", '⇌' to "\\rightleftharpoons ", '↔' to "\\leftrightarrow ", '°' to "^{\\circ}",
)

/** Converts a mathematical fragment to TeX while preserving adjacent multi-digit scripts. */
fun mathTextToLatex(text: String): String {
    val output = StringBuilder()
    var position = 0
    while (position < text.length) {
        val character = text[position]
        val script = when (character) {
            in subscriptCharacters -> subscriptCharacters
            in superscriptCharacters -> superscriptCharacters
            else -> null
        }
        if (script != null) {
            if (position == 0) output.append("{}")
            output.append(if (script === subscriptCharacters) "_{" else "^{")
            while (position < text.length) {
                val scriptCharacter = script[text[position]]
                if (scriptCharacter != null) {
                    output.append(scriptCharacter)
                    position++
                } else if (text[position] == ',' && position + 1 < text.length && text[position + 1] in script) {
                    output.append(',')
                    position++
                } else break
            }
            output.append('}')
            continue
        }
        if (character == '^' && position + 1 < text.length) {
            val start = position + 1
            val end = if (text[start] == '(') {
                val closing = text.indexOf(')', start + 1)
                if (closing >= 0) closing + 1 else start + 1
            } else {
                var next = start
                while (next < text.length && (text[next].isLetterOrDigit() || text[next] == '−' || text[next] == '-')) next++
                maxOf(start + 1, next)
            }
            output.append("^{").append(mathTextToLatex(text.substring(start, end))).append('}')
            position = end
            continue
        }
        if (character.isLetter() && character.code < 128) {
            val start = position
            while (position < text.length && text[position].isLetter() && text[position].code < 128) position++
            output.append("\\mathsf{").append(text.substring(start, position)).append('}')
            continue
        }
        output.append(mathSymbols[character] ?: when (character) {
            '%' -> "\\%"
            '#' -> "\\#"
            '&' -> "\\&"
            '_' -> "\\_"
            '{' -> "\\{"
            '}' -> "\\}"
            '\\' -> "\\backslash "
            else -> character.toString()
        })
        position++
    }
    return output.toString()
}

/** Splits mixed Chinese prose and math so that only the math needs a TeX font. */
fun latexParts(text: String, forceFormula: Boolean = false): List<LatexPart> {
    val mathCharacters = subscriptCharacters.keys + superscriptCharacters.keys + mathSymbols.keys
    fun isMathCharacter(character: Char): Boolean = character.code < 128 && (character.isLetterOrDigit() || character in "()[]+*/=.,:^-%") || character in mathCharacters
    val parts = mutableListOf<LatexPart>()
    var start = 0
    while (start < text.length) {
        val mathematical = isMathCharacter(text[start])
        var end = start + 1
        while (end < text.length && isMathCharacter(text[end]) == mathematical) end++
        val fragment = text.substring(start, end)
        val hasScript = fragment.any { it in subscriptCharacters || it in superscriptCharacters }
        parts += LatexPart(fragment, if (mathematical && (forceFormula || hasScript)) mathTextToLatex(fragment) else null)
        start = end
    }
    return parts
}

/** Converts rendered mathematical notation to text suitable for the clipboard. */
fun mathTextForClipboard(text: String): String {
    val subscripts = "0123456789+-=()aehijklmnoprstuvxβγ".zip("₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓᵦᵧ").toMap()
    val superscripts = "0123456789+-=()abcdefghijklmnoprstuvwxyzABDEGHIJKLMNOPRTUVW".zip(
        "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂ"
    ).toMap()
    val commands = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "Delta" to "Δ", "delta" to "δ",
        "eta" to "η", "lambda" to "λ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "phi" to "φ",
        "Sigma" to "Σ", "sum" to "∑", "prod" to "∏", "infty" to "∞", "surd" to "√",
        "times" to "×", "div" to "÷", "cdot" to "·", "pm" to "±", "approx" to "≈",
        "leq" to "≤", "geq" to "≥", "propto" to "∝", "rightarrow" to "→",
        "rightleftharpoons" to "⇌", "leftrightarrow" to "↔", "circ" to "°"
    )

    fun convert(source: String): String {
        val output = StringBuilder()
        var position = 0
        fun group(start: Int, open: Char, close: Char): Int {
            var depth = 0
            for (index in start until source.length) {
                if (source[index] == open) depth++
                if (source[index] == close && --depth == 0) return index
            }
            return -1
        }
        while (position < source.length) {
            val character = source[position]
            if ((character == '_' || character == '^') && position + 1 < source.length) {
                val start = position + 1
                val closing = when (source[start]) {
                    '{' -> group(start, '{', '}')
                    '(' -> if (character == '^') group(start, '(', ')') else -1
                    else -> -1
                }
                val end = if (closing >= 0) closing + 1 else {
                    var next = start
                    while (next < source.length && (source[next].isLetterOrDigit() || source[next] in "+-−")) next++
                    next
                }
                if (end > start) {
                    val raw = source.substring(start, end)
                    val content = if (raw.startsWith('{') && raw.endsWith('}')) raw.substring(1, raw.length - 1) else raw
                    val parsed = convert(content).replace('−', '-')
                    val alphabet = if (character == '_') subscripts else superscripts
                    if (character == '^' && parsed == "°") {
                        output.append('°')
                    } else if (parsed.isNotEmpty() && parsed.all { it in alphabet || it == ',' }) {
                        parsed.forEach { output.append(alphabet[it] ?: it) }
                    } else {
                        output.append(character).append(raw)
                    }
                    position = end
                    continue
                }
            }
            if (character == '\\' && position + 1 < source.length) {
                val start = position + 1
                var end = start
                while (end < source.length && source[end].isLetter()) end++
                if (end == start) {
                    output.append(source[start]); position = start + 1; continue
                }
                val command = source.substring(start, end)
                val symbol = commands[command]
                if (symbol != null) {
                    output.append(symbol)
                    position = if (end < source.length && source[end] == ' ') end + 1 else end
                    continue
                }
                if (command in setOf("mathsf", "mathrm", "mathbf", "boldsymbol", "text", "ce") && end < source.length && source[end] == '{') {
                    val closing = group(end, '{', '}')
                    if (closing >= 0) {
                        output.append(convert(source.substring(end + 1, closing)))
                        position = closing + 1
                        continue
                    }
                }
                if (command == "frac" && end < source.length && source[end] == '{') {
                    val numeratorEnd = group(end, '{', '}')
                    val denominatorStart = numeratorEnd + 1
                    if (numeratorEnd >= 0 && denominatorStart < source.length && source[denominatorStart] == '{') {
                        val denominatorEnd = group(denominatorStart, '{', '}')
                        if (denominatorEnd >= 0) {
                            output.append('(').append(convert(source.substring(end + 1, numeratorEnd))).append(")/(")
                                .append(convert(source.substring(denominatorStart + 1, denominatorEnd))).append(')')
                            position = denominatorEnd + 1
                            continue
                        }
                    }
                }
                output.append(source.substring(position, end))
                position = end
                continue
            }
            output.append(character)
            position++
        }
        return output.toString()
    }
    return convert(text)
}
