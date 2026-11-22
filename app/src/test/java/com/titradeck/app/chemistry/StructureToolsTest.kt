package com.titradeck.app.chemistry

import org.junit.Assert.*
import org.junit.Test

class StructureToolsTest {
    @Test fun vseprDistinguishesElectronDomainsFromMolecularGeometry() {
        val water = StructureTools.predictGeometry(2, 2)
        assertEquals("AX2E2", water.notation)
        assertEquals("四面体", water.electronGeometry)
        assertEquals("折线形", water.molecularGeometry)
        assertEquals(3, water.model.atoms.size)
        assertEquals("三角锥形", StructureTools.predictGeometry(3, 1).molecularGeometry)
        assertEquals("平面正方形", StructureTools.predictGeometry(4, 2).molecularGeometry)
        assertEquals("直线形", StructureTools.predictGeometry(2, 3).molecularGeometry)
        assertEquals("T 形", StructureTools.predictGeometry(3, 2).molecularGeometry)
    }

    @Test fun vseprRejectsImpossibleAndOutOfScopeDomainCounts() {
        for (pair in listOf(1 to 0, 6 to 1, 3 to -1, 0 to 4, 7 to 0)) {
            assertThrows(IllegalArgumentException::class.java) { StructureTools.predictGeometry(pair.first, pair.second) }
        }
    }

    @Test fun degreesOfUnsaturationMatchKnownCompounds() {
        assertEquals(0, StructureTools.degreeOfUnsaturation("C2H6"))
        assertEquals(1, StructureTools.degreeOfUnsaturation("C2H4"))
        assertEquals(2, StructureTools.degreeOfUnsaturation("C2H2"))
        assertEquals(4, StructureTools.degreeOfUnsaturation("C6H6"))
        assertEquals(1, StructureTools.degreeOfUnsaturation("C6H12"))
        assertEquals(1, StructureTools.degreeOfUnsaturation("C2H3Cl"))
        assertEquals(6, StructureTools.degreeOfUnsaturation("C8H10N4O2"))
        assertEquals(0, StructureTools.degreeOfUnsaturation("C₂H₆O"))
    }

    @Test fun degreesOfUnsaturationRejectRadicalsIonsMetalsAndMixtures() {
        for (formula in listOf("CH3", "CH5", "Na2CO3", "NH3", "C2H6O·H2O", "2C6H6", "CH3+")) {
            assertThrows("Reject $formula", IllegalArgumentException::class.java) { StructureTools.degreeOfUnsaturation(formula) }
        }
    }

    @Test fun geometryModelsHaveValidFiniteCoordinatesAndBondEndpoints() {
        val models = (2..6).flatMap { total -> (2..total).map { StructureTools.predictGeometry(it, total - it).model } }
        for (model in models) {
            assertTrue(model.atoms.isNotEmpty())
            assertTrue(model.atoms.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            assertTrue(model.bonds.all { it.from in model.atoms.indices && it.to in model.atoms.indices && it.from != it.to && it.order in 1..3 })
        }
    }
}
