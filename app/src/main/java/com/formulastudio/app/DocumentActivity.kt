package com.formulastudio.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.formulastudio.app.doc.BlockRenderer
import com.formulastudio.app.doc.DocExporter
import com.formulastudio.app.doc.LatexDocument
import com.formulastudio.app.pdf.PdfViewerActivity
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Prefs
import com.formulastudio.core.doc.DocxReader
import com.formulastudio.core.model.DocBlock
import java.io.File

/**
 * LaTeX 文档渲染（阶段 4.3：已迁移到 Compose）。
 *
 * 把整份 .tex 拆成 文本 / 公式 / 图形 块逐块渲染，
 * 支持导出 PDF / Word / Excel / SVG，以及应用内查看 PDF。
 */
class DocumentActivity : ComponentActivity() {

    /** 渲染结果：文本或位图（导出器依赖它，保持 public） */
    data class Item(val isText: Boolean, val text: String, val bitmap: Bitmap?)

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        val initial = intent?.getStringExtra("latex") ?: ""
        setContent {
            QuickTexTheme {
                DocumentScreen(initial = initial, onClose = { finish() })
            }
        }
    }
}

@Composable
private fun DocumentScreen(initial: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val toast: (Int) -> Unit = { res -> Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show() }

    var source by remember { mutableStateOf(initial) }
    var items by remember { mutableStateOf<List<DocumentActivity.Item>>(emptyList()) }
    var mathLatex by remember { mutableStateOf<List<String>>(emptyList()) }
    /** docx 导入后的原始块（用于带样式导出，实现双向闭环） */
    var docBlocks by remember { mutableStateOf<List<DocBlock>>(emptyList()) }
    /** docx 导入的图片资源：文件名 → 字节 */
    var docImages by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }

    /** 从 docx uri 导入：读取字节 → DocxReader 解析 → DocBlock → 按样式渲染 → 显示 */
    fun importDocx(uri: android.net.Uri) {
        busy = true
        toast(R.string.document_rendering)
        Thread {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@Thread
            val result = DocxReader.parse(bytes) ?: return@Thread
            docBlocks = DocxReader.toDocBlocks(result)
            docImages = result.images
            // 按样式渲染成位图，包装为 Item 显示
            val rendered = ArrayList<DocumentActivity.Item>()
            val latex = ArrayList<String>()
            for ((block, bmp) in BlockRenderer.renderBlocksToBitmaps(ctx, docBlocks, docImages)) {
                when (block) {
                    is DocBlock.Text -> rendered.add(DocumentActivity.Item(true, block.text, null))
                    is DocBlock.Math -> {
                        latex.add(block.latex)
                        rendered.add(DocumentActivity.Item(false, block.latex, bmp))
                    }
                    is DocBlock.Figure -> rendered.add(DocumentActivity.Item(false, block.source, bmp))
                    is DocBlock.Table -> rendered.add(DocumentActivity.Item(false, block.rows.joinToString { it.joinToString(" | ") }, bmp))
                    is DocBlock.Image -> rendered.add(DocumentActivity.Item(false, block.src, bmp))
                }
            }
            items = rendered
            mathLatex = latex
            source = "" // 导入 docx 后清空 LaTeX 源码框
            status = ctx.getString(R.string.document_math_count, latex.size,
                rendered.count { !it.isText }) + " (来自 docx 导入)"
            busy = false
        }.start()
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { u -> if (u != null) importDocx(u) }

    /** 渲染：解析放后台，位图必须在主线程创建 */
    fun render() {
        if (source.isBlank()) { toast(R.string.document_empty); return }
        busy = true
        toast(R.string.document_rendering)
        Thread {
            val blocks = LatexDocument.parse(source)
            val rendered = ArrayList<DocumentActivity.Item>()
            val latex = ArrayList<String>()
            for (b in blocks) {
                when (b.type) {
                    LatexDocument.Type.TEXT -> if (b.text.isNotBlank())
                        rendered.add(DocumentActivity.Item(true, b.text, null))
                    LatexDocument.Type.MATH -> {
                        latex.add(b.text)
                        rendered.add(
                            DocumentActivity.Item(false, b.text, BlockRenderer.mathBitmap(ctx, b.text, b.inline))
                        )
                    }
                    LatexDocument.Type.FIGURE ->
                        rendered.add(
                            DocumentActivity.Item(false, b.text, BlockRenderer.figureBitmap(ctx, b.figure, b.text))
                        )
                }
            }
            items = rendered
            mathLatex = latex
            status = ctx.getString(R.string.document_math_count, latex.size,
                rendered.count { !it.isText })
            busy = false
        }.start()
    }

    /** 导出文件并调起系统分享 */
    fun share(f: File?, type: String, titleRes: Int) {
        if (f == null) { toast(R.string.document_export_failed); return }
        try {
            val uri = FileProvider.getUriForFile(ctx, "com.formulastudio.app.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, ctx.getString(titleRes)))
        } catch (e: Exception) {
            toast(R.string.document_export_failed)
        }
    }

    fun export(block: () -> File?, ext: String, type: String, titleRes: Int) {
        busy = true
        toast(R.string.document_rendering)
        Thread {
            val f = block()
            busy = false
            share(f, type, titleRes)
        }.start()
    }

    fun openPdf() {
        busy = true
        toast(R.string.document_rendering)
        Thread {
            val f = DocExporter.toPdf(ctx, items)
            busy = false
            if (f == null) { toast(R.string.document_export_failed); return@Thread }
            ctx.startActivity(Intent(ctx, PdfViewerActivity::class.java)
                .putExtra(PdfViewerActivity.EXTRA_PATH, f.absolutePath))
        }.start()
    }

    fun copyAll() {
        if (mathLatex.isEmpty()) { toast(R.string.document_empty); return }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("latex", mathLatex.joinToString("\n\n")))
        Toast.makeText(ctx, ctx.getString(R.string.document_copy_done, mathLatex.size),
            Toast.LENGTH_SHORT).show()
    }

    fun copyOne(latex: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("latex", latex))
        toast(R.string.copied)
    }

    fun exportSvg() {
        if (items.isEmpty()) { toast(R.string.document_empty); return }
        busy = true
        Thread {
            val margin = 24f
            val docW = 1200f
            var totalH = margin
            for (it in items) {
                if (it.isText) totalH += it.text.split("\n").size * 36f + 18f
                else if (it.bitmap != null)
                    totalH += it.bitmap.height * ((docW - margin * 2) / it.bitmap.width).coerceAtMost(1f) + 20f
                else totalH += 60f
            }
            val docH = totalH + margin
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(docW.toInt())
                .append("\" height=\"").append(docH.toInt())
                .append("\" viewBox=\"0 0 ").append(docW.toInt()).append(' ')
                .append(docH.toInt()).append("\">\n")
            sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
            var y = margin
            for (it in items) {
                if (it.isText) {
                    for (line in it.text.split("\n")) {
                        y += 28f
                        sb.append("<text x=\"").append(margin).append("\" y=\"").append(y)
                            .append("\" font-family=\"serif\" font-size=\"26\" fill=\"#111111\">")
                            .append(escapeXml(line)).append("</text>\n")
                        y += 8f
                    }
                    y += 10f
                } else {
                    val b = it.bitmap
                    if (b != null && b.width > 0 && b.height > 0) {
                        val sc = ((docW - margin * 2) / b.width).coerceAtMost(1f)
                        val w = b.width * sc
                        val h = b.height * sc
                        val bos = java.io.ByteArrayOutputStream()
                        b.compress(Bitmap.CompressFormat.PNG, 100, bos)
                        val b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                        sb.append("<image href=\"data:image/png;base64,").append(b64)
                            .append("\" x=\"").append(margin).append("\" y=\"").append(y)
                            .append("\" width=\"").append(w).append("\" height=\"").append(h)
                            .append("\"/>\n")
                        y += h + 20f
                    } else {
                        y += 28f
                        sb.append("<text x=\"").append(margin).append("\" y=\"").append(y)
                            .append("\" font-family=\"serif\" font-size=\"22\" fill=\"#999999\">")
                            .append(escapeXml(it.text)).append("</text>\n")
                        y += 32f
                    }
                }
            }
            sb.append("</svg>\n")
            val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, "document_${System.currentTimeMillis()}.svg")
            f.writeText(sb.toString(), Charsets.UTF_8)
            busy = false
            share(f, "image/svg+xml", R.string.document_export)
        }.start()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---------- 顶栏 ----------
        Surface(shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(stringResource(R.string.document_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { render() }) { Text(stringResource(R.string.document_render)) }
                TextButton(onClick = { showExport = true }) { Text(stringResource(R.string.document_export)) }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) }) { Text(stringResource(R.string.import_docx)) }
                TextButton(onClick = { copyAll() }) { Text(stringResource(R.string.document_copy)) }
            }
        }

        if (busy) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp).padding(end = 8.dp))
                Text(stringResource(R.string.document_rendering),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---------- 输入 ----------
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small)
                .padding(10.dp)
        ) {
            if (source.isEmpty()) {
                Text(stringResource(R.string.document_input_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = source,
                onValueChange = { source = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxSize()
            )
        }

        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        // ---------- 渲染结果 ----------
        LazyColumn(Modifier.fillMaxSize()) {
            items(items) { item ->
                if (item.isText) {
                    Text(item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val b = item.bitmap
                        if (b != null) {
                            Image(b.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        } else {
                            Text(stringResource(R.string.document_render_failed),
                                color = MaterialTheme.colorScheme.error)
                        }
                        Text(stringResource(R.string.document_copy),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { copyOne(item.text) })
                    }
                }
            }
        }
    }

    if (showExport) {
        val opts = listOf(
            R.string.document_export_pdf to { export({ DocExporter.toPdf(ctx, items) }, ".pdf", "application/pdf", R.string.document_export) },
            R.string.document_export_docx to {
                val block = if (docBlocks.isNotEmpty()) {
                    { DocExporter.toDocxFromBlocks(ctx, docBlocks, docImages) }
                } else {
                    { DocExporter.toDocx(ctx, items) }
                }
                export(block, ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", R.string.document_export)
            },
            R.string.document_export_xlsx to { export({ DocExporter.toXlsx(ctx, items) }, ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", R.string.document_export) },
            R.string.document_export_md to { export({ DocExporter.toMarkdown(ctx, items) }, ".md", "text/markdown", R.string.document_export) },
            R.string.document_export_svg to { showExport = false; exportSvg() },
            R.string.open_pdf to { showExport = false; openPdf() }
        )
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text(stringResource(R.string.document_export)) },
            text = {
                Column {
                    opts.forEach { (res, action) ->
                        Text(stringResource(res),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExport = false; action() }
                                .padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExport = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ---------------- 工具 ----------------



private fun escapeXml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
