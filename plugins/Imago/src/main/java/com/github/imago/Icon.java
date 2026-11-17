package com.github.imago;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

/** Ícone do botão (mesmo PNG do plugin original do Revenge). */
public final class Icon {
    private static final String PNG_BASE64 =
        "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" +
        "RU5ErkJggg==";

    private static Bitmap bitmap;

    private Icon() {}

    public static synchronized Bitmap getBitmap() {
        if (bitmap == null) {
            byte[] bytes = Base64.decode(PNG_BASE64, Base64.DEFAULT);
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return bitmap;
    }
}
