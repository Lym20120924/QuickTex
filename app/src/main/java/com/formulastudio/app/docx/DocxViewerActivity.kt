package com.formulastudio.app.docx

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.formulastudio.app.R
import com.formulastudio.app.doc.BlockRenderer
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Prefs
import com.formulastudio.core.doc.DocxReader

/** docx 查看器：读取 Word 文档并渲染（文字 + 公式） */
class DocxViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        val uri = intent?.data ?: intent?.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
        setContent {
            QuickTexTheme {
                ViewerScreen(uri = uri, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_URI = "uri"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(uri: Uri?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var blocks by remember { mutableStateOf<List<DocxReader.Block>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(uri) {
        if (uri == null) { failed = true; return@LaunchedEffect }
        name = uri.lastPathSegment ?: ""
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val r = bytes?.let { DocxReader.parse(it) }
            if (r == null) failed = true else blocks = r.blocks
        } catch (e: Exception) {
            android.util.Log.e("DocxViewer", "读取失败 uri=$uri", e)
            failed = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.docx_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val b = blocks
            when {
                failed -> Text(stringResource(R.string.docx_parse_failed),
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error)
                b == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                b.isEmpty() -> Text(stringResource(R.string.docx_empty),
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(b) { blk ->
                        if (blk.isMath) {
                            val bg = MaterialTheme.colorScheme.surface.toArgb()
                            val ink = MaterialTheme.colorScheme.onSurface.toArgb()
                            val bmp = remember(blk.text, bg, ink) {
                                BlockRenderer.mathBitmap(ctx, blk.text, false, bg, ink)
                            }
                            if (bmp != null) {
                                Image(bmp.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp))
                            } else {
                                Text(blk.text, Modifier.padding(16.dp),
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(blk.text, Modifier.fillMaxWidth().padding(
                                horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
