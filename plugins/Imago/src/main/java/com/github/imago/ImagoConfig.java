package com.github.imago;

import com.aliucord.api.SettingsAPI;

/** Acesso às configurações salvas (mesmas chaves do plugin do Revenge). */
public class ImagoConfig {
    public static final String KEY_API = "apiKey";
    public static final String KEY_BASE = "base";
    public static final String DEFAULT_BASE = "https://imago-gamma.vercel.app";

    private final SettingsAPI settings;

    public ImagoConfig(SettingsAPI settings) {
        this.settings = settings;
    }

    public String getApiKey() {
        return settings.getString(KEY_API, "");
    }

    public void setApiKey(String v) {
        settings.setString(KEY_API, v);
    }

    public String getBase() {
        String b = settings.getString(KEY_BASE, DEFAULT_BASE);
        return b == null || b.trim().isEmpty() ? DEFAULT_BASE : b;
    }

    public void setBase(String v) {
        settings.setString(KEY_BASE, v);
    }
}
