package com.titradeck.app.chemistry

import java.util.Locale
import kotlin.math.*

object PhysicalTools {
    private fun choice(vararg pairs: Pair<String, String>) = pairs.map { Choice(it.first, it.second) }
    private fun field(key: String, label: String, value: String, hint: String = "") = FieldSpec(key, label, value, hint)
    private fun select(key: String, label: String, value: String, vararg pairs: Pair<String, String>) = FieldSpec(key, label, value, choices = choice(*pairs))
    private fun n(input: Map<String, String>, key: String, label: String = key): Double {
        val value = input[key]?.trim()?.replace('−', '-')?.replace(',', '.')?.toDoubleOrNull()
        require(value != null && value.isFinite()) { "请输入有效的$label（支持 1.2e-3 科学计数法）。" }
        return value
    }
    private fun s(input: Map<String, String>, key: String) = input[key].orEmpty()
    private fun pos(value: Double, label: String) = PhysicalMath.positive(value, label)
    private fun nonneg(value: Double, label: String) = PhysicalMath.nonnegative(value, label)
    private fun f(value: Double): String {
        PhysicalMath.finite(value, "结果")
        if (value == 0.0) return "0"
        return String.format(Locale.ROOT, "%.9g", value).replace(Regex("(\\.\\d*?[1-9])0+(?=e|$)"), "$1").replace(Regex("\\.0+(?=e|$)"), "")
    }
    private fun row(label: String, value: Double, unit: String = "") = ResultRow(label, f(value), unit)
    private fun temp(input: Map<String, String>, key: String, unitKey: String): Double = pos(n(input, key, "温度") + if (s(input, unitKey) == "C") 273.15 else 0.0, "绝对温度")
    private val pressureFactors = mapOf("Pa" to 1.0, "kPa" to 1000.0, "atm" to PhysicalMath.ATM, "mmHg" to PhysicalMath.ATM / 760.0, "bar" to 100000.0)
    private val volumeFactors = mapOf("m3" to 1.0, "L" to 1e-3, "mL" to 1e-6)
    private fun factor(map: Map<String, Double>, key: String): Double = map[key] ?: throw IllegalArgumentException("请选择有效单位。")
    private fun int(input: Map<String, String>, key: String, label: String, range: IntRange): Int {
        val value = n(input, key, label)
        require(value == floor(value) && value >= range.first && value <= range.last) { "$label 必须为 ${range.first}–${range.last} 的整数。" }
        return value.toInt()
    }

