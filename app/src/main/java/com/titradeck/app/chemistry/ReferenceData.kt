package com.titradeck.app.chemistry

import kotlin.math.log10

data class ReferenceEntry(
    val id: String,
    val name: String,
    val value: Double,
    val unit: String,
    val description: String,
    val source: String,
    val toolId: String? = null,
    val fieldKey: String? = null,
    val inputOverrides: Map<String, String> = emptyMap(),
)

/** Offline snapshot. Sources, precision and scope are recorded in docs/DATA_SOURCES.md. */
object ReferenceData {
    private const val NIST = "https://physics.nist.gov/cuu/Constants/index.html"
    private const val ACIDS = "https://openstax.org/books/chemistry-2e/pages/h-ionization-constants-of-weak-acids"
    private const val BASES = "https://openstax.org/books/chemistry-2e/pages/i-ionization-constants-of-weak-bases"
    private const val KSP = "https://openstax.org/books/chemistry-2e/pages/j-solubility-products"
    private const val ELECTRODES = "https://openstax.org/books/chemistry-2e/pages/l-standard-electrode-half-cell-potentials"

    val entries: List<ReferenceEntry> = listOf(
        ReferenceEntry("avogadro", "阿伏伽德罗常数 Nₐ", 6.02214076e23, "mol⁻¹", "SI 定义中的精确值；粒子数 N = nNₐ。", NIST),
        ReferenceEntry("gas_constant", "摩尔气体常数 R", 8.31446261815324, "J·mol⁻¹·K⁻¹", "R = Nₐkᵦ；由 SI 定义常数导出，使用 Pa·m³ 或 J。", NIST),
        ReferenceEntry("faraday", "法拉第常数 F", 96485.33212331001, "C·mol⁻¹", "F = Nₐe；每摩尔电子所携带的电荷量（显示值经过浮点舍入）。", NIST),
        ReferenceEntry("boltzmann", "玻尔兹曼常数 kᵦ", 1.380649e-23, "J·K⁻¹", "SI 定义中的精确值。", NIST),
        ReferenceEntry("elementary_charge", "元电荷 e", 1.602176634e-19, "C", "SI 定义中的精确值，取电荷量绝对值。", NIST),
        ReferenceEntry("planck", "普朗克常数 h", 6.62607015e-34, "J·s", "SI 定义中的精确值；E = hν。", NIST),
        ReferenceEntry("light_speed", "真空光速 c", 299792458.0, "m·s⁻¹", "SI 定义中的精确值。", NIST),
        ReferenceEntry("atmosphere", "标准大气压", 101325.0, "Pa", "1 atm = 101325 Pa，定义值；不同于标准压力 100000 Pa。", "https://www.bipm.org/en/publications/si-brochure"),
        ReferenceEntry("standard_pressure", "标准压力 p°", 100000.0, "Pa", "热力学标准压力 1 bar；使用气体摩尔体积时必须同时指定温度。", "https://goldbook.iupac.org/terms/view/S05921"),
        ReferenceEntry("pkw25", "水的 pKᵥ（25 °C 近似）", 14.0, "无量纲", "稀水溶液、25 °C 常用近似；pH + pOH = pKᵥ，温度改变时需更改。", "https://openstax.org/books/chemistry-2e/pages/14-2-ph-and-poh", "ph", "pKw"),
        ReferenceEntry("acetic_pka", "乙酸 pKₐ", -log10(1.8e-5), "无量纲", "25 °C；由同一来源 Kₐ = 1.8×10⁻⁵ 派生，忽略活度系数，非无限精度实验常数。", ACIDS, "ph", "pK", mapOf("kind" to "weak_acid", "pKw" to "14")),
        ReferenceEntry("formic_pka", "甲酸 pKₐ", -log10(1.8e-4), "无量纲", "25 °C；由 Kₐ = 1.8×10⁻⁴ 派生；适用一元弱酸模型。", ACIDS, "ph", "pK", mapOf("kind" to "weak_acid", "pKw" to "14")),
        ReferenceEntry("hf_pka", "氢氟酸 pKₐ", -log10(6.4e-4), "无量纲", "25 °C；由 Kₐ = 6.4×10⁻⁴ 派生，稀溶液近似。", ACIDS, "ph", "pK", mapOf("kind" to "weak_acid", "pKw" to "14")),
        ReferenceEntry("ammonia_pkb", "氨 pKᵦ", -log10(1.8e-5), "无量纲", "25 °C；由 Kᵦ = 1.8×10⁻⁵ 派生，选择一元弱碱模型。", BASES, "ph", "pK", mapOf("kind" to "weak_base", "pKw" to "14")),
        ReferenceEntry("agcl_ksp", "AgCl 溶度积", 1.6e-10, "25 °C", "AgCl(s) ⇌ Ag⁺ + Cl⁻；Ksp≈[Ag⁺][Cl⁻]，浓度用 mol/L，活度系数按 1。", KSP, "solubility", "ksp", mapOf("a" to "1", "b" to "1", "molarMass" to FormulaParser.parse("AgCl").molarMass.toString(), "commonA" to "0", "commonB" to "0")),
        ReferenceEntry("caf2_ksp", "CaF₂ 溶度积", 4.0e-11, "25 °C", "CaF₂(s) ⇌ Ca²⁺ + 2F⁻；Ksp≈[Ca²⁺][F⁻]²；此处数值来自 OpenStax 指定版本。", KSP, "solubility", "ksp", mapOf("a" to "1", "b" to "2", "molarMass" to FormulaParser.parse("CaF2").molarMass.toString(), "commonA" to "0", "commonB" to "0")),
        ReferenceEntry("cuoh2_ksp", "Cu(OH)₂ 溶度积", 2.2e-20, "25 °C", "Cu(OH)₂(s) ⇌ Cu²⁺ + 2OH⁻；简单溶解模型不含水解、配位和其他酸碱平衡。", KSP, "solubility", "ksp", mapOf("a" to "1", "b" to "2", "molarMass" to FormulaParser.parse("Cu(OH)2").molarMass.toString(), "commonA" to "0", "commonB" to "0")),
        ReferenceEntry("hydrogen_e0", "H⁺/H₂ 标准还原电势", 0.0, "V", "2H⁺ + 2e⁻ → H₂；标准氢电极参比，25 °C、标准态；n = 2。", ELECTRODES, "nernst", "e0", mapOf("electrons" to "2", "temperature" to "298.15", "mode" to "q", "q" to "1")),
        ReferenceEntry("copper_e0", "Cu²⁺/Cu 标准还原电势", 0.337, "V", "Cu²⁺ + 2e⁻ → Cu；相对于标准氢电极，25 °C、标准态；n = 2。", ELECTRODES, "nernst", "e0", mapOf("electrons" to "2", "temperature" to "298.15", "mode" to "q", "q" to "1")),
        ReferenceEntry("zinc_e0", "Zn²⁺/Zn 标准还原电势", -0.7618, "V", "Zn²⁺ + 2e⁻ → Zn；相对于标准氢电极，25 °C、标准态；n = 2。", ELECTRODES, "nernst", "e0", mapOf("electrons" to "2", "temperature" to "298.15", "mode" to "q", "q" to "1")),
        ReferenceEntry("silver_e0", "Ag⁺/Ag 标准还原电势", 0.7996, "V", "Ag⁺ + e⁻ → Ag；相对于标准氢电极，25 °C、标准态；n = 1。", ELECTRODES, "nernst", "e0", mapOf("electrons" to "1", "temperature" to "298.15", "mode" to "q", "q" to "1")),
        ReferenceEntry("fescn_kf", "FeSCN²⁺ 形成常数", 8.9e2, "25 °C 近似", "Fe³⁺ + SCN⁻ ⇌ FeSCN²⁺；1:1 配合模型，Kf≈[FeSCN²⁺]/([Fe³⁺][SCN⁻])；条件改变数值也会变化。", "https://openstax.org/books/chemistry-2e/pages/k-formation-constants-for-complex-ions", "complex", "kf"),
    )
}
