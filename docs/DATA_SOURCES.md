# 离线化学数据来源与适用范围

本文件记录 2026-11-21 获取的离线数据快照。应用运行时不请求网络，不将用户输入发送至这些站点。

## 元素

- [CIAAW Abridged Standard Atomic Weights](https://ciaaw.org/abridged-atomic-weights.htm)：84 个有标准原子量的元素采用此表的简明数值。天然同位素组成会变化，标准原子量不是单个原子的精确质量；应用结果的显示位数不能提高来源精度。比如 H = 1.0080、C = 12.011、Fe = 55.845、Cu = 63.546。
- [PubChem Periodic Table](https://pubchem.ncbi.nlm.nih.gov/periodic-table/)，[官方 JSON](https://pubchem.ncbi.nlm.nih.gov/rest/pug/periodictable/JSON)：118 元素的序数、符号、英文名称、电子排布、电负性、原子半径、熔点、沸点、常见氧化态等。数据中的空值保留为缺失，不预测或补造。
- 没有 CIAAW 标准原子量的 34 个元素采用 PubChem 所列代表同位素质量，`massIsRepresentative=true`；它们不是标准原子量，也不表示任意同位素标记样品的质量。
- 电负性为 PubChem 所列 Pauling 量表数值；半径单位 pm；熔点和沸点单位 K。半径定义和熔沸点实验条件以原来源为准，不能把所有熔点均理解为 1 atm 条件，例如氦有特殊压力条件。电子排布标为 predicted 的内容保留原标记。
- 镧系与锕系在独立附表行展示，`group=null`，不替第 3 族归属争议作判断。
- 科学数值属于事实数据；本项目对 NIH/PubChem、CIAAW 作来源标注，不复制其站点图片或品牌内容。重新分发数据时亦应保留来源，并注意 PubChem 原始第三方数据的使用条件。
- `scripts/update-elements.ps1` 可重新下载官方数据并生成 `Elements.kt`；更新后须重新执行数据完整性和计算回归测试。

## 物理常数、平衡常数与电极电势

- SI 定义常数与派生常数：[NIST Constants](https://physics.nist.gov/cuu/Constants/index.html)、[BIPM SI Brochure](https://www.bipm.org/en/publications/si-brochure)。Nₐ、kᵦ、e、h、c 为定义值；R=Nₐkᵦ、F=Nₐe，显示浮点数会有舍入。
- [OpenStax Chemistry 2e, H 弱酸电离常数](https://openstax.org/books/chemistry-2e/pages/h-ionization-constants-of-weak-acids)、[I 弱碱电离常数](https://openstax.org/books/chemistry-2e/pages/i-ionization-constants-of-weak-bases)：乙酸、甲酸、HF 与 NH₃ 采用表列 Ka/Kb，pKa/pKb 通过 −log₁₀K 派生。为保持同源一致性，未混用其他版本常见的 pKa 小数值。
- [J 溶度积](https://openstax.org/books/chemistry-2e/pages/j-solubility-products)：AgCl 1.6×10⁻¹⁰、CaF₂ 4.0×10⁻¹¹、Cu(OH)₂ 2.2×10⁻²⁰（25 °C）。不同资料及离子强度下可有不同数值；用户可以自行输入。
- [K 配离子形成常数](https://openstax.org/books/chemistry-2e/pages/k-formation-constants-for-complex-ions)：Fe³⁺ + SCN⁻ ⇌ FeSCN²⁺，Kf = 8.9×10²，用于简单 1:1 近似模型。
- [L 标准电极半电池电势](https://openstax.org/books/chemistry-2e/pages/l-standard-electrode-half-cell-potentials)：H⁺/H₂ 0 V、Cu²⁺/Cu 0.337 V、Zn²⁺/Zn −0.7618 V、Ag⁺/Ag 0.7996 V，相对标准氢电极，25 °C 标准态。
- OpenStax Chemistry 2e，Paul Flowers、Klaus Theopold、Richard Langley、William R. Robinson，Rice University，按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 授权。此处仅摘取少量数值，中文说明与计算表达为本项目编写。
- 热力学平衡常数严格以相对标准态的活度定义，为无量纲值；本应用教学模型用 mol/L 浓度数值近似活度，不包含活度系数、混合溶剂或多重竞争平衡。

## 结构模型

- VSEPR 使用单中心、2–6 电子域的标准教学模型；双键或三键各算一个电子域。几何是电子对排斥模型的预测，并非量子化学结构优化，孤对电子会使实际键角偏离理想角。
- Lewis 和 3D 图使用明确列出的常见小分子/离子模板；模型坐标为程序绘制的示意坐标，不是实验键长。并非任意分子 Lewis 求解器、任意名称命名引擎或构象搜索器。
- 有机名称查询是离线小型结构字典，按完整名称、常见别名、SMILES 或所列分子式检索；遇到同分异构体会明确提示多个候选，不根据分子式猜测唯一结构。
- 不饱和度适用于常规闭壳层中性 CHNXO/S 化合物，不能据此确定原子的连接顺序、芳香性或异构体。