    val tools: List<ToolDefinition> = listOf(
        ToolDefinition("concentration", "浓度换算", "摩尔浓度、质量摩尔浓度、当量浓度与质量分数", ToolCategory.SOLUTION,
            "c = n/V；m = n/m溶剂；N = zc；w = m溶质/m溶液",
            listOf(select("kind", "已知浓度类型", "M", "M" to "摩尔浓度 mol/L", "m" to "质量摩尔浓度 mol/kg", "N" to "当量浓度 eq/L", "w" to "质量分数 %"),
                field("value", "已知浓度数值", "1"), field("molarMass", "溶质摩尔质量 / g·mol⁻¹", "58.44"),
                field("density", "溶液密度 / g·mL⁻¹", "1.04", "必须使用该浓度和温度下的实际密度"), field("equivalents", "反应当量因子 z", "1", "如酸碱反应中每 mol 参与的 H⁺ 数；与具体反应有关"))) { input ->
            val mw = n(input, "molarMass", "摩尔质量"); val density = n(input, "density", "密度"); val z = n(input, "equivalents", "当量因子")
            val c = PhysicalMath.concentration(n(input, "value", "浓度"), s(input, "kind"), mw, density, z)
            CalculationResult("摩尔浓度", f(c.molarity), "mol/L", listOf(row("质量摩尔浓度", c.molality, "mol/kg 溶剂"), row("当量浓度", c.normality, "eq/L"), row("溶质质量分数", c.massFraction * 100.0, "%")),
                listOf("取 1 L 溶液：m溶液 = ${f(density)} g/mL × 1000 mL = ${f(density * 1000)} g。", "n溶质 = ${f(c.molarity)} mol；m溶质 = n × ${f(mw)} g/mol = ${f(c.molarity * mw)} g。", "m溶剂 = m溶液 − m溶质；m = n / (m溶剂/1000)，单位 mol/kg。", "N = zc = ${f(z)} × ${f(c.molarity)} eq/L；w = m溶质/m溶液。"),
                listOf("按单一溶质计算；当量浓度依赖指定反应，不能仅凭化学式唯一确定。密度与温度、组成有关。"))
        },
        ToolDefinition("dilution", "稀释配液", "选择未知量，按溶质守恒计算定容配液", ToolCategory.SOLUTION, "C₁V₁ = C₂V₂",
            listOf(select("unknown", "待求变量", "v1", "c1" to "原液浓度 C₁", "v1" to "取用原液体积 V₁", "c2" to "目标浓度 C₂", "v2" to "定容后体积 V₂"),
                field("c1", "原液浓度 C₁ / mol·L⁻¹", "1"), field("v1", "取用原液 V₁ / mL", "10"),
                field("c2", "目标浓度 C₂ / mol·L⁻¹", "0.1"), field("v2", "定容后 V₂ / mL", "100"))) { input ->
            val unknown = s(input, "unknown")
            val values = mutableMapOf<String, Double>()
            listOf("c1", "v1", "c2", "v2").filter { it != unknown }.forEach { values[it] = pos(n(input, it, it.uppercase()), it.uppercase()) }
            values[unknown] = when (unknown) { "c1" -> values.getValue("c2") * values.getValue("v2") / values.getValue("v1"); "v1" -> values.getValue("c2") * values.getValue("v2") / values.getValue("c1"); "c2" -> values.getValue("c1") * values.getValue("v1") / values.getValue("v2"); "v2" -> values.getValue("c1") * values.getValue("v1") / values.getValue("c2"); else -> throw IllegalArgumentException("请选择待求变量。") }
            val c1 = values.getValue("c1"); val c2 = values.getValue("c2"); val v1 = values.getValue("v1"); val v2 = values.getValue("v2")
            require(c1 >= c2 * (1 - 1e-12) && v2 >= v1 * (1 - 1e-12)) { "稀释要求原液浓度不低于目标浓度，定容体积不小于取液体积。" }
            CalculationResult(mapOf("c1" to "原液浓度", "c2" to "目标浓度", "v1" to "取用原液体积", "v2" to "定容后体积").getValue(unknown), f(values.getValue(unknown)), if (unknown.startsWith("c")) "mol/L" else "mL",
                listOf(row("C₁", c1, "mol/L"), row("V₁", v1, "mL"), row("C₂", c2, "mol/L"), row("V₂", v2, "mL")),
                listOf("原液中的溶质：${f(c1)} mol/L × ${f(v1)} mL × 10⁻³ L/mL = ${f(c1 * v1 / 1000)} mol。", "定容后：${f(c2)} mol/L × ${f(v2)} mL × 10⁻³ L/mL，溶质的量保持不变。"),
                listOf("量取原液后加溶剂至最终体积；不要把 V₂−V₁ 当作精确的加水体积（混合可能发生体积收缩）。"))
        },
        ToolDefinition("mixing", "溶液混合", "同一溶质、无化学反应时的混合浓度", ToolCategory.SOLUTION, "c混 = (c₁V₁ + c₂V₂)/(V₁+V₂)",
            listOf(field("c1", "第一份浓度 / mol·L⁻¹", "1"), field("v1", "第一份体积 / mL", "100"), field("c2", "第二份浓度 / mol·L⁻¹", "0.2"), field("v2", "第二份体积 / mL", "200"), field("finalVolume", "实测混合体积 / mL（可留空）", "", "留空表示假设体积可加和"))) { input ->
            val c1 = nonneg(n(input, "c1", "第一份浓度"), "浓度"); val c2 = nonneg(n(input, "c2", "第二份浓度"), "浓度")
            val v1 = pos(n(input, "v1", "第一份体积"), "体积"); val v2 = pos(n(input, "v2", "第二份体积"), "体积")
            val finalV = if (s(input, "finalVolume").isBlank()) v1 + v2 else pos(n(input, "finalVolume", "实测混合体积"), "体积")
            val totalN = (c1 * v1 + c2 * v2) / 1000
            CalculationResult("混合后摩尔浓度", f(totalN / (finalV / 1000)), "mol/L", listOf(row("溶质总物质的量", totalN, "mol"), row("混合总体积", finalV, "mL")),
                listOf("n₁ = ${f(c1)} × ${f(v1)} × 10⁻³ = ${f(c1 * v1 / 1000)} mol。", "n₂ = ${f(c2)} × ${f(v2)} × 10⁻³ = ${f(c2 * v2 / 1000)} mol。", "c混 = (n₁+n₂)/(V混×10⁻³ L/mL)。"), listOf("只适用于同一溶质、不发生反应或沉淀的溶液。实测最终体积可修正体积不可加和的情况。"))
        },
        ToolDefinition("fraction", "质量与体积分数", "独立计算组成百分比，并区分溶液与溶剂", ToolCategory.SOLUTION, "w = m组分/m总；φ = V组分/V总",
            listOf(select("kind", "分数类型", "mass", "mass" to "质量分数", "volume" to "体积分数"), field("part", "组分量（质量 g / 体积 mL）", "5"), field("total", "混合物总量（相同单位）", "100"))) { input ->
            val part = nonneg(n(input, "part", "组分量"), "组分量"); val total = pos(n(input, "total", "总量"), "总量")
            require(part <= total) { "组分量不能大于总量；分母应为混合物总量。" }
            val mass = s(input, "kind") == "mass"
            CalculationResult(if (mass) "质量分数" else "体积分数", f(part / total * 100), "%", listOf(row("小数表示", part / total)), listOf("${f(part)} ${if (mass) "g" else "mL"} ÷ ${f(total)} ${if (mass) "g" else "mL"} × 100%，分子分母单位相消。"),
                listOf(if (mass) "质量分数的分母是溶液总质量，不是溶剂质量。" else "体积分数需明确约定温度与体积定义；配液浓度 v/v 通常使用组分体积/最终溶液体积。"))
        },
        ToolDefinition("ph", "pH 与酸碱平衡", "强酸强碱、单质子弱酸弱碱；计入水的自电离", ToolCategory.SOLUTION,
            "Kw = [H⁺][OH⁻]；弱酸：[H⁺] = Kw/[H⁺] + CKa/(Ka+[H⁺])",
            listOf(select("kind", "溶液类型", "weak_acid", "strong_acid" to "强酸（每 mol 1 mol H⁺）", "strong_base" to "强碱（每 mol 1 mol OH⁻）", "weak_acid" to "单质子弱酸", "weak_base" to "单碱弱碱"), field("concentration", "分析浓度 C / mol·L⁻¹", "0.1"), field("pK", "弱酸 pKa / 弱碱 pKb", "4.76", "强酸强碱时不使用；可从常数库填入"), field("pKw", "水的 pKw", "14", "25 °C 近似取 14；其他温度应使用对应值"))) { input ->
            val c = n(input, "concentration", "分析浓度"); val kind = s(input, "kind"); val pKw = n(input, "pKw", "pKw")
            val pk = if (kind.startsWith("weak")) n(input, "pK", "pKa / pKb") else 0.0
            val result = PhysicalMath.acidBase(c, kind, pk, pKw)
            val rows = mutableListOf(row("[H⁺]", result.hydrogen, "mol/L"), row("[OH⁻]", result.hydroxide, "mol/L"), row("pOH", result.poh))
            result.ionizedFraction?.let { rows += row("电离分数", it * 100, "%") }
            CalculationResult("pH", f(result.ph), rows = rows,
                steps = listOf("Kw = 10^(−pKw) = ${f(10.0.pow(-pKw))}。", if (kind.startsWith("weak")) "K = 10^(−pK) = ${f(10.0.pow(-pk))}；联立物料守恒、电荷守恒与水的离子积，求平衡浓度。" else "强电解质完全解离，并用 x = (C + √(C²+4Kw))/2 保留水的贡献。", "pH = −log₁₀([H⁺]/1 mol·L⁻¹)；pOH = pKw−pH。"),
                notes = listOf("稀溶液近似：活度系数视为 1；不适用于多元酸逐级电离、缓冲混合物或高离子强度。强酸/强碱浓度指 H⁺/OH⁻ 当量浓度。", "即使 C 非常小或为 0，模型也会趋向该 pKw 下的中性，而不会错误地把极稀酸算成碱性。"))
        },
        gasTool(), converterTool(), nernstTool(), electrolysisTool(), kcKpTool(), quotientTool(), solubilityTool(), complexTool(), arrheniusTool(), firstOrderTool(), hessTool(),
    )

