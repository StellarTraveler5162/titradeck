package com.titradeck.app.chemistry

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PhysicalMathTest {
    private fun near(expected: Double, actual: Double, relative: Double = 1e-8) {
        assertEquals(expected, actual, max(abs(expected) * relative, 1e-300))
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { /* expected */ }
    }
    private fun tool(id: String, vararg values: Pair<String, String>): CalculationResult {
        val spec = PhysicalTools.tools.single { it.id == id }
        return spec.calculate(spec.fields.associate { it.key to it.defaultValue } + values)
    }

    @Test fun concentrationMassBalanceAndRoundTrips() {
        // 10% NaCl by mass, density 1.07 g/mL: 107 g NaCl + 963 g water per litre.
        val c = PhysicalMath.concentration(10.0, "w", 58.44, 1.07, 1.0)
        near(107.0 / 58.44, c.molarity)
        near((107.0 / 58.44) / 0.963, c.molality)
        near(0.1, c.massFraction)
        near(c.molarity, PhysicalMath.concentration(c.molality, "m", 58.44, 1.07, 1.0).molarity)
        near(0.5, PhysicalMath.concentration(1.0, "N", 98.079, 1.03, 2.0).molarity)
    }
    @Test fun concentrationRejectsImpossibleSolventMass() {
        rejected { PhysicalMath.concentration(30.0, "M", 58.44, 1.0, 1.0) }
        rejected { PhysicalMath.concentration(100.0, "w", 58.44, 1.0, 1.0) }
        rejected { PhysicalMath.concentration(1.0, "N", 58.44, 1.0, 0.0) }
    }
    @Test fun strongAcidsAndBasesRetainWaterAutoionization() {
        near(2.0, PhysicalMath.acidBase(0.01, "strong_acid").ph)
        near(12.0, PhysicalMath.acidBase(0.01, "strong_base").ph)
        val dilute = PhysicalMath.acidBase(1e-9, "strong_acid")
        assertTrue(dilute.ph < 7.0 && dilute.ph > 6.99)
        near(7.0, PhysicalMath.acidBase(0.0, "strong_acid").ph)
        near(6.0, PhysicalMath.acidBase(0.0, "strong_base", pKw = 12.0).ph)
        near(1e-14, dilute.hydrogen * dilute.hydroxide)
    }
    @Test fun aceticAcidSatisfiesEquilibriumAndChargeBalance() {
        val concentration = 0.1
        val ka = 1.8e-5
        val result = PhysicalMath.acidBase(concentration, "weak_acid", -log10(ka))
        near(2.87527706, result.ph, 1e-8)
        val conjugate = result.hydrogen - result.hydroxide
        near(ka, result.hydrogen * conjugate / (concentration - conjugate))
        near(conjugate / concentration, result.ionizedFraction!!)
    }
    @Test fun weakBaseAndExtremeDilutionAreStable() {
        val base = PhysicalMath.acidBase(0.1, "weak_base", -log10(1.8e-5))
        near(14.0 - 2.87527706, base.ph, 1e-8)
        val dilute = PhysicalMath.acidBase(1e-15, "weak_acid", 30.0)
        near(7.0, dilute.ph)
        near(1e-23, dilute.ionizedFraction!!)
        rejected { PhysicalMath.acidBase(-1.0, "weak_acid") }
        rejected { PhysicalMath.acidBase(1.0, "weak_acid", Double.NaN) }
    }
    @Test fun standardIdealGasAndAllUnknowns() {
        val result = PhysicalMath.gas("v", 101325.0, 0.0, 1.0, 273.15)
        near(0.022413969545, result.volume)
        near(1.0, PhysicalMath.gas("n", 101325.0, result.volume, 0.0, 273.15).amount)
        near(273.15, PhysicalMath.gas("t", 101325.0, result.volume, 1.0, 0.0).temperature)
        near(101325.0, PhysicalMath.gas("p", 0.0, result.volume, 1.0, 273.15).pressure)
        near(22.413969545, tool("ideal_gas", "unknown" to "v", "t" to "0", "tUnit" to "C").value.toDouble())
        rejected { PhysicalMath.gas("v", 0.0, 0.0, 1.0, 273.15) }
        rejected { tool("ideal_gas", "t" to "-274", "tUnit" to "C") }
    }
    @Test fun unitConversionsRespectDimensionsAndOffsets() {
        near(1000.0, PhysicalMath.convert(1.0, "g", "mg"))
        near(0.001, PhysicalMath.convert(1.0, "mL", "L"))
        near(PhysicalMath.NA, PhysicalMath.convert(1.0, "mol", "particles"))
        near(1.0, PhysicalMath.convert(PhysicalMath.NA, "particles", "mol"))
        near(298.15, PhysicalMath.convert(25.0, "C", "K"))
        near(-273.15, PhysicalMath.convert(0.0, "K", "C"))
        near(58.44, PhysicalMath.convert(1.0, "M", "g/L", 58.44))
        near(1.0, PhysicalMath.convert(58.44, "mg/L", "mM", 58.44))
        rejected { PhysicalMath.convert(1.0, "g", "L") }
        rejected { PhysicalMath.convert(1.0, "M", "g/L") }
        rejected { PhysicalMath.convert(-274.0, "C", "K") }
    }
    @Test fun nernstHydrogenElectrodeAndElectrolysis() {
        val potential = PhysicalMath.nernst(0.0, 2.0, 298.15, 14.0 * ln(10.0))
        near(-0.4141154478, potential)
        near(potential, tool("nernst", "e0" to "0", "mode" to "ph", "ph" to "7", "hExponent" to "-2").value.toDouble())
        val deposited = tool("electrolysis", "current" to "1", "time" to PhysicalMath.F.toString(), "timeUnit" to "s", "electrons" to "2", "molarMass" to "63.546").value.toDouble()
        near(31.773, deposited)
        rejected { PhysicalMath.nernst(0.0, 0.0, 298.15, 0.0) }
        rejected { tool("electrolysis", "efficiency" to "120") }
    }
    @Test fun equilibriumConversionIsReversible() {
        val kp = PhysicalMath.kcToKp(2.0, 300.0, -2.0)
        near(2.0, PhysicalMath.kcToKp(kp, 300.0, 2.0))
        near(2.0, PhysicalMath.kcToKp(2.0, 300.0, 0.0))
        near(1.0 / 6.0, tool("reaction_quotient").value.toDouble())
    }
    @Test fun solubilityOfAgClAndCalciumFluorideAndCommonIon() {
        val agcl = PhysicalMath.solubility(1.8e-10, 1, 1)
        near(sqrt(1.8e-10), agcl.solubility)
        val caf2 = PhysicalMath.solubility(3.9e-11, 1, 2)
        near((3.9e-11 / 4).pow(1.0 / 3.0), caf2.solubility)
        val common = PhysicalMath.solubility(1.8e-10, 1, 1, 0.1, 0.0)
        near(1.8e-10, common.ionA * common.ionB)
        assertTrue(common.solubility < agcl.solubility)
        rejected { PhysicalMath.solubility(1.8e-10, 1, 1, 0.1, 0.1) }
        near(1e-150, PhysicalMath.solubility(1e-300, 1, 1).solubility, 1e-10)
    }
    @Test fun complexPreservesMassAndVeryWeakOrStrongBinding() {
        val result = PhysicalMath.complex(1e6, 0.001, 0.002)
        near(0.001, result.bound + result.freeMetal)
        near(0.002, result.bound + result.freeLigand)
        near(1e6, result.bound / (result.freeMetal * result.freeLigand))
        val weak = PhysicalMath.complex(1e-100, 0.001, 0.001)
        near(1e-106, weak.bound)
        val strong = PhysicalMath.complex(1e100, 0.001, 0.001)
        near(1e100, strong.bound / (strong.freeMetal * strong.freeLigand))
        val excess = PhysicalMath.complex(1e20, 1.0, 0.001)
        assertTrue(excess.freeLigand > 0.0)
        near(1e20, excess.bound / (excess.freeMetal * excess.freeLigand))
        near(0.0, PhysicalMath.complex(1e6, 0.0, 0.1).bound)
    }
    @Test fun arrheniusRoundTripAndKnownHalfLife() {
        val k2 = PhysicalMath.rateAtTemperature(0.01, 50000.0, 298.15, 308.15)
        near(50000.0, PhysicalMath.activationEnergy(0.01, k2, 298.15, 308.15))
        near(ln(2.0) / 0.01, tool("first_order").value.toDouble())
        near(0.5, tool("first_order", "unknown" to "remaining", "time" to (ln(2.0) / 0.01).toString()).value.toDouble())
        rejected { PhysicalMath.activationEnergy(0.01, 0.02, 298.15, 298.15) }
        rejected { tool("first_order", "k" to "0") }
    }
    @Test fun mixtureDilutionAndHessHaveKnownAnswers() {
        near(10.0, tool("dilution").value.toDouble())
        near(0.466666666666, tool("mixing").value.toDouble())
        near(5.0, tool("fraction").value.toDouble())
        near(-110.5, tool("hess").value.toDouble())
        rejected { tool("dilution", "c2" to "10") }
        rejected { tool("fraction", "part" to "101", "total" to "100") }
    }
    @Test fun everyToolDefaultIsComputableAndDocumented() {
        assertTrue(PhysicalTools.tools.size >= 16)
        PhysicalTools.tools.forEach { spec ->
            val result = spec.calculate(spec.fields.associate { it.key to it.defaultValue })
            assertTrue("${spec.id} empty result", result.value.isNotBlank())
            assertTrue("${spec.id} missing steps", result.steps.isNotEmpty())
            assertTrue("${spec.id} missing formula", spec.formula.isNotBlank())
        }
    }
}
