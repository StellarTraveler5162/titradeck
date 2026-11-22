package com.titradeck.app

import com.titradeck.app.chemistry.*
import org.junit.Assert.*
import org.junit.Test

class CatalogIntegrationTest {
    private val tools = ReactionTools.tools + PhysicalTools.tools + StructureTools.tools
    @Test fun everyToolHasAWorkingExampleAndUniqueInputKeys() {
        assertEquals(tools.size, tools.map { it.id }.toSet().size)
        tools.forEach { tool ->
            assertEquals(tool.id, tool.fields.size, tool.fields.map { it.key }.toSet().size)
            tool.fields.filter { it.choices.isNotEmpty() }.forEach { field ->
                assertTrue("${tool.id}.${field.key}: invalid example choice", field.choices.any { it.value == field.defaultValue })
            }
            val result = tool.calculate(tool.fields.associate { it.key to it.defaultValue })
            assertTrue("${tool.id}: missing result", result.value.isNotBlank())
            assertTrue("${tool.id}: missing explanation", result.steps.isNotEmpty())
            assertFalse(tool.id, Regex("\\b(NaN|Infinity)\\b").containsMatchIn(result.value + result.rows.joinToString { it.value }))
        }
    }
    @Test fun referenceValuesOpenValidCalculations() {
        ReferenceData.entries.filter { it.toolId != null }.forEach { reference ->
            val tool = tools.single { it.id == reference.toolId }
            val values = tool.fields.associate { it.key to it.defaultValue }.toMutableMap()
            reference.fieldKey?.let { key -> assertTrue(reference.id, values.containsKey(key)); values[key] = reference.value.toString() }
            reference.inputOverrides.forEach { (key, value) -> assertTrue(reference.id, values.containsKey(key)); values[key] = value }
            assertTrue(reference.id, tool.calculate(values).value.isNotBlank())
        }
    }
}
