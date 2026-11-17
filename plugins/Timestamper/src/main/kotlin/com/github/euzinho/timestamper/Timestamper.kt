package com.github.euzinho.timestamper

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class Timestamper : Plugin() {
    private val styles = setOf("t", "T", "d", "D", "f", "F", "R")

    /** Aceita: agora | +2h | -30m | +3d | +1w | 18:30 | 25/12/2026 | 25/12/2026 18:30 | 2026-12-25 18:30 */
    private fun parse(input: String): Calendar? {
        val s = input.trim()
        val cal = Calendar.getInstance()
        if (s.isEmpty() || s.equals("agora", true) || s.equals("now", true)) return cal

        val rel = Regex("^([+-])(\\d+)\\s*([smhdw])$", RegexOption.IGNORE_CASE).matchEntire(s)
        if (rel != null) {
            val sign = if (rel.groupValues[1] == "-") -1 else 1
            val n = rel.groupValues[2].toIntOrNull() ?: return null
            val field = when (rel.groupValues[3].lowercase()) {
                "s" -> Calendar.SECOND
                "m" -> Calendar.MINUTE
                "h" -> Calendar.HOUR_OF_DAY
                "d" -> Calendar.DAY_OF_YEAR
                else -> Calendar.WEEK_OF_YEAR
            }
            cal.add(field, sign * n)
            return cal
        }

        val patterns = listOf("dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd/MM/yyyy", "yyyy-MM-dd", "HH:mm")
        for (p in patterns) {
            val f = SimpleDateFormat(p, Locale.US)
            f.isLenient = false
            val parsedDate = try {
                f.parse(s)
            } catch (_: Exception) {
                null
            }
            val date = parsedDate ?: continue
            val parsed = Calendar.getInstance().apply { time = date }
            if (p == "HH:mm") {
                cal.set(Calendar.HOUR_OF_DAY, parsed.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, parsed.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, 0)
                return cal
            }
            return parsed
        }
        return null
    }

    override fun start(context: Context) {
        commands.registerCommand(
            "timestamp",
            "Gera um horário que aparece no fuso de quem lê (<t:...>)",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "time",
                    "agora, +2h, -30m, +3d, 18:30, 25/12/2026 18:30, 2026-12-25 18:30",
                ),
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "style",
                    "R=relativo (padrão), t, T, d, D, f, F",
                ),
            ),
        ) { ctx ->
            val cal = parse(ctx.getStringOrDefault("time", ""))
            val style = ctx.getStringOrDefault("style", "R").trim().ifEmpty { "R" }
            when {
                cal == null -> CommandsAPI.CommandResult(
                    "Não entendi o horário. Exemplos: agora, +2h, -30m, 18:30, 25/12/2026 18:30",
                    null,
                    false,
                )
                style !in styles -> CommandsAPI.CommandResult("Estilo inválido. Use: t, T, d, D, f, F ou R.", null, false)
                else -> CommandsAPI.CommandResult("<t:${cal.timeInMillis / 1000}:$style>", null, true)
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
