package com.formulastudio.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as C
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.formulastudio.app.ui.components.RadioRow
import com.formulastudio.app.ui.components.SectionHeader
import com.formulastudio.app.ui.components.SwitchRow
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Prefs
import kotlin.math.roundToInt

/**
 * 外观设置中心（阶段 4.2：已迁移到 Compose）。
 *
 * 与旧的 View 版本功能一致，但：
 * - 用 Compose 重写，后续可直接复用设计系统组件
 * - 与 QuickTexTheme 共用同一套 [Prefs]，改设置即时生效
 */
class AppearanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        setContent {
            QuickTexTheme {
                AppearanceScreen(onRestart = { recreate() })
            }
        }
    }
}

@Composable
private fun AppearanceScreen(onRestart: () -> Unit) {
    val ctx = LocalContext.current
    // 每次改设置就 +1，触发整个页面重组（等价于旧版的 rebuild()）
    var version by remember { mutableStateOf(0) }
    val bump = { version++ }

    var showColorStyle by remember { mutableStateOf(false) }
    var showCustomColor by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // 1. 界面风格
        SectionHeader(string(R.string.section_theme_style))
        RadioRow(string(R.string.style_material), null,
            Prefs.themeStyle(ctx) == Prefs.STYLE_MATERIAL) {
            Prefs.setThemeStyle(ctx, Prefs.STYLE_MATERIAL); onRestart()
        }
        RadioRow(string(R.string.style_miuix), null,
            Prefs.themeStyle(ctx) == Prefs.STYLE_MIUIX) {
            Prefs.setThemeStyle(ctx, Prefs.STYLE_MIUIX); onRestart()
        }

        // 2. 导航栏
        SectionHeader(string(R.string.section_nav_bar))
        SwitchRow(string(R.string.nav_floating), string(R.string.nav_floating_desc),
            Prefs.floatingBar(ctx)) { Prefs.setFloatingBar(ctx, it); bump() }
        SwitchRow(string(R.string.nav_liquid), string(R.string.nav_liquid_desc),
            Prefs.liquidGlass(ctx)) { Prefs.setLiquidGlass(ctx, it); bump() }

        // 3. 配色
        SectionHeader(string(R.string.section_color))
        ClickRow(icon = { Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary) },
            title = string(R.string.color_wallpaper),
            status = string(R.string.color_wallpaper_status)) { showColorStyle = true }

        SwitchRow(string(R.string.color_dynamic), string(R.string.color_dynamic_desc),
            Prefs.dynamicColor(ctx)) { Prefs.setDynamicColor(ctx, it); applyStyle(ctx); bump() }

        Text(string(R.string.color_picker),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp))
        if (Prefs.dynamicColor(ctx)) {
            Text(string(R.string.color_picker_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp))
        }

        ColorSwatches(
            dynamic = Prefs.dynamicColor(ctx),
            accent = Prefs.accentColor(ctx),
            onPick = { c ->
                Prefs.setAccentColor(ctx, adjustContrast(c, Prefs.contrast(ctx))); bump()
            },
            onAdd = { showCustomColor = true }
        )

        // 配色风格下拉
        ClickRow(title = string(R.string.color_style), status = colorStyleLabel(ctx)) {
            showColorStyle = true
        }

        Text(string(R.string.color_contrast),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Slider(
            value = Prefs.contrast(ctx),
            onValueChange = { Prefs.setContrast(ctx, (it * 20).roundToInt() / 20f) },
            valueRange = 0.8f..1.4f,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        SwitchRow(string(R.string.color_content), null, Prefs.contentColor(ctx)) {
            Prefs.setContentColor(ctx, it); bump()
        }

        // 4. 显示模式
        SectionHeader(string(R.string.section_display))
        RadioRow(string(R.string.display_system), null,
            Prefs.displayMode(ctx) == Prefs.DISPLAY_SYSTEM) {
            Prefs.setDisplayMode(ctx, Prefs.DISPLAY_SYSTEM); Prefs.applyDisplayMode(ctx); onRestart()
        }
        RadioRow(string(R.string.display_light), null,
            Prefs.displayMode(ctx) == Prefs.DISPLAY_LIGHT) {
            Prefs.setDisplayMode(ctx, Prefs.DISPLAY_LIGHT); Prefs.applyDisplayMode(ctx); onRestart()
        }
        RadioRow(string(R.string.display_dark), null,
            Prefs.displayMode(ctx) == Prefs.DISPLAY_DARK) {
            Prefs.setDisplayMode(ctx, Prefs.DISPLAY_DARK); Prefs.applyDisplayMode(ctx); onRestart()
        }
        RadioRow(string(R.string.display_amoled), string(R.string.display_amoled_desc),
            Prefs.displayMode(ctx) == Prefs.DISPLAY_AMOLED) {
            Prefs.setDisplayMode(ctx, Prefs.DISPLAY_AMOLED); Prefs.applyDisplayMode(ctx); onRestart()
        }

        // 5. 启动画面与图标
        SectionHeader(string(R.string.section_splash))
        SwitchRow(string(R.string.splash_theme), null, Prefs.splashTheme(ctx)) {
            Prefs.setSplashTheme(ctx, it); bump()
        }
        SwitchRow(string(R.string.icon_theme), null, Prefs.iconTheme(ctx)) {
            Prefs.setIconTheme(ctx, it); bump()
        }
    }

    // 配色风格选择
    if (showColorStyle) {
        AlertDialog(
            onDismissRequest = { showColorStyle = false },
            title = { Text(string(R.string.color_wallpaper)) },
            text = {
                Column {
                    val opts = listOf(
                        R.string.color_style_dynamic, R.string.color_style_classic,
                        R.string.color_style_fresh, R.string.color_style_deep
                    )
                    opts.forEachIndexed { i, r ->
                        Row(Modifier
                            .fillMaxWidth()
                            .clickable {
                                Prefs.setColorStyle(ctx, i)
                                applyStyle(ctx)
                                showColorStyle = false
                                bump()
                            }
                            .padding(vertical = 12.dp)) {
                            Text(string(r))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorStyle = false }) { Text(string(R.string.cancel)) } }
        )
    }

    // 自定义颜色
    if (showCustomColor) {
        var hue by remember { mutableStateOf(210f) }
        AlertDialog(
            onDismissRequest = { showCustomColor = false },
            title = { Text(string(R.string.color_add)) },
            text = {
                Column {
                    Box(Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(C(hsv(hue))))
                    Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setAccentColor(ctx, hsv(hue))
                    showCustomColor = false
                    bump()
                }) { Text(string(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showCustomColor = false }) { Text(string(R.string.cancel)) } }
        )
    }
}

// ---------------- 小组件 ----------------

@Composable
private fun ClickRow(
    title: String,
    status: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

private val swatches = listOf(
    0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
    0xFF3F51B5, 0xFF1565C0, 0xFF03A9F4, 0xFF00BCD4,
    0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
    0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548
).map { it.toInt() }

@Composable
private fun ColorSwatches(
    dynamic: Boolean,
    accent: Int,
    onPick: (Int) -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        for (i in 0 until 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (j in 0 until 4) {
                    val c = swatches[i * 4 + j]
                    val selected = !dynamic && accent == c
                    Box(
                        Modifier
                            .padding(4.dp)
                            .size(46.dp)
                            .background(C(c), CircleShape)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .clickable { if (!dynamic) onPick(c) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null,
                            tint = if (isLight(c)) C.DarkGray else C.White)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val defaultColor = 0xFF3C4043.toInt()
            Box(
                Modifier
                    .padding(4.dp)
                    .size(46.dp)
                    .background(C(defaultColor), CircleShape)
                    .border(
                        width = if (!dynamic && accent == defaultColor) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clickable { if (!dynamic) onPick(defaultColor) },
                contentAlignment = Alignment.Center
            ) {
                if (!dynamic && accent == defaultColor)
                    Icon(Icons.Filled.Check, null,
                        tint = if (isLight(defaultColor)) C.DarkGray else C.White)
            }
            Text(string(R.string.color_default),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 12.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, null) }
            Text(string(R.string.color_add),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

// ---------------- 工具 ----------------

@Composable
private fun string(res: Int): String = androidx.compose.ui.res.stringResource(res)

private fun isLight(color: Int): Boolean {
    val r = Color.red(color) / 255.0
    val g = Color.green(color) / 255.0
    val b = Color.blue(color) / 255.0
    return 0.299 * r + 0.587 * g + 0.114 * b > 0.6
}

private fun hsv(hue: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))

private fun adjustContrast(color: Int, contrast: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] = (hsv[2] * contrast).coerceIn(0f, 1f)
    return Color.HSVToColor(hsv)
}

private fun applyStyle(ctx: android.content.Context) {
    if (Prefs.dynamicColor(ctx)) return
    val base = when (Prefs.colorStyle(ctx)) {
        Prefs.COLOR_CLASSIC -> 0xFF1565C0.toInt()
        Prefs.COLOR_FRESH -> 0xFF00897B.toInt()
        Prefs.COLOR_DEEP -> 0xFF6A1B9A.toInt()
        else -> 0xFF3482FF.toInt()
    }
    Prefs.setAccentColor(ctx, adjustContrast(base, Prefs.contrast(ctx)))
}

private fun colorStyleLabel(ctx: android.content.Context): String = ctx.getString(
    when (Prefs.colorStyle(ctx)) {
        Prefs.COLOR_CLASSIC -> R.string.color_style_classic
        Prefs.COLOR_FRESH -> R.string.color_style_fresh
        Prefs.COLOR_DEEP -> R.string.color_style_deep
        else -> R.string.color_style_dynamic
    }
)
