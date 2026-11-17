package com.github.imago;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.aliucord.Utils;
import com.discord.utilities.color.ColorCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Equivalente ao `onElement` do plugin do Revenge: procura, na barra de entrada do chat, o
 * contêiner dos botões de presente / emoji / microfone e insere o botão do Imago ao lado.
 * Em vez de olhar a árvore React, aqui olhamos a árvore de Views (ids e contentDescription).
 */
public final class ButtonInjector {
    private static final String TAG = "imago-button";
    private static final Pattern HINT =
        Pattern.compile("gift|emoji|expression|voice|mic|record|sticker|present", Pattern.CASE_INSENSITIVE);

    private static final List<WeakReference<View>> injected = new ArrayList<>();

    /** Texto do último diagnóstico (mostrado nas configurações). */
    private static volatile String lastDump = "";

    private ButtonInjector() {}

    public static String getLastDump() {
        return lastDump;
    }

    private static String entryName(View v) {
        try {
            return v.getId() != View.NO_ID ? v.getResources().getResourceEntryName(v.getId()) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String label(View v) {
        CharSequence cd = v.getContentDescription();
        return v.getClass().getSimpleName() + "|" + entryName(v) + "|" + (cd == null ? "" : cd);
    }

    private static void walk(View v, int depth, List<View> views, List<Integer> depths) {
        views.add(v);
        depths.add(depth);
        // não desce em listas (ex.: autocomplete, reações) para não achar falsos positivos
        if (v instanceof ViewGroup && !(v instanceof RecyclerView)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), depth + 1, views, depths);
        }
    }

    private static int themedColor(Context ctx) {
        try {
            int attr = Utils.getResId("colorInteractiveNormal", "attr");
            if (attr != 0) return ColorCompat.getThemedColor(ctx, attr);
        } catch (Throwable ignored) {
        }
        return Color.parseColor("#B5BAC1");
    }

    /** @return true se o botão está (ou ficou) presente na barra. */
    public static boolean inject(View root, View.OnClickListener onClick) {
        List<View> all = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        walk(root, 0, all, depths);

        StringBuilder dump = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            for (int d = 0; d < depths.get(i); d++) dump.append("  ");
            dump.append(label(all.get(i))).append('\n');
        }
        lastDump = dump.toString();

        if (root.findViewWithTag(TAG) != null) return true;

        List<View> hits = new ArrayList<>();
        for (View v : all) {
            if (v != root && !TAG.equals(v.getTag()) && HINT.matcher(label(v)).find()) hits.add(v);
        }
        if (hits.isEmpty()) return false;

        // contêiner (LinearLayout) com mais botões "parecidos"
        Map<LinearLayout, Integer> count = new HashMap<>();
        LinearLayout parent = null;
        int best = 0;
        for (View v : hits) {
            if (!(v.getParent() instanceof LinearLayout)) continue;
            LinearLayout p = (LinearLayout) v.getParent();
            int c = count.containsKey(p) ? count.get(p) + 1 : 1;
            count.put(p, c);
            if (c > best) {
                best = c;
                parent = p;
            }
        }
        if (parent == null) return false;

        List<View> siblings = new ArrayList<>();
        for (View v : hits) if (v.getParent() == parent) siblings.add(v);
        View anchor = siblings.get(siblings.size() - 1);
        View ref = siblings.get(0);
        Context ctx = root.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        ViewGroup.LayoutParams rlp = ref.getLayoutParams();
        int w = rlp != null && rlp.width > 0 && rlp.height > 0 ? rlp.width : (int) (40 * density);
        int h = rlp != null && rlp.width > 0 && rlp.height > 0 ? rlp.height : w;
        int pad = (int) (8 * density);

        ImageView btn = new ImageView(ctx);
        btn.setTag(TAG);
        btn.setContentDescription("Imago");
        btn.setImageBitmap(Icon.getBitmap());
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(pad, pad, pad, pad);
        btn.setColorFilter(themedColor(ctx), PorterDuff.Mode.SRC_IN);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(onClick);

        parent.addView(btn, parent.indexOfChild(anchor), new LinearLayout.LayoutParams(w, h));
        injected.add(new WeakReference<>(btn));
        return true;
    }

    public static void removeAll() {
        for (WeakReference<View> ref : injected) {
            View v = ref.get();
            if (v == null) continue;
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        }
        injected.clear();
    }
}
