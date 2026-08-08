# QuickTex

一款完全离线的 Android 可视化 LaTeX 公式编辑器 —— 像 MathType / AxMath 一样，
不用懂 LaTeX，点符号按钮就能排版数学公式，适合老师、学生日常出题、做笔记。

## 功能一览

- **所见即所得编辑**：底部符号键盘按 10 类分页（基础运算 / 分式根号 / 上下标 / 大型运算符 / 希腊字母 / 英文字母 / 矩阵分段 / 高等数学 / 关系逻辑 / 化学），点一下即插入结构，虚线框是待填位置，点任意位置即可定位输入。
- **公式代码框**：实时显示 LaTeX 代码，可手动修改（反向同步回图形），一键复制。
- **高清预览**：内置 KaTeX 离线渲染，飞行模式可用；点击预览里的某个结构，光标自动跳到编辑器对应位置。
- **化学公式**：支持 `\ce{H2O}`、离子、反应方程式（mhchem）。
- **收藏夹 + 历史记录**：本地保存，随取随用。
- **导出图片**：公式一键导出为 PNG 并分享到微信/QQ/相册等。
- **撤销 / 重做**、方向键导航、系统键盘输入字母数字。

## 技术栈

- Kotlin + Android（minSdk 26 / Android 8.0+，targetSdk 35）
- 编辑器：自绘 Canvas 视图（结构树模型：分数、根号、上下标、矩阵、cases 均为节点）
- 渲染：KaTeX v0.16.22（MIT 协议）+ mhchem，全部资源打包进 APK
- 存储：SharedPreferences（收藏/历史）
- 构建：Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.3.21

## 目录结构

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

## 构建

```bash
# 方式一：Android Studio 直接打开本目录，等同步完成点 Run
# 方式二：命令行
export JAVA_HOME="<你的JDK17+路径>"
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 使用提示

1. 点下方符号按钮插入结构，虚线框 = 待填位置
2. 点虚线框，或直接点预览里对应位置，光标即定位到那里
3. 字母数字用手机键盘直接输入（点键盘左下角 ABC 唤出）
4. 随时可在中间代码框手改 LaTeX，图形和预览同步更新
5. 长公式可在编辑区左右拖动滚动

## 已知限制

- 反向同步以符号键盘覆盖的命令为准；键盘外的复杂手写 LaTeX 会原样显示
- 第一版为浅色主题（深色模式后续版本支持）
- debug 版签名，适合自用，未上架应用商店

## 开源协议

- 本应用源码：MIT License（见 LICENSE）
- KaTeX：MIT License
- Material Icons：Apache License 2.0

## 仓库地址

https://github.com/37-7/QuickTex
