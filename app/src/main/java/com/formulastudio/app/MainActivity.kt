package com.formulastudio.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.formulastudio.app.databinding.ActivityMainBinding
import com.formulastudio.app.editor.SymbolAction
import com.formulastudio.app.export.PngSaver
import com.formulastudio.app.export.SvgExporter
import com.formulastudio.app.doc.LatexDocument
import com.formulastudio.app.util.Prefs
import com.formulastudio.app.util.Store
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 正在回填公式框（防止循环触发） */
    private var updatingFormulaBox = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private var formulaDebounce: Runnable? = null
    private var historyRunnable: Runnable? = null

    private val libraryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val latex = result.data?.getStringExtra("latex") ?: return@registerForActivityResult
                applyExternalLatex(latex)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyDisplayMode(this)
        setTheme(Prefs.themeRes(this))
        Prefs.applyLanguage(this)
        installCrashHandler()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()
        applyPrefs()

        setupEditor()
        setupFormulaBox()
        setupToolbar()
        setupKeyboard()

        // 恢复上次退出前的公式
        val saved = getPreferences(MODE_PRIVATE).getString("formula_latex", "") ?: ""
        if (saved.isNotBlank()) {
            binding.formulaEditor.loadLatex(saved)
        }
        updateFavoriteIcon(binding.formulaEditor.getLatex())
    }

    override fun onPause() {
        super.onPause()
        // 保存当前公式
        val latex = binding.formulaEditor.getLatex()
        getPreferences(MODE_PRIVATE).edit().putString("formula_latex", latex).apply()
    }

    override fun onResume() {
        super.onResume()
        // 导航栏外观：悬浮底栏 / 液态玻璃
        binding.symbolKeyboard.applyChrome(
            Prefs.floatingBar(this), Prefs.liquidGlass(this)
        )
    }

    /** 应用用户设置（字号 / 点阵 / 代码框显隐） */
    private fun applyPrefs() {
        binding.formulaEditor.applySettings(Prefs.fontScale(this), Prefs.showGrid(this))
        val showCode = Prefs.showCode(this)
        binding.formulaEdit.visibility = if (showCode) View.VISIBLE else View.GONE
        binding.hintOverlay.visibility = if (showCode) View.VISIBLE else View.GONE
        // 代码框的容器整体隐藏，避免留下空白
        (binding.formulaEdit.parent.parent as? View)?.visibility =
            if (showCode) View.VISIBLE else View.GONE
    }

    /** 让顶部栏避开状态栏、底部键盘避开导航栏 */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, ins ->
            val bars = ins.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            ins
        }
    }

    /** 崩溃时把堆栈写进应用私有目录，并在界面弹出错误信息，方便定位问题 */
    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val f = File(filesDir, "crash_log.txt")
                f.appendText(
                    java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()) + "\n" +
                        android.util.Log.getStackTraceString(throwable) + "\n----\n"
                )
                val msg = throwable.toString() + "\n\n" +
                    throwable.stackTrace.take(10).joinToString("\n") { "    at $it" }
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.crash_title)
                        .setMessage(msg)
                        .setPositiveButton(R.string.crash_ok, null)
                        .setCancelable(false)
                        .show()
                }
            } catch (_: Throwable) {
            }
        }
    }

    // ---------- 画布编辑器 ----------

    private fun setupEditor() {
        binding.formulaEditor.setOnFormulaChanged { latex ->
            if (!updatingFormulaBox) {
                updatingFormulaBox = true
                if (binding.formulaEdit.text.toString() != latex) {
                    binding.formulaEdit.setText(latex)
                    binding.formulaEdit.setSelection(latex.length)
                }
                updatingFormulaBox = false
            }
            updateFavoriteIcon(latex)
            scheduleHistory(latex)
        }
    }

    private var docPromptFor: String? = null

    /** 检测到整份 LaTeX 文档时的引导 */
    private fun promptDocument(text: String) {
        if (docPromptFor == text) return
        docPromptFor = text
        AlertDialog.Builder(this)
            .setTitle(R.string.document_detected_title)
            .setMessage(R.string.document_detected_msg)
            .setPositiveButton(R.string.document_open) { _, _ ->
                startActivity(
                    Intent(this, DocumentActivity::class.java).putExtra("latex", text)
                )
            }
            .setNegativeButton(R.string.document_first_only) { _, _ ->
                val first = LatexDocument.parse(text)
                    .firstOrNull { it.type == LatexDocument.Type.MATH }
                if (first != null) binding.formulaEditor.loadLatex(first.text)
            }
            .setNeutralButton(R.string.cancel, null)
            .setOnDismissListener { docPromptFor = null }
            .show()
    }

    /** 记录历史：停止输入 1.5s 后写入，去重由 Store 负责 */
    private fun scheduleHistory(latex: String) {
        historyRunnable?.let { uiHandler.removeCallbacks(it) }
        if (latex.isBlank()) return
        historyRunnable = Runnable {
            Store.pushHistory(this, latex)
        }.also { uiHandler.postDelayed(it, 1500) }
    }

    // ---------- 公式代码框 ----------

    private fun setupFormulaBox() {
        binding.formulaEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // 居中提示文字：有内容就隐藏
                binding.hintOverlay.visibility =
                    if (s.isNullOrEmpty()) View.VISIBLE else View.GONE
                if (updatingFormulaBox) return
                val text = s?.toString() ?: return
                formulaDebounce?.let { uiHandler.removeCallbacks(it) }
                formulaDebounce = Runnable {
                    // 粘贴了整份 LaTeX 文档 → 引导去「文档渲染」
                    if (text.contains("\\begin{document}")) {
                        promptDocument(text)
                        return@Runnable
                    }
                    if (text != binding.formulaEditor.getLatex()) {
                        binding.formulaEditor.loadLatex(text)
                    }
                }.also { formulaDebounce = it }
                uiHandler.postDelayed(formulaDebounce!!, 300)
            }
        })

        binding.btnCopy.setOnClickListener {
            val latex = binding.formulaEdit.text?.toString() ?: ""
            if (latex.isBlank()) {
                toast(R.string.copy_failed)
                return@setOnClickListener
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("latex", latex))
            toast(R.string.copied)
        }
    }

    // ---------- 工具栏 ----------

    private fun setupToolbar() {
        binding.btnFavorite.setOnClickListener {
            val latex = binding.formulaEditor.getLatex()
            if (latex.isBlank()) {
                toast(R.string.empty_formula)
                return@setOnClickListener
            }
            if (Store.isFavorite(this, latex)) {
                Store.removeFavorite(this, latex)
                toast(R.string.unfavorited)
            } else {
                Store.addFavorite(this, latex)
                toast(R.string.favorited)
            }
            updateFavoriteIcon(latex)
        }
        binding.btnHistory.setOnClickListener {
            libraryLauncher.launch(Intent(this, LibraryActivity::class.java))
        }
        binding.btnExport.setOnClickListener { showExportMenu() }
        binding.btnDoc.setOnClickListener {
            startActivity(Intent(this, DocumentActivity::class.java))
        }
        binding.btnSettings.setOnClickListener { showSettings() }
        binding.btnAbout.setOnClickListener { showAbout() }
    }

    /** 导出菜单：PNG / SVG / 复制图片 / 分享 LaTeX */
    private fun showExportMenu() {
        val items = arrayOf(
            getString(R.string.export_png),
            getString(R.string.export_svg),
            getString(R.string.copy_image),
            getString(R.string.share_latex)
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportPng()
                    1 -> exportSvg()
                    2 -> copyImage()
                    3 -> shareLatex()
                }
            }
            .show()
    }

    /** 把 LaTeX 代码作为文本分享出去 */
    private fun shareLatex() {
        val latex = binding.formulaEditor.getLatex()
        if (latex.isBlank()) {
            toast(R.string.empty_formula)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, latex)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_latex)))
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(getString(R.string.about_message))
            .setPositiveButton(R.string.copy_link) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("url", "https://github.com/37-7/QuickTex"))
                toast(R.string.copied)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun updateFavoriteIcon(latex: String) {
        val fav = latex.isNotBlank() && Store.isFavorite(this, latex)
        binding.btnFavorite.setImageResource(
            if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    // ---------- 符号键盘 ----------

    private fun setupKeyboard() {
        binding.symbolKeyboard.listener = { action ->
            if (Prefs.haptic(this)) {
                binding.symbolKeyboard.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY
                )
            }
            if (action == SymbolAction.ToggleIme) {
                showIme()
            } else {
                binding.formulaEditor.insertAction(action)
            }
        }
    }

    /** 调起系统输入法（微信输入法等其他输入法也兼容）：先聚焦画布再显式弹出 */
    private fun showIme() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.formulaEditor.requestFocus()
        imm.showSoftInput(binding.formulaEditor, 0)
    }

    // ---------- 其他 ----------

    private fun refreshAfterExternalChange(latex: String) {
        updatingFormulaBox = true
        binding.formulaEdit.setText(latex)
        binding.formulaEdit.setSelection(latex.length)
        updatingFormulaBox = false
    }

    private fun applyExternalLatex(latex: String) {
        binding.formulaEditor.loadLatex(latex)
        refreshAfterExternalChange(latex)
    }

    private fun exportPng() {
        if (binding.formulaEditor.getLatex().isBlank()) {
            toast(R.string.empty_formula)
            return
        }
        toast(R.string.exporting)
        uiHandler.postDelayed({
            val bmp = binding.formulaEditor.renderToBitmap(Prefs.pngScale(this).toFloat())
            if (bmp != null) {
                val f = PngSaver.save(this, bmp)
                if (f != null) {
                    shareFile(f, "image/png", R.string.export_ready)
                    return@postDelayed
                }
            }
            toast(R.string.export_failed)
        }, 150)
    }

    /** 导出 SVG 矢量图（复用编辑器排版，纯矢量） */
    private fun exportSvg() {
        if (binding.formulaEditor.getLatex().isBlank()) {
            toast(R.string.empty_formula)
            return
        }
        toast(R.string.exporting_svg)
        val svg = binding.formulaEditor.renderToSvg()
        val f = svg?.let { SvgExporter.save(this, it) }
        if (f != null) {
            shareFile(f, "image/svg+xml", R.string.export_svg_ready)
        } else {
            toast(R.string.export_svg_failed)
        }
    }

    /** 把公式渲染成 PNG 并复制到剪贴板 */
    private fun copyImage() {
        if (binding.formulaEditor.getLatex().isBlank()) {
            toast(R.string.empty_formula)
            return
        }
        toast(R.string.exporting)
        uiHandler.postDelayed({
            val bmp = binding.formulaEditor.renderToBitmap(Prefs.pngScale(this).toFloat())
            if (bmp == null) {
                toast(R.string.export_failed)
                return@postDelayed
            }
            val f = PngSaver.save(this, bmp)
            if (f == null) {
                toast(R.string.copy_image_failed)
                return@postDelayed
            }
            try {
                val uri = FileProvider.getUriForFile(this, "com.formulastudio.app.fileprovider", f)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newUri(contentResolver, "formula", uri))
                toast(R.string.copied_image)
            } catch (e: Exception) {
                toast(R.string.copy_image_failed)
            }
        }, 150)
    }

    private fun shareFile(f: File, mime: String, titleRes: Int) {
        val uri = FileProvider.getUriForFile(this, "com.formulastudio.app.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(titleRes)))
    }

    // ---------- 设置 ----------

    private var settingsDialog: AlertDialog? = null

    private fun showSettings() {
        settingsDialog?.dismiss()
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(4))
        }
        scroll.addView(content)

        // ---- 外观设置入口 ----
        content.addView(TextView(this).apply {
            text = getString(R.string.appearance_title)
            textSize = 15f
            setTextColor(Prefs.accentColor(this@MainActivity))
            setPadding(dp(12), dp(12), dp(6), dp(8))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AppearanceActivity::class.java))
            }
        })

        // ---- 公式字号 ----
        content.addView(settingTitle(getString(R.string.setting_font_scale)))
        val fontVal = TextView(this).apply {
            textSize = 15f
            width = dp(56)
            gravity = Gravity.CENTER
            text = "${(Prefs.fontScale(this@MainActivity) * 100).toInt()}%"
        }
        val fontRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fontRow.addView(settingButton("−") {
            Prefs.setFontScale(this, (Prefs.fontScale(this) - 0.1f).coerceAtLeast(0.6f))
            applyPrefs(); showSettings()
        })
        fontRow.addView(fontVal)
        fontRow.addView(settingButton("＋") {
            Prefs.setFontScale(this, (Prefs.fontScale(this) + 0.1f).coerceAtMost(1.8f))
            applyPrefs(); showSettings()
        })
        content.addView(fontRow)

        // ---- 开关项 ----
        content.addView(settingSwitch(getString(R.string.setting_show_code), Prefs.showCode(this)) {
            Prefs.setShowCode(this, it); applyPrefs(); showSettings()
        })
        content.addView(settingSwitch(getString(R.string.setting_grid), Prefs.showGrid(this)) {
            Prefs.setShowGrid(this, it); applyPrefs(); showSettings()
        })
        content.addView(settingSwitch(getString(R.string.setting_haptic), Prefs.haptic(this)) {
            Prefs.setHaptic(this, it); showSettings()
        })

        // ---- 深色模式 ----
        content.addView(choiceRow(
            getString(R.string.setting_dark_mode),
            listOf(
                getString(R.string.setting_dark_system) to Prefs.DARK_SYSTEM,
                getString(R.string.setting_dark_light) to Prefs.DARK_LIGHT,
                getString(R.string.setting_dark_dark) to Prefs.DARK_DARK
            ),
            Prefs.darkMode(this)
        ) {
            Prefs.setDarkMode(this, it)
            AppCompatDelegate.setDefaultNightMode(
                when (it) {
                    Prefs.DARK_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    Prefs.DARK_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        })

        // ---- 语言 ----
        content.addView(choiceRow(
            getString(R.string.setting_language),
            listOf(
                getString(R.string.setting_lang_system) to Prefs.LANG_SYSTEM,
                getString(R.string.setting_lang_zh) to Prefs.LANG_ZH,
                getString(R.string.setting_lang_en) to Prefs.LANG_EN
            ),
            Prefs.language(this)
        ) {
            Prefs.setLanguage(this, it)
            Prefs.applyLanguage(this)
        })

        // ---- 历史记录条数 ----
        content.addView(choiceRow(
            getString(R.string.setting_history_max),
            listOf("20" to 20, "60" to 60, "100" to 100, "200" to 200),
            Prefs.historyMax(this)
        ) {
            Prefs.setHistoryMax(this, it); showSettings()
        })

        // ---- 导出清晰度 ----
        content.addView(choiceRow(
            getString(R.string.setting_png_scale),
            listOf("1x" to 1, "2x" to 2, "3x" to 3, "4x" to 4),
            Prefs.pngScale(this)
        ) {
            Prefs.setPngScale(this, it); showSettings()
        })

        settingsDialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun settingTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.accent_blue))
        setPadding(0, dp(14), 0, dp(2))
    }

    private fun settingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(SwitchCompat(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
        return row
    }

    private fun settingButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 18f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(4), dp(14), dp(4))
        background = android.graphics.drawable.ColorDrawable(0x1A000000)
        setOnClickListener { onClick() }
    }

    /** 一行「选项」：点击弹出单选对话框 */
    private fun <T> choiceRow(
        title: String,
        options: List<Pair<String, T>>,
        current: T,
        onPick: (T) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = options.firstOrNull { it.second == current }?.first ?: ""
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.accent_blue))
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                        onPick(options[which].second)
                    }
                    .show()
            }
        })
        return row
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}
