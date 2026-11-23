package com.titradeck.app.chemistry

import org.junit.Assert.*
import org.junit.Test

class ReferenceDataTest {
    @Test fun elementTableIsCompleteAndUniquelyPositioned() {
        assertEquals(118, Elements.all.size)
        assertEquals((1..118).toList(), Elements.all.map { it.atomicNumber })
        assertEquals(118, Elements.bySymbol.size)
        assertEquals(118, Elements.all.map { it.displayRow to it.displayColumn }.toSet().size)
        assertTrue(Elements.all.all { it.atomicMass.isFinite() && it.atomicMass > 0.0 })
        assertTrue(Elements.all.all { it.displayColumn in 1..18 && it.displayRow in 1..9 })
        assertEquals(18, Elements.bySymbol.getValue("He").group)
        assertNull(Elements.bySymbol.getValue("La").group)
    }

    @Test fun commonAtomicWeightsUseTheCiaawSnapshot() {
        assertEquals(6.94, Elements.bySymbol.getValue("Li").atomicMass, 1e-12)
        assertEquals(12.011, Elements.bySymbol.getValue("C").atomicMass, 1e-12)
        assertEquals(55.845, Elements.bySymbol.getValue("Fe").atomicMass, 1e-12)
        assertEquals(63.546, Elements.bySymbol.getValue("Cu").atomicMass, 1e-12)
        assertFalse(Elements.bySymbol.getValue("Fe").massIsRepresentative)
        assertTrue(Elements.bySymbol.getValue("Og").massIsRepresentative)
        assertNull(Elements.bySymbol.getValue("Og").boilingPoint)
        assertNull(Elements.bySymbol.getValue("He").electronegativity)
    }

    @Test fun periodicColorsAndRadioactivityCoverEveryElement() {
        assertEquals(PeriodicColorGroup.NONMETAL, Elements.bySymbol.getValue("C").periodicColorGroup)
        assertEquals(PeriodicColorGroup.NONMETAL, Elements.bySymbol.getValue("Cl").periodicColorGroup)
        assertEquals(PeriodicColorGroup.NONMETAL, Elements.bySymbol.getValue("Si").periodicColorGroup)
        assertEquals(PeriodicColorGroup.METAL, Elements.bySymbol.getValue("Fe").periodicColorGroup)
        assertEquals(PeriodicColorGroup.METAL, Elements.bySymbol.getValue("La").periodicColorGroup)
        assertEquals(PeriodicColorGroup.NOBLE_GAS, Elements.bySymbol.getValue("Rn").periodicColorGroup)
        assertEquals(PeriodicColorGroup.entries.toSet(), Elements.all.map { it.periodicColorGroup }.toSet())
        val expected = setOf(43, 61) + (83..118)
        assertEquals(expected, Elements.all.filter { it.hasNoStableIsotopes }.map { it.atomicNumber }.toSet())
        assertFalse(Elements.bySymbol.getValue("U").massIsRepresentative)
        assertTrue(Elements.bySymbol.getValue("U").hasNoStableIsotopes)
    }

    @Test fun siRelationshipsAndReferenceIdentitiesAreConsistent() {
        val entries = ReferenceData.entries.associateBy { it.id }
        assertEquals(ReferenceData.entries.size, entries.size)
        assertTrue(ReferenceData.entries.all { it.value.isFinite() && it.source.startsWith("https://") })
        val avogadro = entries.getValue("avogadro").value
        assertEquals(avogadro * entries.getValue("boltzmann").value, entries.getValue("gas_constant").value, 1e-13)
        assertEquals(avogadro * entries.getValue("elementary_charge").value, entries.getValue("faraday").value, 1e-9)
        assertEquals("weak_base", entries.getValue("ammonia_pkb").inputOverrides["kind"])
        assertEquals("2", entries.getValue("caf2_ksp").inputOverrides["b"])
    }

    @Test fun referencePresetsPopulateRealFieldsAndProduceValidCalculations() {
        val tools = PhysicalTools.tools.associateBy { it.id }
        for (entry in ReferenceData.entries.filter { it.toolId != null }) {
            val tool = tools.getValue(entry.toolId!!)
            val values = tool.fields.associate { it.key to it.defaultValue }.toMutableMap()
            assertTrue("${entry.id}: primary field", values.containsKey(entry.fieldKey))
            assertTrue("${entry.id}: override field", entry.inputOverrides.keys.all { values.containsKey(it) })
            values.putAll(entry.inputOverrides)
            values[entry.fieldKey!!] = entry.value.toString()
            val result = tool.calculate(values)
            assertTrue(entry.id, result.value.isNotBlank())
            assertFalse(entry.id, result.value.contains("NaN") || result.value.contains("Infinity"))
        }
        val silverChloride = ReferenceData.entries.first { it.id == "agcl_ksp" }
        assertEquals(FormulaParser.parse("AgCl").molarMass, silverChloride.inputOverrides.getValue("molarMass").toDouble(), 1e-9)
    }
}
