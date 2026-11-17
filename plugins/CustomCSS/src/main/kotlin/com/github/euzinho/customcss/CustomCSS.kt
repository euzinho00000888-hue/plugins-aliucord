package com.github.euzinho.customcss

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class CustomCSS : Plugin() {
    private val handler = Handler(Looper.getMainLooper())
    private val clearBootFlag = Runnable { settings.setBool(CssKeys.BOOT_PENDING, false) }

    override fun start(context: Context) {
        // Proteção: se o app fechou logo depois da última inicialização, desliga o CSS
        // (um CSS ruim não pode te deixar preso num app que não abre).
        if (settings.getBool(CssKeys.BOOT_PENDING, false)) {
            settings.setBool(CssKeys.ENABLED, false)
            Utils.showToast("Custom CSS desativado: o app fechou logo após a última inicialização.")
        }
        settings.setBool(CssKeys.BOOT_PENDING, true)
        handler.postDelayed(clearBootFlag, 15_000)

        settingsTab = Plugin.SettingsTab(CssSettingsPage::class.java).withArgs(settings)

        (context.applicationContext as? Application)?.let { CssEngine.install(it) }
        CssEngine.configure(
            settings.getString(CssKeys.CSS, CssPresets.DEFAULT_CSS),
            settings.getBool(CssKeys.ENABLED, true),
        )
        (context as? Activity)?.let { CssEngine.attach(it) }

        commands.registerCommand("cssdump", "Lista tipos e ids de views da tela atual (para usar como seletores)") {
            CommandsAPI.CommandResult(CssEngine.dump(), null, false)
        }

        commands.registerCommand("csstoggle", "Liga ou desliga o Custom CSS") {
            val now = !settings.getBool(CssKeys.ENABLED, true)
            settings.setBool(CssKeys.ENABLED, now)
            CssEngine.setEnabled(now)
            CommandsAPI.CommandResult(if (now) "Custom CSS ligado." else "Custom CSS desligado.", null, false)
        }
    }

    override fun stop(context: Context) {
        handler.removeCallbacks(clearBootFlag)
        settings.setBool(CssKeys.BOOT_PENDING, false)
        CssEngine.shutdown()
        patcher.unpatchAll()
    }
}
