package com.titradeck.app.chemistry

/** Formats a single chemical species for display without changing its stored representation. */
fun displayChemicalFormula(formula: String): String {
    val parsed = runCatching { FormulaParser.parse(formula) }.getOrNull()
    val source = parsed?.let { it.normalizedFormula + (it.state?.let { state -> "($state)" } ?: "") } ?: formula
    val subscript = "₀₁₂₃₄₅₆₇₈₉"
    val superscript = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    val result = StringBuilder()
    var charge = false
    var atomCount = false
    for (character in source) {
        when {
            character == '^' -> charge = true
            charge && character in '0'..'9' -> result.append(superscript[character - '0'])
            charge && character == '+' -> result.append('⁺')
            charge && character == '-' -> result.append('⁻')
            charge -> { charge = false; result.append(character) }
            character in '0'..'9' && atomCount -> result.append(subscript[character - '0'])
            character in '0'..'9' -> result.append(character)
            else -> {
                result.append(character)
                atomCount = character.isLetter() || character == ')' || character == ']'
            }
        }
    }
    return result.toString()
}

fun displayChemicalEquation(equation: BalancedEquation): String {
    fun side(start: Int, entries: List<EquationSpecies>) = entries.mapIndexed { index, entry ->
        val coefficient = equation.coefficients[start + index]
        (if (coefficient == 1) "" else "$coefficient ") + displayChemicalFormula(entry.formula)
    }.joinToString(" + ")
    return side(0, equation.reactants) + " → " + side(equation.reactants.size, equation.products)
}

/** Present older saved results with the same notation without changing their stored values. */
fun displaySavedResult(toolId: String, inputs: Map<String, String>, result: CalculationResult): CalculationResult {
    val formulas = mutableSetOf<String>()
    fun add(raw: String) {
        runCatching { FormulaParser.parse(raw) }.getOrNull()?.let { parsed ->
            formulas += parsed.normalizedFormula
            parsed.state?.let { formulas += parsed.normalizedFormula + "($it)" }
        }
    }
    when (toolId) {
        "balance", "stoichiometry" -> runCatching { EquationBalancer.balance(inputs["equation"].orEmpty()) }.getOrNull()
            ?.species?.forEach { formulas += it.formula; formulas += it.parsed.normalizedFormula }
        "molar_mass", "unsaturation" -> add(inputs["formula"].orEmpty())
    }
    if (toolId in setOf("empirical", "unsaturation", "organic_lookup")) {
        result.rows.filter { it.label.contains("分子式") || it.label == "经验式" }.forEach { add(it.value) }
        if (toolId == "empirical") add(result.value)
    }
    val replacements = formulas.sortedByDescending(String::length).map { raw ->
        Regex("(?<![A-Za-z0-9₀-₉])" + Regex.escape(raw) + "(?![A-Za-z0-9₀-₉])") to displayChemicalFormula(raw)
    }
    fun convert(text: String): String = replacements.fold(text) { current, (pattern, formatted) -> pattern.replace(current) { formatted } }
    return result.copy(
        headline = convert(result.headline), value = convert(result.value),
        rows = result.rows.map { if (it.label.contains("SMILES")) it else it.copy(label = convert(it.label), value = convert(it.value)) },
        steps = result.steps.map(::convert), notes = result.notes.map(::convert),
    )
}