    private fun gasTool() = ToolDefinition("ideal_gas", "理想气体定律", "压力、体积、温度、物质的量，知三求一", ToolCategory.PHYSICAL, "PV = nRT",
        listOf(select("unknown", "待求变量", "v", "p" to "压力 P", "v" to "体积 V", "n" to "物质的量 n", "t" to "温度 T"),
            field("p", "压力数值", "1"), select("pUnit", "压力单位", "atm", "Pa" to "Pa", "kPa" to "kPa", "atm" to "atm", "mmHg" to "mmHg", "bar" to "bar"),
            field("v", "体积数值", "22.414"), select("vUnit", "体积单位", "L", "L" to "L", "mL" to "mL", "m3" to "m³"),
            field("n", "物质的量 / mol", "1"), field("t", "温度数值", "273.15"), select("tUnit", "温度单位", "K", "K" to "K", "C" to "°C"))) { input ->
        val unknown = s(input, "unknown"); val pf = factor(pressureFactors, s(input, "pUnit")); val vf = factor(volumeFactors, s(input, "vUnit"))
        val p = if (unknown == "p") 0.0 else n(input, "p", "压力") * pf
        val v = if (unknown == "v") 0.0 else n(input, "v", "体积") * vf
        val amount = if (unknown == "n") 0.0 else n(input, "n", "物质的量")
        val t = if (unknown == "t") 0.0 else temp(input, "t", "tUnit")
        val result = PhysicalMath.gas(unknown, p, v, amount, t)
        val displayT = result.temperature - if (s(input, "tUnit") == "C") 273.15 else 0.0
        val answer = when (unknown) { "p" -> result.pressure / pf; "v" -> result.volume / vf; "n" -> result.amount; else -> displayT }
        val unit = when (unknown) { "p" -> s(input, "pUnit"); "v" -> s(input, "vUnit"); "n" -> "mol"; else -> if (s(input, "tUnit") == "C") "°C" else "K" }
        CalculationResult(mapOf("p" to "气体压力", "v" to "气体体积", "n" to "物质的量", "t" to "气体温度").getValue(unknown), f(answer), unit,
            listOf(row("P（SI）", result.pressure, "Pa"), row("V（SI）", result.volume, "m³"), row("n", result.amount, "mol"), row("T（绝对温度）", result.temperature, "K")),
            listOf("统一为 Pa、m³、mol、K；摄氏温度加 273.15 转为 K。", "R = ${f(PhysicalMath.R)} Pa·m³·mol⁻¹·K⁻¹。", "P×V = ${f(result.pressure * result.volume)} Pa·m³ = nRT，且 Pa·m³ = J。"),
            listOf("理想气体近似；高压、低温或接近液化时可能偏离。压力使用绝对压力；这里 mmHg 按 760 mmHg = 1 atm 的常用近似。"))
    }

