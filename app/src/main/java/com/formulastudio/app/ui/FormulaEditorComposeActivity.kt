package com.formulastudio.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.formulastudio.app.R
import com.formulastudio.app.ui.compose.FormulaEditor
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Prefs

/**
 * 阶段 4.4 演示页：在 Compose 里嵌入自绘公式编辑器。
 *
 * 这是 Compose 化的「互操作」步骤——验证 Compose 页面可以直接复用
 * 现有编辑器（无需 DrawScope 重写即可获得全部功能）。
 */
class FormulaEditorComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        setContent {
            QuickTexTheme {
                EditorScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun EditorScreen(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(stringResource(R.string.title_compose_editor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        FormulaEditor(Modifier.fillMaxSize())
    }
}
