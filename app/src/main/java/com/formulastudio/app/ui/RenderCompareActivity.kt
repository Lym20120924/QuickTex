package com.formulastudio.app.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.formulastudio.app.editor.FormulaLayout
import com.formulastudio.app.editor.FormulaRenderer
import com.formulastudio.app.editor.LatexParser
import com.formulastudio.app.render.AndroidLayoutMetrics
import com.formulastudio.app.render.CanvasTarget
import com.formulastudio.app.render.ComposeTarget
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Prefs

/**
 * 4.4b 等价验证页：同一公式分别用
 *  - Android Canvas（CanvasTarget）
 *  - Compose DrawScope（ComposeTarget）
 * 绘制，排版与绘制逻辑都来自 core，用于逐像素对比两个后端。
 */
class RenderCompareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        setContent {
            QuickTexTheme {
                CompareScreen()
            }
        }
    }
}

private const val LATEX = "\\frac{a}{b}+\\sqrt{c}\\cdot\\sum_{i=1}^{n}x_i"

/** Android Canvas 后端：用 core 的 FormulaRenderer 画到系统 Canvas */
private class CoreCanvasView(
    context: Context,
    latex: String,
    private val inkColor: Int
) : View(context) {

    private val density = resources.displayMetrics.density
    private val engine = FormulaLayout(AndroidLayoutMetrics(resources))
    private val root = LatexParser().parse(latex)
    private val box = engine.layout(root, 40f * density)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        FormulaRenderer(engine, inkColor).draw(CanvasTarget(canvas, resources), root, box)
    }
}

@Composable
private fun CompareScreen() {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()

    val engine = remember { FormulaLayout(AndroidLayoutMetrics(ctx.resources)) }
    val root = remember { LatexParser().parse(LATEX) }
    val box = remember(density) { engine.layout(root, 40f * density) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(shadowElevation = 2.dp) {
            Text(
                "上：Android Canvas　下：Compose DrawScope（同一 core 排版 + 绘制）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { c -> CoreCanvasView(c, LATEX, ink) },
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            FormulaRenderer(engine, ink).draw(ComposeTarget(this, measurer), root, box)
        }
    }
}