    private fun converterTool() = ToolDefinition("unit_converter", "化学单位换算", "质量、体积、物质的量、粒子数、浓度与温压", ToolCategory.CORE, "m = nM；ρ = cM；N = nNₐ",
        listOf(field("value", "原始数值", "1"), FieldSpec("fromUnit", "原始单位", "mol", choices = PhysicalMath.units.map { Choice(it.id, it.label) }),
            FieldSpec("toUnit", "目标单位", "particles", choices = PhysicalMath.units.map { Choice(it.id, it.label) }), field("molarMass", "摩尔质量 / g·mol⁻¹", "58.44", "跨量纲时可输入摩尔质量"))) { input ->
        val value = n(input, "value", "原始数值"); val from = s(input, "fromUnit"); val to = s(input, "toUnit")
        val mw = s(input, "molarMass").trim().replace('−', '-').replace(',', '.').toDoubleOrNull()
        val result = PhysicalMath.convert(value, from, to, mw)
        val source = PhysicalMath.units.first { it.id == from }; val target = PhysicalMath.units.first { it.id == to }
        val steps = mutableListOf("${f(value)} ${source.label.substringBefore(" ·")} → 基准量 → ${f(result)} ${target.label.substringBefore(" ·")}。")
        if (from == "particles" || to == "particles") steps += "N = nNₐ；Nₐ = 6.02214076×10²³ mol⁻¹（SI 定义精确值）。"
        if (source.dimension != target.dimension) {
            val massSource = source.dimension == "mass" || source.dimension == "mass_concentration"
            val concentration = source.dimension.endsWith("concentration")
            val sourceBaseUnit = if (massSource) { if (concentration) "g/L" else "g" } else { if (concentration) "mol/L" else "mol" }
            val targetBaseUnit = if (massSource) { if (concentration) "mol/L" else "mol" } else { if (concentration) "g/L" else "g" }
            val sourceBase = value * source.factor
            val targetBase = if (massSource) sourceBase / mw!! else sourceBase * mw!!
            val operation = if (massSource) "÷" else "×"
            steps += "${f(sourceBase)} $sourceBaseUnit $operation ${f(mw)} g/mol = ${f(targetBase)} $targetBaseUnit。"
        }
        if (source.dimension == "temperature") steps += "T/K = t/°C + 273.15。"
        CalculationResult("换算结果", f(result), target.label.substringBefore(" ·"), steps = steps, notes = listOf("质量与物质的量、质量浓度与摩尔浓度互换时需要指定摩尔质量。mmHg 采用 760 mmHg = 1 atm 近似。"))
    }

