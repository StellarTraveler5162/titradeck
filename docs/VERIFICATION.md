# 0.2.1 验证记录

验证日期：2026-11-22。包名：`com.titradeck.app`；versionCode：3；最低 API 26，target/compile API 36。

## 自动检查

- JVM 单元与整合测试：45 项，0 失败、0 错误、0 跳过。
- Android 15 / API 35 模拟器工作流测试：包含系统主题色、自定义背景、设置持久化、底部导航几何和既有化学工作流。
- Android Lint：0 错误、0 警告。
- 调试 APK 的 APK Signature Scheme v2 签名验证通过。
- 核心计算和参考数据保持离线可用，PubChem 化合物查询使用网络。

单元测试覆盖公式与水合物、元素和电荷守恒、极限投料和产率、经验式、pH 水自电离、量纲、气体与温度边界、Ksp、极强/弱络合、24 个工具的默认示例、常数库填入、118 元素数据，以及 HEX 背景解析和对比度。

设备工作流覆盖：

1. 葡萄糖摩尔质量计算、历史保存和输入恢复、错误元素输入的反馈。
2. 元素搜索及性质弹窗、系统主题色与深色模式切换。
3. 自定义背景输入、预设颜色、非法 HEX 错误提示、重启后的偏好恢复。
4. 底部导航等宽、图标尺寸和标签居中检查。
5. 结构模型绘制与拖动旋转。

## 产物与截图

- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- [深色周期表](screenshots/TitraDeck/periodic-table.png)。

完整机器报告位于 `app/build/reports/tests/testDebugUnitTest`、`app/build/reports/androidTests/connected/debug` 和 `app/build/reports/lint-results-debug.html`。测试源码位于 `app/src/test` 和 `app/src/androidTest`。

## 图标接入验证

本轮为桌面、启动画面、首页、关于页和宽屏导航栏接入新的矢量图标，并提供主题图标用的单色蒙版。JVM 45 项测试通过；构建与 Lint 重新执行。圆形/圆角裁切、单色浅深主题以及 36/40/48/64 dp 尺寸的图稿已渲染检查，见 [图标预览](branding/icon-preview.png)。

前景与单色图标使用相同路径，应用内品牌图标使用相同图形并调整留白。以 10 倍分辨率栅格检查前景有效像素，最远距离约 32.55 dp，位于中央半径 33 dp 的安全圆内。

用户已关闭模拟器，本轮未再次启动模拟器或重跑设备工作流。前述设备测试和截图来自图标接入前的主题版本；图标预览属于资源渲染图，不是设备截图。

## 验证范围

已在 Android 15 x86_64 模拟器运行并检查实际截图。未在厂商实体设备上验证；应用使用系统动态主题色和标准 Material 3 控件，不依赖厂商私有组件。模型和数据的适用边界见 [FEATURES.md](FEATURES.md) 与 [DATA_SOURCES.md](DATA_SOURCES.md)。

之后修改源码或图标资源并重新编译时，应重新执行相应验证。




