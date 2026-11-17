package com.github.imago;

import com.aliucord.Http;
import com.aliucord.Utils;
import com.discord.stores.StoreStream;

import java.util.HashMap;
import java.util.Map;

public final class ImagoSender {
    private ImagoSender() {}

    /** Envia o link da imagem como mensagem no canal aberto. Bloqueante: use fora da thread principal. */
    public static void send(String url) throws Exception {
        long channelId = StoreStream.getChannelsSelected().getId();
        if (channelId == 0L) throw new Exception("Imago: não achei o canal.");

        String nonce = String.valueOf(System.currentTimeMillis() * 4194304L + (long) (Math.random() * 4194304));
        // Map (não JSONObject): o executeWithJson serializa com Gson
        Map<String, Object> body = new HashMap<>();
        body.put("content", url);
        body.put("tts", false);
        body.put("flags", 0);
        body.put("nonce", nonce);

        Http.Response res = Http.Request.newDiscordRNRequest("/channels/" + channelId + "/messages", "POST")
            .executeWithJson(body);
        if (!res.ok()) throw new Exception("Imago: erro ao enviar (" + res.statusCode + ").");
    }

    public static void toast(final String msg) {
        Utils.mainThread.post(() -> Utils.showToast(msg));
    }
}