    private fun nernstTool() = ToolDefinition("nernst", "能斯特方程", "电极电势、电池电动势与 pH 的联系", ToolCategory.EQUILIBRIUM, "E = E° − RT/(nF) ln Q",
        listOf(field("e0", "标准电势 E° / V", "0.34"), field("electrons", "转移电子数 n", "2"), field("temperature", "温度 / K", "298.15"),
            select("mode", "反应商输入方式", "q", "q" to "直接输入 Q", "ph" to "由 pH 与其他活度因子得到 Q"), field("q", "Q（pH 模式下为不含 H⁺ 的因子）", "1"), field("ph", "pH（仅 pH 模式）", "7"), field("hExponent", "Q 中 H⁺ 的指数 ν（产物−反应物）", "-2", "例：2H⁺+2e⁻→H₂，ν=−2"))) { input ->
        val q = pos(n(input, "q", "反应商"), "反应商"); val e0 = n(input, "e0", "标准电势"); val electrons = n(input, "electrons", "电子数"); val t = n(input, "temperature", "温度")
        var logQ = ln(q)
        if (s(input, "mode") == "ph") logQ -= n(input, "hExponent", "H⁺ 指数") * n(input, "ph", "pH") * ln(10.0)
        val potential = PhysicalMath.nernst(e0, electrons, t, logQ)
        CalculationResult("平衡电势 E", f(potential), "V", listOf(row("相对标准电势变化", potential - e0, "V"), row("log₁₀ Q", logQ / ln(10.0))),
            listOf("R = ${f(PhysicalMath.R)} J/(mol·K)，F = ${f(PhysicalMath.F)} C/mol。", if (s(input, "mode") == "ph") "ln Q = ln Q其他 − ν·pH·ln 10 = ${f(logQ)}。" else "ln Q = ln(${f(q)}) = ${f(logQ)}。", "ΔE = −RT·ln Q/(nF)，J/C = V；E = E°+ΔE。"),
            listOf("Q 必须按与 E° 相同方向的反应式构造，并使用无量纲活度。纯固体/纯液体活度取 1；浓度替代活度仅为稀溶液近似。输入电池总反应的 E° 与电子数时可求电池电动势。"))
    }

    private fun electrolysisTool() = ToolDefinition("electrolysis", "电解与法拉第定律", "电流、通电时间与电流效率决定产物量", ToolCategory.EQUILIBRIUM, "Q = It；n产物 = ηIt/(zF)；m = nM",
        listOf(field("current", "电流 / A", "1"), field("time", "通电时间", "60"), select("timeUnit", "时间单位", "min", "s" to "s", "min" to "min", "h" to "h"), field("electrons", "每 mol 产物对应电子数 z", "2"), field("molarMass", "产物摩尔质量 / g·mol⁻¹", "63.546"), field("efficiency", "电流效率 / %", "100"))) { input ->
        val current = nonneg(n(input, "current", "电流"), "电流"); val seconds = nonneg(n(input, "time", "时间"), "时间") * factor(mapOf("s" to 1.0, "min" to 60.0, "h" to 3600.0), s(input, "timeUnit"))
        val z = int(input, "electrons", "电子计量数", 1..100); val mw = pos(n(input, "molarMass", "摩尔质量"), "摩尔质量"); val efficiency = n(input, "efficiency", "电流效率")
        require(efficiency in 0.0..100.0) { "电流效率应为 0–100%。" }
        val charge = current * seconds; val mol = charge / PhysicalMath.F / z * efficiency / 100.0
        CalculationResult("电解产物质量", f(mol * mw), "g", listOf(row("产物物质的量", mol, "mol"), row("总电荷", charge, "C"), row("总电子物质的量", charge / PhysicalMath.F, "mol e⁻")),
            listOf("t = ${f(seconds)} s；Q = ${f(current)} A × t = ${f(charge)} C。", "n(e⁻) = Q/F；F = ${f(PhysicalMath.F)} C/mol。", "n产物 = n(e⁻)/$z × ${f(efficiency / 100)}；m = n产物 × ${f(mw)} g/mol。"), listOf("假定电流恒定且产物反应已明确；副反应以电流效率修正。变电流时应使用积分电荷。"))
    }

