package com.github.imago;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public final class ImagoApi {
    private ImagoApi() {}

    /** Bloqueante: chame fora da thread principal. */
    public static List<ImagoItem> search(ImagoConfig config, String query) throws Exception {
        String key = config.getApiKey().trim();
        if (key.isEmpty()) {
            throw new Exception("Coloque sua Imago Key nas configurações do plugin (Plugins > Imago > engrenagem).");
        }
        String base = config.getBase().trim().replaceAll("/+$", "");
        URL url = new URL(base + "/api/v1/search?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=30");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = stream == null ? "" : readAll(stream);

            JSONObject json = null;
            try {
                json = new JSONObject(text);
            } catch (JSONException ignored) {
            }

            if (code < 200 || code >= 300) {
                String err = json == null ? "" : json.optString("error");
                throw new Exception(err.isEmpty() ? "Imago respondeu " + code : err);
            }

            List<ImagoItem> out = new ArrayList<>();
            JSONArray arr = json == null ? null : json.optJSONArray("results");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String u = o.optString("url");
                if (!u.isEmpty()) out.add(new ImagoItem(o.optString("id", u), u));
            }
            return out;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }
}
