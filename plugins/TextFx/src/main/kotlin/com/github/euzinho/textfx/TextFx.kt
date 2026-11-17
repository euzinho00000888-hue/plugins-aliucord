package com.github.euzinho.textfx

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class TextFx : Plugin() {
    private fun fx(name: String, description: String, transform: (String) -> String) {
        commands.registerCommand(
            name,
            description,
            listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "text", "Texto a transformar")),
        ) { ctx ->
            val input = ctx.getStringOrDefault("text", "")
            if (input.isBlank()) {
                CommandsAPI.CommandResult("Informe um texto.", null, false)
            } else {
                val out = transform(input)
                if (out.length > 2000) {
                    CommandsAPI.CommandResult("Resultado maior que 2000 caracteres (${out.length}).", null, false)
                } else {
                    CommandsAPI.CommandResult(out, null, true)
                }
            }
        }
    }

    override fun start(context: Context) {
        fx("mock", "dEsEnHa O tExTo AlTeRnAnDo MaIuScUlAs") { s ->
            var i = 0
            buildString {
                for (ch in s) {
                    if (ch.isLetter()) {
                        append(if (i % 2 == 0) ch.lowercaseChar() else ch.uppercaseChar())
                        i++
                    } else {
                        append(ch)
                    }
                }
            }
        }

        fx("uwu", "Transforma o texto em uwu") { s ->
            s.replace(Regex("[rl]"), "w")
                .replace(Regex("[RL]"), "W")
                .replace(Regex("n([aeiouAEIOU])"), "ny$1")
                .replace(Regex("N([aeiouAEIOU])"), "Ny$1") + " uwu"
        }

        fx("clap", "Coloca 👏 entre as palavras") { s ->
            s.trim().split(Regex("\\s+")).joinToString(" 👏 ") + " 👏"
        }

        fx("reverse", "Inverte o texto") { s -> StringBuilder(s).reverse().toString() }

        fx("vapor", "ｖａｐｏｒｗａｖｅ (largura total)") { s ->
            buildString {
                for (ch in s) {
                    when {
                        ch == ' ' -> append('　')
                        ch.code in 0x21..0x7E -> append((ch.code + 0xFEE0).toChar())
                        else -> append(ch)
                    }
                }
            }
        }

        fx("bigtext", "Escreve com letras de emoji") { s ->
            val digits = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
            buildString {
                for (ch in s.lowercase()) {
                    when {
                        ch in 'a'..'z' -> append(":regional_indicator_").append(ch).append(": ")
                        ch in '0'..'9' -> append(':').append(digits[ch - '0']).append(": ")
                        ch == ' ' -> append("   ")
                        else -> append(ch)
                    }
                }
            }.trim()
        }

        fx("spoilerall", "Esconde cada palavra em um spoiler") { s ->
            s.trim().split(Regex("\\s+")).joinToString(" ") { "||$it||" }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