    private fun kcKpTool() = ToolDefinition("kc_kp", "Kc 与 Kp", "气相反应平衡常数的温压基准换算", ToolCategory.EQUILIBRIUM, "Kp = Kc (RT·c°/p°)^Δn",
        listOf(select("direction", "换算方向", "to_kp", "to_kp" to "已知 Kc 求 Kp", "to_kc" to "已知 Kp 求 Kc"), field("constant", "已知平衡常数", "1"), field("temperature", "温度 / K", "298.15"), field("deltaN", "气体计量数变化 Δn", "-2", "生成物气体系数和 − 反应物气体系数和"))) { input ->
        val known = pos(n(input, "constant", "平衡常数"), "平衡常数"); val t = n(input, "temperature", "温度"); val dn = n(input, "deltaN", "Δn")
        val toKp = s(input, "direction") == "to_kp"; val result = PhysicalMath.kcToKp(known, t, if (toKp) dn else -dn)
        CalculationResult(if (toKp) "Kp" else "Kc", f(result), steps = listOf("采用 c° = 1 mol/L、p° = 1 atm；R = ${f(PhysicalMath.R * 1000 / PhysicalMath.ATM)} L·atm/(mol·K)。", "RT·c°/p° = ${f(PhysicalMath.R * 1000 / PhysicalMath.ATM * t)}；Δn = ${f(dn)}。", if (toKp) "Kp = Kc × (RT·c°/p°)^Δn。" else "Kc = Kp / (RT·c°/p°)^Δn。"), notes = listOf("常数以指定标准态定义为无量纲值；只对理想气体组分作此换算，纯固体和液体不计入 Δn。温度必须与原平衡常数对应。"))
    }

    private fun quotientTool() = ToolDefinition("reaction_quotient", "反应商与平衡方向", "按各物种活度和计量系数计算 Q，比较 K", ToolCategory.EQUILIBRIUM, "Q = ∏a生成物^ν / ∏a反应物^ν",
        listOf(FieldSpec("products", "生成物：每行 活度,系数", "0.1,2", "无量纲活度；纯固体/纯液体可省略", multiline = true), FieldSpec("reactants", "反应物：每行 活度,系数", "0.2,1\n0.3,1", multiline = true), field("constant", "同温度同反应方向的 K", "1"))) { input ->
        fun sumLogs(key: String): Double {
            val lines = s(input, key).lineSequence().filter { it.isNotBlank() }.toList()
            require(lines.size in 1..50) { "每一侧请输入 1–50 行活度与系数；若该侧只有纯物质，可输入 1,1。" }
            return lines.sumOf { line ->
                val parts = line.trim().split(Regex("[,，;；\\s]+"))
                require(parts.size == 2) { "每行格式为 活度,系数，例如 0.1,2。" }
                val activity = pos(parts[0].toDoubleOrNull() ?: throw IllegalArgumentException("活度应为数字。"), "活度")
                val exponent = pos(parts[1].toDoubleOrNull() ?: throw IllegalArgumentException("系数应为数字。"), "系数")
                exponent * ln(activity)
            }
        }
        val logQ = sumLogs("products") - sumLogs("reactants"); val k = pos(n(input, "constant", "平衡常数"), "K")
        val delta = logQ - ln(k)
        val direction = if (abs(delta) < 1e-9) "处于平衡（数值容差内）" else if (delta < 0) "反应自发趋势：正向" else "反应自发趋势：逆向"
        val q = exp(logQ)
        CalculationResult("反应商 Q", f(q), rows = listOf(ResultRow("与 K 比较", direction), row("K", k)), steps = listOf("ln Q = Σν生成物 ln a生成物 − Σν反应物 ln a反应物 = ${f(logQ)}。", "Q < K 时向生成物方向趋于平衡；Q > K 时向反应物方向。"), notes = listOf("使用无量纲活度；稀溶液可用 c/(1 mol/L) 近似，理想气体可用 p/p° 近似。该工具判断方向，不求多组分平衡组成。"))
    }

    private fun solubilityTool() = ToolDefinition("solubility", "Ksp 与溶解平衡", "二元难溶盐的溶解度与同离子效应", ToolCategory.EQUILIBRIUM, "AₐBᵦ(s) ⇌ aA + bB；Ksp = (cA₀+as)^a(cB₀+bs)^b",
        listOf(field("ksp", "溶度积 Ksp", "1.8e-10", "可从常数库选取；默认 AgCl 近似值"), field("a", "每式量单位释放 A 的数目 a", "1"), field("b", "每式量单位释放 B 的数目 b", "1"), field("commonA", "溶解前 A 浓度 / mol·L⁻¹", "0"), field("commonB", "溶解前 B 浓度 / mol·L⁻¹", "0"), field("molarMass", "盐的摩尔质量 / g·mol⁻¹", "143.32"))) { input ->
        val ksp = n(input, "ksp", "Ksp"); val a = int(input, "a", "a", 1..6); val b = int(input, "b", "b", 1..6)
        val ca = n(input, "commonA", "初始 A 浓度"); val cb = n(input, "commonB", "初始 B 浓度"); val mw = pos(n(input, "molarMass", "摩尔质量"), "摩尔质量")
        val result = PhysicalMath.solubility(ksp, a, b, ca, cb)
        CalculationResult("摩尔溶解度 s", f(result.solubility), "mol/L", listOf(row("质量溶解度", result.solubility * mw, "g/L"), row("平衡 [A]", result.ionA, "mol/L"), row("平衡 [B]", result.ionB, "mol/L")),
            listOf("设每升溶解 s mol 盐，则 [A] = ${f(ca)} + $a s，[B] = ${f(cb)} + $b s。", "代入 Ksp = [A]^$a [B]^$b = ${f(ksp)}，在 s ≥ 0 上求唯一根。", "质量溶解度 = s mol/L × ${f(mw)} g/mol。"), notes = listOf("假定溶液稀、固体足量且忽略酸碱副反应、络合与活度系数。输入离子计量数必须对应同一盐；过饱和初态会明确报错。"))
    }

