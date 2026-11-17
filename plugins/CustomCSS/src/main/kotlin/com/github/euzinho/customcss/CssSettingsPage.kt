package com.github.euzinho.customcss

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.views.Button
import com.aliucord.views.DangerButton
import com.discord.views.CheckedSetting

/** Editor de CSS com destaque de sintaxe, validação e pré-visualização ao vivo. */
class CssSettingsPage(private val settings: SettingsAPI) : SettingsPage() {
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var previewHost: FrameLayout
    private lateinit var enabledSwitch: CheckedSetting

    private val refreshTask = Runnable { refresh() }
    private var highlighting = false

    private class Hl(color: Int) : ForegroundColorSpan(color)

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        // a tela do editor nunca é estilizada pelo próprio CSS (evita se "trancar para fora")
        view.tag = CssEngine.IGNORE_TAG
        setActionBarTitle("Custom CSS")

        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(28))
        }
        linearLayout.addView(col)

        // --- ligar / desligar
        enabledSwitch = Utils.createCheckedSetting(
            ctx,
            CheckedSetting.ViewType.SWITCH,
            "Ativar Custom CSS",
            "Aplica as regras ao app. Desligue para voltar ao visual original.",
        )
        enabledSwitch.isChecked = settings.getBool(CssKeys.ENABLED, true)
        enabledSwitch.setOnCheckedListener { checked ->
            settings.setBool(CssKeys.ENABLED, checked)
            CssEngine.setEnabled(checked)
        }
        col.addView(enabledSwitch)

        // --- presets
        col.addView(label(ctx, "Modelos", 16))
        val presetRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, css) in CssPresets.PRESETS) {
            presetRow.addView(
                Button(ctx).apply {
                    text = name
                    isAllCaps = false
                    setOnClickListener { editor.setText(css) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { rightMargin = dp(6) }
                },
            )
        }
        col.addView(HorizontalScrollView(ctx).apply { addView(presetRow) })

        // --- editor
        col.addView(label(ctx, "Editor", 16))
        editor = EditText(ctx).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(0xFFDBDEE1.toInt())
            setHintTextColor(0xFF6D6F78.toInt())
            hint = "/* seu CSS aqui */"
            setBackgroundColor(0xFF1E1F22.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            isHorizontalScrollBarEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setHorizontallyScrolling(false)
        }
        editor.setText(settings.getString(CssKeys.CSS, CssPresets.DEFAULT_CSS))
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (highlighting) return
                highlight(s)
                editor.removeCallbacks(refreshTask)
                editor.postDelayed(refreshTask, 250)
            }
        })
        col.addView(editor)

        // --- status / erros
        status = TextView(ctx).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(6), 0, dp(6))
        }
        col.addView(status)

        // --- ações
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            Button(ctx).apply {
                text = "Aplicar"
                isAllCaps = false
                setOnClickListener { applyNow() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(6)
                }
            },
        )
        actions.addView(
            Button(ctx).apply {
                text = "Copiar"
                isAllCaps = false
                setOnClickListener {
                    clipboard(ctx).setPrimaryClip(ClipData.newPlainText("css", editor.text.toString()))
                    Utils.showToast("CSS copiado")
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(6)
                }
            },
        )
        actions.addView(
            Button(ctx).apply {
                text = "Colar"
                isAllCaps = false
                setOnClickListener {
                    val clip = clipboard(ctx).primaryClip
                    val txt = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx) else null
                    if (txt.isNullOrEmpty()) {
                        Utils.showToast("Área de transferência vazia")
                    } else {
                        editor.setText(txt.toString())
                    }
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        col.addView(actions)

        // --- pré-visualização
        col.addView(label(ctx, "Pré-visualização", 16))
        col.addView(
            TextView(ctx).apply {
                text = "Usa as mesmas regras. As classes .message-* funcionam aqui e no app; " +
                    "para outros elementos do app use /cssdump."
                textSize = 12f
                alpha = 0.7f
            },
        )
        previewHost = FrameLayout(ctx).apply { setPadding(0, dp(8), 0, dp(8)) }
        col.addView(previewHost)

        col.addView(
            DangerButton(ctx).apply {
                text = "Restaurar padrão"
                isAllCaps = false
                setOnClickListener {
                    editor.setText(CssPresets.DEFAULT_CSS)
                    Utils.showToast("Editor restaurado — toque em Aplicar")
                }
            },
        )

        highlight(editor.text)
        refresh()
    }

    override fun onDestroyView() {
        if (::editor.isInitialized) editor.removeCallbacks(refreshTask)
        super.onDestroyView()
    }

    private fun applyNow() {
        val css = editor.text.toString()
        settings.setString(CssKeys.CSS, css)
        settings.setBool(CssKeys.ENABLED, enabledSwitch.isChecked)
        CssEngine.configure(css, enabledSwitch.isChecked)
        activity?.let { CssEngine.attach(it) }
        val result = CssParser.parse(css)
        Utils.showToast(
            if (result.hasErrors) "Aplicado, com erros em algumas regras" else "CSS aplicado (${result.rules.size} regras)",
        )
    }

    private fun refresh() {
        if (!::editor.isInitialized || context == null) return
        val ctx = requireContext()
        val result = CssParser.parse(editor.text.toString())

        if (result.errors.isEmpty()) {
            status.setTextColor(0xFF57F287.toInt())
            status.text = "✓ ${result.rules.size} regra(s), sem problemas"
        } else {
            val hard = result.errors.any { !it.warning }
            status.setTextColor(if (hard) 0xFFED4245.toInt() else 0xFFFEE75C.toInt())
            val sb = StringBuilder("${result.rules.size} regra(s) • ${result.errors.size} aviso(s)/erro(s)\n")
            for (e in result.errors.take(6)) {
                sb.append(if (e.warning) "⚠ " else "✗ ").append("linha ").append(e.line).append(": ").append(e.message).append('\n')
            }
            if (result.errors.size > 6) sb.append("… e mais ${result.errors.size - 6}")
            status.text = sb.toString().trimEnd()
        }

        previewHost.removeAllViews()
        val preview = CssPreview.build(ctx)
        previewHost.addView(preview)
        CssEngine.applyPreview(preview, result.rules)
    }

    private fun highlight(text: Editable) {
        highlighting = true
        try {
            for (old in text.getSpans(0, text.length, Hl::class.java)) text.removeSpan(old)
            val s = text.toString()
            fun mark(re: Regex, color: Int) {
                for (m in re.findAll(s)) {
                    if (m.range.isEmpty()) continue
                    text.setSpan(Hl(color), m.range.first, m.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            // a ordem importa: o que vem depois sobrescreve
            mark(RE_VALUE, 0xFF98C379.toInt())
            mark(RE_SELECTOR, 0xFFE5C07B.toInt())
            mark(RE_PROPERTY, 0xFF61AFEF.toInt())
            mark(RE_HEX, 0xFFD19A66.toInt())
            mark(RE_COMMENT, 0xFF7F848E.toInt())
        } finally {
            highlighting = false
        }
    }

    private fun label(ctx: Context, text: String, sp: Int): TextView = TextView(ctx).apply {
        this.text = text
        textSize = sp.toFloat()
        setTypeface(typeface, Typeface.BOLD)
        val d = ctx.resources.displayMetrics.density
        setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt())
    }

    private fun clipboard(ctx: Context) = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private companion object {
        val RE_VALUE = Regex("(?<=:)[^;{}]*")
        val RE_SELECTOR = Regex("[^{};]+(?=\\{)")
        val RE_PROPERTY = Regex("[a-zA-Z-]+(?=\\s*:)")
        val RE_HEX = Regex("#[0-9a-fA-F]{3,8}\\b")
        val RE_COMMENT = Regex("/\\*[\\s\\S]*?(\\*/|$)")
    }
}
