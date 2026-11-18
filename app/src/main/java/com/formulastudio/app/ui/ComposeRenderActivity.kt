package com.formulastudio.app.ui

import android.os.Bundle
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
import com.formulastudio.app.render.ComposeTarget
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Prefs

/**
 * 阶段 4.4b 验证页：用 Compose 的 [Canvas] / DrawScope 画公式。
 *
 * 排版（FormulaLayout）与绘制（FormulaRenderer）都在 core，
 * 这里只负责把 core 的 [com.formulastudio.core.render.RenderTarget]
 * 落到 Compose 画布上 —— 证明同一套逻辑可以在 View / SVG / PDF / Compose 间复用。
 */
class ComposeRenderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        setContent {
            QuickTexTheme {
                ComposeRenderScreen()
            }
        }
    }
}

@Composable
private fun ComposeRenderScreen() {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()

    val latex = "\\frac{a}{b}+\\sqrt{c}\\cdot\\sum_{i=1}^{n}x_i"
    val engine = remember { FormulaLayout(AndroidLayoutMetrics(ctx.resources)) }
    val root = remember(latex) { LatexParser().parse(latex) }
    val box = remember(latex, density) { engine.layout(root, 40f * density) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(shadowElevation = 2.dp) {
            Text(
                "Compose DrawScope 渲染（core 排版 + core 绘制）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 12.dp)
        ) {
            FormulaRenderer(engine, ink).draw(ComposeTarget(this, measurer), root, box)
        }
    }
}