    private fun complexTool() = ToolDefinition("complex", "配合物稳定平衡", "一比一 M + L ⇌ ML 的物料守恒解", ToolCategory.EQUILIBRIUM, "Kf = [ML]/([M][L])；[ML] = x",
        listOf(field("kf", "形成常数 Kf", "1e6"), field("metal", "金属分析总浓度 / mol·L⁻¹", "0.001"), field("ligand", "配体分析总浓度 / mol·L⁻¹", "0.001"))) { input ->
        val kf = n(input, "kf", "形成常数"); val metal = n(input, "metal", "金属总浓度"); val ligand = n(input, "ligand", "配体总浓度")
        val result = PhysicalMath.complex(kf, metal, ligand)
        CalculationResult("配合物 [ML]", f(result.bound), "mol/L", listOf(row("游离 [M]", result.freeMetal, "mol/L"), row("游离 [L]", result.freeLigand, "mol/L"), row("金属配合率", if (metal == 0.0) 0.0 else result.bound / metal * 100, "%")),
            listOf("金属守恒：[M]+[ML] = ${f(metal)} mol/L；配体守恒：[L]+[ML] = ${f(ligand)} mol/L。", "在 0 ≤ x ≤ min(CM,CL) 内求解 x = Kf(CM−x)(CL−x)。"), notes = listOf("仅适用于一个 1:1 配合物，忽略多级配位、配体质子化、金属水解和活度修正；实际复杂体系应使用条件稳定常数。"))
    }

    private fun arrheniusTool() = ToolDefinition("arrhenius", "阿伦尼乌斯方程", "由两温度速率常数求活化能，或预测新温度速率", ToolCategory.PHYSICAL, "ln(k₂/k₁) = −Ea/R × (1/T₂−1/T₁)",
        listOf(select("unknown", "待求变量", "ea", "ea" to "活化能 Ea", "k2" to "第二温度的速率常数 k₂"), field("k1", "速率常数 k₁（与 k₂ 同单位）", "0.01"), field("k2", "速率常数 k₂", "0.02"), field("t1", "温度 T₁", "298.15"), field("t2", "温度 T₂", "308.15"), select("tempUnit", "两次温度单位", "K", "K" to "K", "C" to "°C"), field("ea", "活化能 / kJ·mol⁻¹（求 k₂ 时）", "50"))) { input ->
        val k1 = n(input, "k1", "k₁"); val t1 = temp(input, "t1", "tempUnit"); val t2 = temp(input, "t2", "tempUnit")
        val solveEa = s(input, "unknown") == "ea"
        val ea = if (solveEa) PhysicalMath.activationEnergy(k1, n(input, "k2", "k₂"), t1, t2) else n(input, "ea", "活化能") * 1000
        val k2 = if (solveEa) n(input, "k2", "k₂") else PhysicalMath.rateAtTemperature(k1, ea, t1, t2)
        CalculationResult(if (solveEa) "表观活化能 Ea" else "第二温度速率常数 k₂", f(if (solveEa) ea / 1000 else k2), if (solveEa) "kJ/mol" else "与 k₁ 同单位",
            listOf(row("Ea", ea / 1000, "kJ/mol"), row("k₂", k2, "与 k₁ 同单位")), listOf("T₁ = ${f(t1)} K；T₂ = ${f(t2)} K。", "ln(k₂/k₁) = ${f(ln(k2) - ln(pos(k1, "k₁")))}；R = ${f(PhysicalMath.R)} J/(mol·K)。", "Ea 先按 J/mol 计算，再除以 1000 转为 kJ/mol。"), notes = listOf("假定温度区间内反应机理不变、指前因子与活化能近似恒定。负值为表观活化能，可能反映复杂机理。"))
    }

