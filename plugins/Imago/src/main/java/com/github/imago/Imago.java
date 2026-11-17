package com.github.imago;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.discord.widgets.chat.input.WidgetChatInput;

@AliucordPlugin(requiresRestart = false)
@SuppressWarnings("unused")
public class Imago extends Plugin {
    public Imago() {
        settingsTab = new SettingsTab(ImagoSettings.class).withArgs(settings);
    }

    @Override
    public void start(@NonNull Context context) throws Throwable {
        final ImagoConfig config = new ImagoConfig(settings);

        // Roda toda vez que a barra de entrada do chat é montada
        patcher.patch(WidgetChatInput.class.getDeclaredMethod("onViewBound", View.class), new Hook(param -> {
            final View root = (View) param.args[0];
            root.post(() -> ButtonInjector.inject(root, button -> openPicker(button.getContext(), config)));
        }));
    }

    private void openPicker(Context ctx, ImagoConfig config) {
        final ImagoPicker[] holder = new ImagoPicker[1];
        holder[0] = new ImagoPicker(ctx, config, url -> {
            holder[0].dismiss();
            new Thread(() -> {
                try {
                    ImagoSender.send(url);
                } catch (Exception e) {
                    ImagoSender.toast(e.getMessage() != null ? e.getMessage() : "Imago: erro ao enviar.");
                }
            }).start();
        });
        holder[0].show();
    }

    @Override
    public void stop(@NonNull Context context) {
        patcher.unpatchAll();
        ButtonInjector.removeAll();
    }
}
