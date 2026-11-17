package com.github.euzinho.snippets

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType
import org.json.JSONObject

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class Snippets : Plugin() {
    private fun load(): JSONObject = try {
        JSONObject(settings.getString(KEY, "{}"))
    } catch (_: Exception) {
        JSONObject()
    }

    private fun save(obj: JSONObject) = settings.setString(KEY, obj.toString())

    private fun reply(text: String) = CommandsAPI.CommandResult(text, null, false)

    private fun clean(name: String) = name.trim().lowercase().replace(Regex("\\s+"), "-")

    override fun start(context: Context) {
        val nameOpt = Utils.createCommandOption(ApplicationCommandType.STRING, "name", "Nome do snippet")
        val textOpt = Utils.createCommandOption(ApplicationCommandType.STRING, "text", "Texto do snippet")

        commands.registerCommand("snipsave", "Salva um texto reutilizável", listOf(nameOpt, textOpt)) { ctx ->
            val name = clean(ctx.getStringOrDefault("name", ""))
            val text = ctx.getStringOrDefault("text", "")
            if (name.isEmpty() || text.isEmpty()) {
                reply("Use: /snipsave name:<nome> text:<texto>")
            } else {
                val all = load()
                val existed = all.has(name)
                all.put(name, text)
                save(all)
                reply(if (existed) "Snippet `$name` atualizado." else "Snippet `$name` salvo.")
            }
        }

        commands.registerCommand("snip", "Envia um snippet salvo", listOf(nameOpt)) { ctx ->
            val name = clean(ctx.getStringOrDefault("name", ""))
            val all = load()
            if (name.isEmpty() || !all.has(name)) {
                reply("Snippet `$name` não encontrado. Use /sniplist para ver os nomes.")
            } else {
                CommandsAPI.CommandResult(all.getString(name), null, true)
            }
        }

        commands.registerCommand("sniplist", "Lista seus snippets") {
            val all = load()
            if (all.length() == 0) {
                reply("Nenhum snippet salvo. Use /snipsave.")
            } else {
                val sb = StringBuilder("Seus snippets:\n")
                val keys = all.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val preview = all.optString(k).replace('\n', ' ')
                    sb.append("• `").append(k).append("` — ")
                        .append(if (preview.length > 60) preview.substring(0, 60) + "…" else preview)
                        .append('\n')
                }
                reply(sb.toString().trimEnd())
            }
        }

        commands.registerCommand("snipdel", "Apaga um snippet", listOf(nameOpt)) { ctx ->
            val name = clean(ctx.getStringOrDefault("name", ""))
            val all = load()
            if (!all.has(name)) {
                reply("Snippet `$name` não existe.")
            } else {
                all.remove(name)
                save(all)
                reply("Snippet `$name` apagado.")
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private companion object {
        const val KEY = "snippets"
    }
}