    private fun firstOrderTool() = ToolDefinition("first_order", "一级反应动力学", "半衰期、速率常数与剩余浓度", ToolCategory.PHYSICAL, "t½ = ln2/k；C(t) = C₀ exp(−kt)",
        listOf(select("unknown", "计算内容", "half", "half" to "由 k 求半衰期", "rate" to "由半衰期求 k", "remaining" to "由 k 与时间求剩余浓度"), field("k", "速率常数 k / s⁻¹", "0.01"), field("halfLife", "半衰期 / s（求 k 时）", "69.314718"), field("time", "反应时间 / s", "100"), field("initial", "初始浓度 / mol·L⁻¹", "1"))) { input ->
        val mode = s(input, "unknown"); val k = if (mode == "rate") ln(2.0) / pos(n(input, "halfLife", "半衰期"), "半衰期") else pos(n(input, "k", "速率常数"), "速率常数")
        val half = ln(2.0) / k
        if (mode == "remaining") {
            val t = nonneg(n(input, "time", "反应时间"), "时间"); val initial = nonneg(n(input, "initial", "初始浓度"), "浓度"); val remaining = initial * exp(-k * t)
            CalculationResult("剩余浓度 C(t)", f(remaining), "mol/L", listOf(row("半衰期", half, "s"), row("剩余比例", exp(-k * t) * 100, "%")), listOf("kt = ${f(k)} s⁻¹ × ${f(t)} s = ${f(k * t)}，指数无量纲。", "C(t) = ${f(initial)} mol/L × exp(−kt)。"), listOf("适用于不可逆一级反应或条件固定的准一级反应。"))
        } else CalculationResult(if (mode == "rate") "一级速率常数 k" else "半衰期 t½", f(if (mode == "rate") k else half), if (mode == "rate") "s⁻¹" else "s", listOf(row("k", k, "s⁻¹"), row("t½", half, "s")), listOf("一级反应在 C/C₀ = 1/2 时，kt½ = ln2。", "t½ = 0.69314718056/k；单位由 1/(s⁻¹) 得到 s。"), listOf("一级反应半衰期与初始浓度无关；不能用于零级或二级反应。"))
    }

    private fun hessTool() = ToolDefinition("hess", "盖斯定律焓变叠加", "对已知反应乘系数或反向，叠加反应焓", ToolCategory.PHYSICAL, "ΔH目标 = Σ λᵢΔHᵢ",
        listOf(FieldSpec("reactions", "每行：反应说明 | 乘数 | ΔH / kJ", "C + O2 → CO2 | 1 | -393.5\nCO + 0.5O2 → CO2 | -1 | -283.0", "负乘数表示逆反应；ΔH 对应该行反应式的一次化学计量过程", multiline = true))) { input ->
        val text = s(input, "reactions"); require(text.length <= 12000) { "输入过长，请限制为 50 条以内的反应。" }
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList(); require(lines.size in 1..50) { "请输入 1–50 条已知反应。" }
        val rows = mutableListOf<ResultRow>(); val steps = mutableListOf<String>(); var total = 0.0
        lines.forEachIndexed { index, line ->
            val parts = line.split('|', '｜').map { it.trim() }; require(parts.size == 3 && parts[0].isNotBlank()) { "第 ${index + 1} 行格式应为 反应说明 | 乘数 | ΔH。" }
            val multiplier = parts[1].toDoubleOrNull() ?: throw IllegalArgumentException("第 ${index + 1} 行乘数必须为数字。")
            val enthalpy = parts[2].toDoubleOrNull() ?: throw IllegalArgumentException("第 ${index + 1} 行焓变必须为数字。")
            PhysicalMath.finite(multiplier, "乘数"); PhysicalMath.finite(enthalpy, "焓变")
            val contribution = multiplier * enthalpy; total += contribution
            rows += row("反应 ${index + 1} 的贡献", contribution, "kJ")
            steps += "${parts[0]}：${f(multiplier)} × (${f(enthalpy)} kJ) = ${f(contribution)} kJ。"
        }
        steps += "将上述经过倍乘的反应相加并消去两侧相同物种；对应焓变相加。"
        CalculationResult("组合反应焓变 ΔH", f(total), "kJ / 所列组合反应", rows, steps, listOf("本工具计算指定线性组合的焓变，不自动求目标反应的组合系数。请先配平各反应并保留物相，确保温度、压力与标准态一致；反向反应的 ΔH 变号，倍乘反应的 ΔH 同比变化。"))
    }
}
