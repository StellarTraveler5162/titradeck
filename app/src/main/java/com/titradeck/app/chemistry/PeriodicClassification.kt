package com.titradeck.app.chemistry

enum class PeriodicColorGroup(val label: String) {
    NONMETAL("非金属"), METAL("金属"), NOBLE_GAS("稀有气体")
}

val Element.periodicColorGroup: PeriodicColorGroup
    get() = when (category) {
        "稀有气体" -> PeriodicColorGroup.NOBLE_GAS
        "非金属", "卤素", "类金属" -> PeriodicColorGroup.NONMETAL
        else -> PeriodicColorGroup.METAL
    }

/** Elements for which every known isotope is radioactive. */
val Element.hasNoStableIsotopes: Boolean
    get() = atomicNumber == 43 || atomicNumber == 61 || atomicNumber >= 83
