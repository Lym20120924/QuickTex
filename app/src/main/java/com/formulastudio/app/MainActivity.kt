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
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.formulastudio.app.databinding.ActivityMainBinding
import com.formulastudio.app.editor.SymbolAction
import com.formulastudio.app.export.PngSaver
import com.formulastudio.app.util.Store
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 正在回填公式框（防止循环触发） */
    private var updatingFormulaBox = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private var formulaDebounce: Runnable? = null

    private val libraryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val latex = result.data?.getStringExtra("latex") ?: return@registerForActivityResult
                applyExternalLatex(latex)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashHandler()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

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
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("QuickTex 遇到问题")
                        .setMessage(msg)
                        .setPositiveButton("知道了", null)
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
        }
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
        binding.btnExport.setOnClickListener { exportPng() }
        binding.btnAbout.setOnClickListener { showAbout() }
    }

    private fun showAbout() {
        android.app.AlertDialog.Builder(this)
            .setTitle("QuickTex")
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
            val bmp = binding.formulaEditor.renderToBitmap(3f)
            if (bmp != null) {
                val f = PngSaver.save(this, bmp)
                if (f != null) {
                    val uri = FileProvider.getUriForFile(this, "com.formulastudio.app.fileprovider", f)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                    return@postDelayed
                }
            }
            toast(R.string.export_failed)
        }, 150)
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}
