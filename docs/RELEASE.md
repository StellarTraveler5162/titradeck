# TitraDeck 发行与填写信息

本项目的首次发行使用独立的发行签名。当前应用名、包名、版本和签名均已配置；直接安装或分发 APK 不需要再填写个人资料。

## 已配置的信息

| 项目 | 当前值 | 后续操作 |
| --- | --- | --- |
| 应用名称 | TitraDeck | 需要改名时修改 `app/src/main/res/values/strings.xml` |
| 应用包名 | `com.titradeck.app` | 首次发布前确定；发布后保持不变才能覆盖升级 |
| 展示版本 | `1.0.0` | 修改 `app/build.gradle.kts` 的 `versionName` |
| 内部版本号 | `4` | 每次对外更新增加 `versionCode` |
| 最低安卓版本 | Android 8.0 / API 26 | 已配置 |
| 目标安卓 API | 36 | 已配置 |
| 签名证书名称（CN） | TitraDeck | 是证书标识，不是商店实名认证信息 |
| 密钥别名（Key alias） | `titradeck-release` | 后续沿用 |
| 密钥类型 / 有效期 | PKCS12，RSA 3072 位，10000 天 | 已生成 |
| Keystore password / Key password | 已生成随机密码，PKCS12 两者相同 | 在本机 `.signing/release.properties` 查看，不在聊天中发送 |

证书的 Organization、Organizational Unit、City、State、Country 等主体字段并非构建 APK 的必填项，因此未虚构或填写个人身份。若平台要求真实的开发者或公司信息，应在该平台填写；无需为填写商店资料重新生成签名。

## 签名文件与备份

私有文件位于项目 `.signing/`：

- `titradeck-release.p12`：发行私钥及证书。
- `release.properties`：密钥文件位置、别名、密钥库密码与私钥密码。
- `titradeck-release-cert.pem`：公开证书，不含私钥。

生成脚本不会覆盖已有密钥。该目录已加入 `.gitignore`，Windows 权限仅授予当前用户和 SYSTEM；密码未打印到构建日志。密码保存在本机属性文件中，备份时请将私钥和属性文件一起保存在受保护的位置，不随 APK 分享。

后续版本必须复用这套发行签名。换电脑时恢复整个 `.signing/` 目录，并确保当前账户拥有读取权限；仅备份 APK 或公开证书无法恢复签名能力。

## 构建

```powershell
# 已经生成签名：直接构建、运行发行变体单元测试及 Lint，并校验 APK 签名和对齐
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release.ps1

# 平台要求 Android App Bundle 时额外生成 AAB
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release.ps1 -Bundle
```

`setup-release-signing.ps1` 仅用于首次创建签名，不应在后续更新中重新生成密钥。发行版启用 R8 代码压缩、混淆和资源缩减，并关闭调试。缺少本机签名时 `build-release.ps1` 会明确停止，不会拿调试密钥签署发行包。

输出位置：

- APK：`app/build/outputs/apk/release/app-release.apk`。
- 可选 AAB：`app/build/outputs/bundle/release/app-release.aab`。
- 混淆映射：`app/build/outputs/mapping/release/mapping.txt`，需要与每次发行的 APK 一起归档以还原崩溃堆栈。
- 单元测试报告：`app/build/reports/tests/testReleaseUnitTest/index.html`。
- Lint：`app/build/reports/lint-results-release.html`。

旧调试版与发行版签名不同，不能用发行 APK 直接覆盖调试版。卸载调试版会清除本机计算历史和设置；有重要记录时先保留需要的数据，再处理安装。之后同一发行签名和递增版本号的 APK 可以正常覆盖升级。

## 上架平台通常需要你补充的信息

以下信息不影响本地发行 APK 的生成；只有选择应用商店或公开发布渠道后才需要填写，具体字段以目标平台为准。

| 需要填写 / 准备 | 内容 |
| --- | --- |
| 开发者主体 | 个人或公司真实名称、平台实名认证及联系方式 |
| 支持邮箱 | 一个实际可联系的邮箱；当前项目未配置 |
| 隐私政策 | 公开可访问的隐私政策网址，写明本机历史与设置、无账号、无云同步等实际行为；不能编造网址 |
| 应用介绍 | 简介、详细介绍、分类（如教育 / 工具）、支持语言、更新说明 |
| 展示素材 | 应用截图、图标，以及平台要求的宣传图；`docs/branding` 已有设计源文件 |
| 平台声明 | 按实际情况填写数据安全、广告、年龄分级及平台要求的备案或资质信息 |

可作为介绍草稿：

> TitraDeck 是一款安卓化学计算工作台，包含化学式解析、摩尔质量、方程式配平、化学计量、浓度、pH、气体定律、118 元素周期表等工具，并支持在线查询 PubChem。计算过程展示公式、单位与适用条件，历史记录保存在本机。

当前版本无账号、广告、云同步和遥测实现。化合物查询使用 INTERNET 权限访问 PubChem；中文名称未命中时会向 MyMemory 发送查询名称。AndroidX 会合并应用内部的 signature 权限。数据安全声明应根据最终发行版本和实际发布方式填写。
