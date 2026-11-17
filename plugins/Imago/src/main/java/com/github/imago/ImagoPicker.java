package com.github.imago;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Seletor de imagens: bottom sheet com busca e grade de 3 colunas. */
public class ImagoPicker {

    /** Carregador simples de miniaturas (cache em memória + redução de resolução). */
    private static final class Thumbs {
        private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
        private static final Handler MAIN = new Handler(Looper.getMainLooper());

        static void load(final ImageView view, final String url) {
            view.setTag(url);
            Bitmap hit = CACHE.get(url);
            if (hit != null) {
                view.setImageBitmap(hit);
                return;
            }
            view.setImageDrawable(null);
            POOL.execute(() -> {
                final Bitmap bmp;
                try {
                    bmp = download(url, 360);
                } catch (Exception e) {
                    return;
                }
                if (bmp == null) return;
                CACHE.put(url, bmp);
                MAIN.post(() -> {
                    if (url.equals(view.getTag())) view.setImageBitmap(bmp);
                });
            });
        }

        private static Bitmap download(String url, int target) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            byte[] bytes;
            try {
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    bytes = out.toByteArray();
                }
            } finally {
                conn.disconnect();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        }
    }

    public interface OnPick {
        void onPick(String url);
    }

    private final Context context;
    private final ImagoConfig config;
    private final OnPick onPick;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService searchPool = Executors.newSingleThreadExecutor();
    private int seq = 0;
    private Runnable pending;

    private final List<ImagoItem> items = new ArrayList<>();
    private TextView status;
    private Adapter adapter;
    private BottomSheetDialog dialog;

    public ImagoPicker(Context context, ImagoConfig config, OnPick onPick) {
        this.context = context;
        this.config = config;
        this.onPick = onPick;
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(String color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(radius);
        return d;
    }

    public void show() {
        dialog = new BottomSheetDialog(context);
        int screenH = context.getResources().getDisplayMetrics().heightPixels;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (screenH * 0.65)));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E1F22"));
        float r = dp(16);
        bg.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        root.setBackground(bg);

        EditText input = new EditText(context);
        input.setHint("Buscar no Imago…");
        input.setHintTextColor(Color.parseColor("#80848E"));
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(rounded("#2B2D31", dp(10)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.bottomMargin = dp(10);
        root.addView(input, inputLp);

        status = new TextView(context);
        status.setTextColor(Color.parseColor("#B5BAC1"));
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.bottomMargin = dp(8);
        root.addView(status, statusLp);

        adapter = new Adapter();
        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new GridLayoutManager(context, 3));
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                schedule(s == null ? "" : s.toString());
            }
        });

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            // Garante que a folha abra por inteiro (não só o "peek")
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        dialog.setOnDismissListener(d -> {
            if (pending != null) ui.removeCallbacks(pending);
            seq++; // descarta resultados em andamento
        });
        dialog.show();
        schedule(""); // carrega os resultados iniciais, como no original
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    /** Debounce de 350 ms, igual ao plugin do Revenge. */
    private void schedule(final String q) {
        if (pending != null) ui.removeCallbacks(pending);
        final int mine = ++seq;
        showStatus("Carregando…", items.isEmpty(), false);
        pending = () -> searchPool.execute(() -> {
            List<ImagoItem> result = null;
            Exception error = null;
            try {
                result = ImagoApi.search(config, q);
            } catch (Exception e) {
                error = e;
            }
            final List<ImagoItem> fr = result;
            final Exception fe = error;
            ui.post(() -> {
                if (mine != seq) return;
                if (fe != null) {
                    showStatus(fe.getMessage() != null ? fe.getMessage() : fe.toString(), true, true);
                    return;
                }
                items.clear();
                items.addAll(fr);
                adapter.notifyDataSetChanged();
                if (fr.isEmpty()) showStatus("Nada encontrado.", true, false);
                else showStatus(null, false, false);
            });
        });
        ui.postDelayed(pending, 350);
    }

    private void showStatus(String text, boolean visible, boolean error) {
        status.setVisibility(visible && text != null ? View.VISIBLE : View.GONE);
        status.setText(text);
        status.setTextColor(Color.parseColor(error ? "#F23F43" : "#B5BAC1"));
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView img = new ImageView(parent.getContext());
            img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            img.setBackground(rounded("#2B2D31", dp(8)));

            FrameLayout cell = new FrameLayout(parent.getContext());
            cell.setPadding(dp(3), dp(3), dp(3), dp(3));
            cell.addView(img, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(110)));
            return new RecyclerView.ViewHolder(cell) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final ImagoItem item = items.get(position);
            FrameLayout cell = (FrameLayout) holder.itemView;
            // célula quadrada (aspectRatio 1 do original)
            View parent = (View) cell.getParent();
            int side = parent != null && parent.getWidth() > 0 ? parent.getWidth() / 3 : dp(110);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, side));
            Thumbs.load((ImageView) cell.getChildAt(0), item.url);
            cell.setOnClickListener(v -> onPick.onPick(item.url));
        }
    }
}
