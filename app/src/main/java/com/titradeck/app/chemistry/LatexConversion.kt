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
