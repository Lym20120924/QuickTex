package com.formulastudio.app.ui.compose

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.formulastudio.app.editor.FormulaEditorView

/**
 * 把现有的自绘公式编辑器嵌入 Compose。
 *
 * 阶段 4.4 采用「互操作」而非「重写」：
 * - 公式编辑器的光标模型 / 触摸定位 / 布局树非常复杂，
 *   用 DrawScope 全部重写风险极高，且无法在真机外验证。
 * - 先用 [AndroidView] 桥接，让 Compose 页面能直接复用它的全部功能；
 *   后续再按模块把绘制层迁到 DrawScope（排版内核已是平台无关的
 *   [com.formulastudio.core.render.RenderTarget]，具备迁移条件）。
 *
 * @param onReady 视图创建完成时回调，便于外部调用 renderToBitmap / renderToSvg 等
 */
@Composable
fun FormulaEditor(
    modifier: Modifier = Modifier,
    onReady: (FormulaEditorView) -> Unit = {}
) {
    AndroidView(
        factory = { ctx ->
            FormulaEditorView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                onReady(this)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
