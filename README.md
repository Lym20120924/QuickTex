# QuickTeX ✨

> **离线 · 可视化 · 印刷级排版**  
> Android 平台所见即所得的 LaTeX 公式编辑器，让公式输入像打字一样自然。

[![Platform](https://img.shields.io/badge/platform-Android-brightgreen)](https://github.com/yourusername/QuickTeX)
[![API](https://img.shields.io/badge/API-21%2B-blue)](https://developer.android.com/)
[![License](https://img.shields.io/badge/license-MIT-yellow)](LICENSE)
[![PRs Welcome]()](CONTRIBUTING.md)

---

## 📱 为什么做 QuickTeX？

在 Android 端，**始终缺少一款像 MathType 或 AxMath 那样“可视化即所得”的 LaTeX 编辑器**。  
不会 LaTeX 语法的学生、教师、科研人员，想要精确描述一个公式，往往只能依赖网页端或复杂的键盘代码，门槛高、效率低。

我设计了这款**完全离线**的安卓工具，让你**摆脱网络依赖**，在任何地方都能优雅地写出专业公式。




##🛠️ 技术栈

- Kotlin + Android（minSdk 26 / Android 8.0+，targetSdk 35）
- 编辑器：自绘 Canvas 视图（结构树模型：分数、根号、上下标、矩阵、cases 均为节点）
- 渲染：KaTeX v0.16.22（MIT 协议）+ mhchem，全部资源打包进 APK
- 存储：SharedPreferences（收藏/历史）
- 构建：Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.3.21

## 📖目录结构

```
app/src/main/java/com/formulastudio/app/
├── MainActivity.kt        主界面（预览 + 公式框 + 编辑器 + 键盘）
├── LibraryActivity.kt     收藏夹 / 历史
├── editor/                编辑器核心
│   ├── Formula.kt         结构树节点 + 光标模型
│   ├── LatexSerializer.kt 结构树 → LaTeX（干净版 / 预览版带 cssId）
│   ├── LatexParser.kt     LaTeX → 结构树（反向同步）
│   ├── FormulaEditorView.kt 自绘编辑器视图
│   ├── SymbolAction.kt    键盘动作定义
│   └── CmdDisplay.kt      命令 → 显示字符映射
├── keyboard/              符号键盘（10 分类）
├── export/PngExporter.kt  预览截图导出 PNG
└── util/Store.kt          收藏/历史持久化
app/src/main/assets/
├── preview.html           预览页（离线 KaTeX + 点击联动）
└── katex/                 离线渲染引擎资源
```

## 💡构建

```bash
# 方式一：Android Studio 直接打开本目录，等同步完成点 Run
# 方式二：命令行
export JAVA_HOME="<你的JDK17+路径>"
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 🧩使用提示

1. 点下方符号按钮插入结构，虚线框 = 待填位置
2. 点虚线框，或直接点预览里对应位置，光标即定位到那里
3. 字母数字用手机键盘直接输入（点键盘左下角 ABC 唤出）
4. 随时可在中间代码框手改 LaTeX，图形和预览同步更新
5. 长公式可在编辑区左右拖动滚动

## ⭕️已知限制

- 反向同步以符号键盘覆盖的命令为准；键盘外的复杂手写 LaTeX 会原样显示
- 只有浅色主题
- debug 版签名



---

## 📄 开源协议

- 本应用源码：MIT License（见 LICENSE）
- KaTeX：MIT License
- Material Icons：Apache License 2.0。

---

*Made with ❤️ for all math lovers.*
