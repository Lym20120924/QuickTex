# QuickTex ✨

> **离线 · 可视化 · 印刷级排版**  
> Android 平台的 LaTeX 公式编辑器，让公式输入像打字一样自然。  
> 正在演进为**模块化办公套件**（公式 / 文档 / 表格 / PDF）。

[![Platform](https://img.shields.io/badge/platform-Android-brightgreen)](https://github.com/Lym20120924/QuickTex)
[![API](https://img.shields.io/badge/API-26%2B-blue)](https://developer.android.com/)
[![License](https://img.shields.io/badge/license-MIT-yellow)](LICENSE)

---

## 🧱 架构（v3 重构）

```
QuickTex/
├── core/                    内核模块（纯 Kotlin，零 Android 依赖）
│   └── com/formulastudio/
│       ├── app/editor/      公式结构树 / LaTeX 双向转换 / 符号动作
│       └── core/render/     渲染抽象：RenderTarget + TextStyle + Ink + PathOp
└── app/                     界面 + 渲染后端实现
    └── com/formulastudio/app/
        ├── render/          🖥️ CanvasTarget / 📐 SvgTarget / 📄 PdfTarget
        ├── editor/          自绘公式编辑器
        ├── doc/             LaTeX 文档渲染 + 导出（PDF / Word / SVG）
        ├── keyboard/        符号键盘
        └── ...
```

**设计原则**

- **内核与平台解耦**：`core` 只做排版，不碰 Android API → 可单元测试、可复用
- **渲染可插拔**：内核产出平台无关的绘图意图，由 `RenderTarget` 的各后端落地  
  （同一份公式 → Canvas / SVG / PDF 效果完全一致）
- **UI 与引擎分离**：界面只负责输入与交互，排版结果全部来自内核

---

## 🛠️ 技术栈

- Kotlin + Android（minSdk 26 / Android 8.0+，targetSdk 35）
- 公式排版：自研结构树引擎（分数 / 根号 / 上下标 / 矩阵 / cases / 化学方程式）
- 字体：KaTeX 字体（字形级回退）
- 渲染：`RenderTarget` 抽象 + Canvas / SVG / PDF 三后端
- 文档渲染：整份 `.tex` → 文本 / 公式 / TikZ / pgfplots 逐块渲染
- 导出：PDF（系统 `PdfDocument`）、Word docx（手写最小 OOXML，零依赖）、SVG
- 存储：SharedPreferences（收藏 / 历史）
- 界面：Material 3 DayNight，中 / 英双语
- 构建：Gradle 9.7 / AGP 9.3.1 / Kotlin 2.3.21

---

## 📖 目录结构

```
core/src/main/java/com/formulastudio/
├── app/editor/
│   ├── Formula.kt            结构树节点 + 光标模型
│   ├── LatexParser.kt        LaTeX → 结构树
│   ├── LatexSerializer.kt    结构树 → LaTeX
│   ├── SymbolAction.kt       键盘动作定义
│   └── CmdDisplay.kt         命令 → 显示字符映射
└── core/render/
    ├── RenderTarget.kt       渲染后端接口
    ├── TextStyle.kt          平台无关文字样式
    ├── Ink.kt                平台无关描边/填充
    └── PathOp.kt             平台无关路径命令

app/src/main/java/com/formulastudio/app/
├── editor/FormulaEditorView.kt  自绘编辑器（屏幕 / 导出共用排版）
├── render/
│   ├── CanvasTarget.kt      Canvas 后端
│   ├── SvgTarget.kt         SVG 后端（内嵌 KaTeX 字体）
│   ├── PdfTarget.kt         PDF 后端
│   ├── PaintAdapters.kt     Paint → TextStyle / Ink
│   ├── pdf/PdfViewerActivity.kt  PDF 离线查看
│   └── FontFaces.kt         字体 CSS 生成
├── doc/
│   ├── LatexDocument.kt     整份 .tex → 文本 / 公式 / 图形块
│   ├── FigureRenderer.kt    TikZ / pgfplots 渲染
│   ├── BlockRenderer.kt     块 → PNG / SVG
│   └── DocExporter.kt       导出 PDF / Word / Excel
├── keyboard/                符号键盘（9 大分类）
└── util/                    设置 / 收藏 / 历史
```

---

## 🚀 构建

```bash
export JAVA_HOME="<你的JDK17+路径>"
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # 单元测试
```

### 打 release 签名包

1. 生成密钥库（没有就执行）：

```bash
keytool -genkeypair -v -keystore keystore/quicktex-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias quicktex \
  -storepass <密码> -keypass <密码> \
  -dname "CN=QuickTex Release, OU=FormulaStudio, O=FormulaStudio, C=CN"
```

2. 复制 `keystore.properties.example` 为 `keystore.properties` 并填写（已在 `.gitignore` 中）

3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

---

## ✨ 功能

- 📐 **可视化编辑**：分数 / 根号 / 上下标 / 矩阵 / 分段函数 / 化学方程式
- ⌨️ **9 大符号键盘**：基础 · 集合 · 关系 · 大运算 · 希腊 · 分析 · 矩阵 · 上下标 · 系统键盘
- 🔁 **双向同步**：代码框可手改 LaTeX，图形与预览实时联动
- 👆 **点哪去哪**：点虚线占位框或预览对应位置，光标即刻定位
- 🖼️ **导出 PNG / SVG / PDF / Word / Excel**：全部零第三方依赖
- 📄 **PDF 查看**：系统 PdfRenderer，离线翻页
- 📄 **LaTeX 文档渲染**：整份 `.tex` 逐块渲染，支持 TikZ / pgfplots
- ⭐ **收藏 & 历史**：搜索、排序、清空
- 🌙 **深色模式** · 🌍 **中英双语** · ↩️ **撤销重做** · 💾 **自动保存**
- 🎨 **外观设置中心**：界面风格 / 导航栏 / 配色 / 显示模式 / 启动画面与图标

---

## ⭕️ 已知限制

- 反向同步以符号键盘覆盖的命令为准；键盘外的复杂手写 LaTeX 会原样显示
- TikZ / pgfplots 仅支持常用子集
- Word 导出中公式以图片嵌入（非 OMML 原生公式）

---

## 📄 开源协议

- 本应用源码：MIT License（见 LICENSE）
- KaTeX：MIT License
- Material Icons：Apache License 2.0

---

*Made with ❤️ for all math lovers.*
