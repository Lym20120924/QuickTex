package com.formulastudio.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.formulastudio.app.ui.theme.BluePrimary
import com.formulastudio.app.ui.theme.OrangePrimary
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.ui.theme.TealPrimary
import com.formulastudio.app.ui.theme.VioletPrimary

/**
 * 设计系统预览页（阶段 4.1）。
 *
 * 用 Compose 重写的第一页：集中展示颜色 / 字体 / 组件，
 * 作为后续把外观设置中心等页面迁到 Compose 的基准。
 */
class DesignSystemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickTexTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("设计系统", style = MaterialTheme.typography.headlineMedium)
                        Text("Compose 版基准页，旧页面仍在 View 体系下正常工作。",
                            style = MaterialTheme.typography.bodyMedium)

                        Text("颜色", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Swatch(BluePrimary, "主色")
                            Swatch(TealPrimary, "青")
                            Swatch(VioletPrimary, "紫")
                            Swatch(OrangePrimary, "橙")
                        }

                        Text("字体", style = MaterialTheme.typography.titleMedium)
                        Text("display 34", style = MaterialTheme.typography.displaySmall)
                        Text("headline 26", style = MaterialTheme.typography.headlineMedium)
                        Text("title 22", style = MaterialTheme.typography.titleLarge)
                        Text("body 16 · 公式 E = mc²", style = MaterialTheme.typography.bodyLarge)

                        Text("组件", style = MaterialTheme.typography.titleMedium)
                        var checked by remember { mutableStateOf(true) }
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("深色模式跟随系统", style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = checked, onCheckedChange = { checked = it })
                            }
                        }
                        Button(onClick = { }) { Text("主按钮") }
                    }
                }
            }
        }
    }

    @Composable
    private fun Swatch(color: Color, label: String) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(0.18f)
                    .background(color, RoundedCornerShape(12.dp))
            )
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
