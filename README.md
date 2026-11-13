# QuickTex ✨

> **离线 · 可视化 · 印刷级排版**  
> Android 平台所见即所得的 LaTeX 公式编辑器，让公式输入像打字一样自然。

[![Platform](https://img.shields.io/badge/platform-Android-brightgreen)](https://github.com/yourusername/QuickTex)
[![API](https://img.shields.io/badge/API-26%2B-blue)](https://developer.android.com/)
[![License](https://img.shields.io/badge/license-MIT-yellow)](LICENSE)
[![PRs Welcome]()](CONTRIBUTING.md)

---

## 📱 为什么做 QuickTex？

在 Android 端，**始终缺少一款像 MathType 或 AxMath 那样“可视化即所得”的 LaTeX 编辑器**。  
不会 LaTeX 语法的学生、教师、科研人员，想要精确描述一个公式，往往只能依赖网页端或复杂的键盘代码，门槛高、效率低。

我设计了这款**完全离线**的安卓工具，让你**摆脱网络依赖**，在任何地方都能优雅地写出专业公式。

---

## 🛠️ 技术栈

- Kotlin + Android（minSdk 26 / Android 8.0+，targetSdk 35）
- 编辑器：自绘 Canvas 视图（结构树模型：分数、根号、上下标、矩阵、cases 均为节点）
- 渲染：KaTeX 字体（res/font 字形级回退）+ 自绘排版引擎
- SVG 导出：内置 KaTeX v0.16.22 + mhchem（离线 WebView，纯路径矢量输出）
- 存储：SharedPreferences（收藏 / 历史）
- 界面：Material 3 DayNight（自动跟随系统深色模式），中 / 英双语
- 构建：Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.3.21

## 📖 目录结构

```
app/src/main/java/com/formulastudio/app/
├── MainActivity.kt        主界面（预览 + 公式框 + 编辑器 + 键盘）
├── LibraryActivity.kt     收藏夹 / 历史（搜索 + 排序 + 清空）
├── editor/                编辑器核心
│   ├── Formula.kt         结构树节点 + 光标模型
│   ├── LatexSerializer.kt 结构树 → LaTeX（干净版 / 预览版带 cssId）
│   ├── LatexParser.kt     LaTeX → 结构树（反向同步）
│   ├── FormulaEditorView.kt 自绘编辑器视图
│   ├── SymbolAction.kt    键盘动作定义
│   └── CmdDisplay.kt      命令 → 显示字符映射
├── keyboard/              符号键盘（9 大分类）
├── export/
│   ├── PngSaver.kt        PNG 图片导出
│   └── SvgExporter.kt     SVG 矢量图导出（离线 KaTeX）
└── util/Store.kt          收藏 / 历史持久化
app/src/main/assets/
├── preview.html           SVG 渲染页（离线 KaTeX + mhchem）
└── katex/                 离线渲染引擎资源（JS / CSS / 字体）
```

## 🚀 构建

```bash
# 方式一：Android Studio 直接打开本目录，等同步完成点 Run
# 方式二：命令行
export JAVA_HOME="<你的JDK17+路径>"
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 打 release 签名包

1. 准备一个密钥库（没有就用 keytool 生成一个）：

```bash
keytool -genkeypair -v -keystore keystore/quicktex-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias quicktex \
  -storepass <密码> -keypass <密码> \
  -dname "CN=QuickTex Release, OU=FormulaStudio, O=FormulaStudio, C=CN"
```

2. 复制 `keystore.properties.example` 为 `keystore.properties`，填入密钥信息
   （`keystore.properties` 与 `keystore/` 已在 `.gitignore` 中，不会上传）

3. 构建：

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## ✨ 功能

- 📐 **可视化编辑**：分数 / 根号 / 上下标 / 矩阵 / 分段函数 / 化学方程式，全部图形化插入
- ⌨️ **9 大符号键盘**：基础 · 集合 · 关系 · 大运算 · 希腊 · 分析 · 矩阵 · 上下标 · 系统键盘
- 🔁 **双向同步**：代码框可手改 LaTeX，图形与预览实时联动；反向同步支持更多手写命令
- 👆 **点哪去哪**：点虚线占位框或预览里对应位置，光标即刻定位
- 🖼️ **导出 PNG / SVG**：PNG 3 倍清晰度；SVG 为纯路径矢量，可无限放大
- 📋 **复制图片**：公式一键复制到剪贴板
- ⭐ **收藏 & 历史**：支持搜索、按时间 / 名称排序、清空
- 🌙 **深色模式**：跟随系统自动切换
- 🌍 **中英双语**：跟随系统语言
- ↩️ **撤销 / 重做**
- 💾 **自动保存**：退出自动记住当前公式

## 🧩 使用提示

1. 点下方符号按钮插入结构，虚线框 = 待填位置
2. 点虚线框，或直接点预览里对应位置，光标即定位到那里
3. 字母数字用手机键盘直接输入（点键盘左下角 ABC 唤出）
4. 随时可在中间代码框手改 LaTeX，图形和预览同步更新
5. 长公式可在编辑区左右拖动滚动

## ⭕️ 已知限制

- 反向同步以符号键盘覆盖的命令为准；键盘外的复杂手写 LaTeX 会原样显示
- SVG 导出依赖系统 WebView

---

## 📄 开源协议

- 本应用源码：MIT License（见 LICENSE）
- KaTeX：MIT License
- Material Icons：Apache License 2.0。

---

*Made with ❤️ for all math lovers.*
