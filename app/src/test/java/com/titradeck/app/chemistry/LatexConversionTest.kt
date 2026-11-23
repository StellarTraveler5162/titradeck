package com.titradeck.app.chemistry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexConversionTest {
    @Test fun chemicalScriptsAndCoefficientsBecomeTex() {
        assertEquals("2\\mathsf{H}_{2}\\mathsf{O}", mathTextToLatex("2H₂O"))
        assertEquals("\\mathsf{SO}_{4}^{2-}", mathTextToLatex("SO₄²⁻"))
        assertEquals("\\mathsf{CuSO}_{4}\\cdot 5\\mathsf{H}_{2}\\mathsf{O}", mathTextToLatex("CuSO₄·5H₂O"))
        assertEquals("10^{-3}", mathTextToLatex("10⁻³"))
        assertEquals("\\mathsf{A}_{r,i}", mathTextToLatex("Aᵣ,ᵢ"))
    }

    @Test fun mixedTextPreservesChineseAndSplitsLongEquationsAtSpaces() {
        val parts = latexParts("配平：2 H₂ + O₂ → 2 H₂O")
        assertEquals("配平：2 H₂ + O₂ → 2 H₂O", parts.joinToString("") { it.text })
        assertTrue(parts.any { it.text == "H₂" && it.latex == "\\mathsf{H}_{2}" })
        assertTrue(parts.any { it.text == "O₂" && it.latex == "\\mathsf{O}_{2}" })
        assertTrue(parts.filter { it.latex != null }.all { !it.text.contains(' ') })
    }

    @Test fun formulaModeRendersOrdinaryMathFragments() {
        val parts = latexParts("PV = nRT；c₁V₁ = c₂V₂", forceFormula = true)
        assertEquals("\\mathsf{PV}", parts.first().latex)
        assertTrue(parts.any { it.latex == "\\mathsf{c}_{1}\\mathsf{V}_{1}" })
        assertEquals("10^{(-\\mathsf{pKw})}", mathTextToLatex("10^(−pKw)"))
    }

    @Test fun clipboardTextPreservesChemicalScriptsAndConvertsTex() {
        assertEquals("2 H₂ + O₂ → 2 H₂O", mathTextForClipboard("2 H₂ + O₂ → 2 H₂O"))
        assertEquals("H₂O + SO₄²⁻", mathTextForClipboard("\\mathsf{H}_{2}\\mathsf{O} + \\mathrm{SO}_{4}^{2-}"))
        assertEquals("10⁽⁻ᵖᴷʷ⁾", mathTextForClipboard("10^(−pKw)"))
        assertEquals("(H₂)/(O₂)", mathTextForClipboard("\\frac{H_{2}}{O_{2}}"))
        assertEquals("Aᵣ,ᵢ = 10⁻³", mathTextForClipboard("A_{r,i} = 10^{-3}"))
        assertEquals("x_{abc} + x^{q}", mathTextForClipboard("x_{abc} + x^{q}"))
    }
}
