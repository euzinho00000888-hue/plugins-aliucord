package com.github.imago;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.aliucord.api.SettingsAPI;
import com.aliucord.fragments.SettingsPage;
import com.aliucord.views.TextInput;
import com.google.android.material.button.MaterialButton;

public class ImagoSettings extends SettingsPage {
    private final SettingsAPI settings;

    public ImagoSettings(SettingsAPI settings) {
        this.settings = settings;
    }

    private interface OnChange {
        void changed(String value);
    }

    @Override
    public void onViewBound(View view) {
        super.onViewBound(view);
        setActionBarTitle("Imago");

        Context ctx = view.getContext();
        final ImagoConfig config = new ImagoConfig(settings);
        int pad = (int) (12 * ctx.getResources().getDisplayMetrics().density);

        getLinearLayout().addView(field(ctx, "Imago Key (imk_...)", config.getApiKey(), true, config::setApiKey));
        getLinearLayout().addView(field(ctx, "Endereço do Imago", config.getBase(), false, config::setBase));

        final TextView dump = new TextView(ctx);
        dump.setTextIsSelectable(true);
        dump.setTextSize(11f);
        dump.setPadding(pad, pad, pad, pad);

        MaterialButton button = new MaterialButton(ctx);
        button.setText("Diagnóstico do botão");
        button.setOnClickListener(v -> {
            String d = ButtonInjector.getLastDump();
            dump.setText(d.isEmpty() ? "Nada coletado. Abra um chat e volte aqui." : d);
        });
        getLinearLayout().addView(button);
        getLinearLayout().addView(dump);
    }

    private TextInput field(Context ctx, String title, String initial, boolean secure, final OnChange onChange) {
        TextInput input = new TextInput(ctx);
        input.setHint(title);
        EditText et = input.getEditText();
        if (et != null) {
            et.setText(initial);
            et.setInputType(InputType.TYPE_CLASS_TEXT
                | (secure ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    onChange.changed(s == null ? "" : s.toString());
                }
            });
        }
        return input;
    }
}
