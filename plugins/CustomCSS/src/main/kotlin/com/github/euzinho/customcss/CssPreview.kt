package com.github.euzinho.customcss

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Mini "tela de chat" montada com views nativas. As views recebem tags com classes
 * (".message", ".message-author"...) para que os mesmos seletores do app funcionem aqui.
 */
object CssPreview {
    fun build(ctx: Context): View {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            tag = "list"
            setBackgroundColor(0xFF313338.toInt())
            setPadding(0, 0, 0, dp(8))
        }

        // cabeçalho
        val header = LinearLayout(ctx).apply {
            tag = "header"
            setBackgroundColor(0xFF2B2D31.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        header.addView(
            TextView(ctx).apply {
                tag = "title"
                text = "# geral"
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, Typeface.BOLD)
                textSize = 16f
            },
        )
        list.addView(header)

        fun message(author: String, authorColor: Int, avatarColor: Int, body: String, time: String) {
            val row = LinearLayout(ctx).apply {
                tag = "message"
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(0xFF313338.toInt())
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            val avatar = ImageView(ctx).apply {
                tag = "avatar message-avatar"
                setBackgroundColor(avatarColor)
                setImageResource(android.R.drawable.sym_def_app_icon)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) }
            }
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val top = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(
                TextView(ctx).apply {
                    tag = "author message-author"
                    text = author
                    setTextColor(authorColor)
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = 15f
                },
            )
            top.addView(
                TextView(ctx).apply {
                    tag = "timestamp"
                    text = "  $time"
                    setTextColor(0xFF949BA4.toInt())
                    textSize = 11f
                },
            )
            col.addView(top)
            col.addView(
                TextView(ctx).apply {
                    tag = "text message-text"
                    text = body
                    setTextColor(0xFFDBDEE1.toInt())
                    textSize = 15f
                },
            )
            row.addView(avatar)
            row.addView(col)
            list.addView(row)
        }

        message("Aliu", 0xFFF47FFF.toInt(), 0xFF5865F2.toInt(), "Bom dia! Já testou o novo plugin?", "hoje às 09:41")
        message("Cord", 0xFF57F287.toInt(), 0xFFEB459E.toInt(), "Testei, ficou ótimo. Manda um print do tema.", "hoje às 09:42")

        // botão + campo de texto
        val bar = LinearLayout(ctx).apply {
            tag = "input"
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        bar.addView(
            TextView(ctx).apply {
                tag = "input-text"
                text = "Conversar em #geral"
                setTextColor(0xFF949BA4.toInt())
                setBackgroundColor(0xFF383A40.toInt())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(8)
                }
            },
        )
        bar.addView(
            Button(ctx).apply {
                tag = "button"
                text = "Enviar"
                isAllCaps = false
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF5865F2.toInt())
            },
        )
        list.addView(bar)
        return list
    }
}
