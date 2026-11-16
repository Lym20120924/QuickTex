package com.formulastudio.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formulastudio.app.ui.theme.QuickTexTheme
import com.formulastudio.app.util.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏夹 / 历史（阶段 4.3：已迁移到 Compose）。
 *
 * 功能与旧版一致：Tab 切换、搜索、排序、单条删除、清空、点击「载入」回传公式。
 */
class LibraryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        com.formulastudio.app.util.Prefs.applyDisplayMode(this)
        com.formulastudio.app.util.Prefs.applyLanguage(this)
        super.onCreate(savedInstanceState)
        setContent {
            QuickTexTheme {
                LibraryScreen(onClose = { finish() },
                    onLoad = {
                        setResult(Activity.RESULT_OK, Intent().putExtra("latex", it))
                        finish()
                    })
            }
        }
    }
}

private enum class LibTab { FAVORITES, HISTORY }
private enum class LibSort { TIME_DESC, TIME_ASC, NAME }

@Composable
private fun LibraryScreen(onClose: () -> Unit, onLoad: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(LibTab.FAVORITES) }
    var sort by remember { mutableStateOf(LibSort.TIME_DESC) }
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    var showClear by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val bump = { version++ }

    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    val all = remember(tab, version) {
        if (tab == LibTab.FAVORITES) Store.getFavorites(ctx) else Store.getHistory(ctx)
    }
    val entries = remember(all, query, sort) {
        var list = all
        if (query.isNotEmpty()) list = list.filter { it.latex.contains(query, ignoreCase = true) }
        when (sort) {
            LibSort.TIME_DESC -> list.sortedByDescending { it.time }
            LibSort.TIME_ASC -> list.sortedBy { it.time }
            LibSort.NAME -> list.sortedBy { it.latex.lowercase(Locale.getDefault()) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showClear = true }) {
                Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.primary)
            }
        }

        // ---------- Tab 行 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabChip(stringResource(R.string.tab_favorites), tab == LibTab.FAVORITES) { tab = LibTab.FAVORITES }
            TabChip(stringResource(R.string.tab_history), tab == LibTab.HISTORY) { tab = LibTab.HISTORY }
        }

        // ---------- 搜索 + 排序 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.sort))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_time_desc)) },
                        onClick = { sort = LibSort.TIME_DESC; sortMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_time_asc)) },
                        onClick = { sort = LibSort.TIME_ASC; sortMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_name)) },
                        onClick = { sort = LibSort.NAME; sortMenu = false })
                }
            }
        }

        // ---------- 列表 ----------
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        query.isNotEmpty() -> stringResource(R.string.no_match)
                        tab == LibTab.FAVORITES -> stringResource(R.string.empty_favorites)
                        else -> stringResource(R.string.empty_history)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.latex + it.time }) { e ->
                    EntryCard(
                        latex = e.latex,
                        time = fmt.format(Date(e.time)),
                        onLoad = { onLoad(e.latex) },
                        onDelete = {
                            if (tab == LibTab.FAVORITES) Store.removeFavorite(ctx, e.latex)
                            else Store.removeHistory(ctx, e.latex)
                            bump()
                        }
                    )
                }
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            text = {
                Text(
                    if (tab == LibTab.FAVORITES) stringResource(R.string.clear_favorites_confirm)
                    else stringResource(R.string.clear_history_confirm)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tab == LibTab.FAVORITES) Store.clearFavorites(ctx) else Store.clearHistory(ctx)
                    showClear = false; bump()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EntryCard(latex: String, time: String, onLoad: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(latex, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface)
            Text(time, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.load), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onLoad))
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onDelete))
            }
        }
        Box(Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
