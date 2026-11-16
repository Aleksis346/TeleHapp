package org.telegram.messenger.happ;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.security.MessageDigest;
import java.util.UUID;

public class HappHwid {
    private static final String PREFS = "happ_servers";
    private static final String KEY_HWID = "hwid_v1";

    public static String getHwid() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return fallback();
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String saved = prefs.getString(KEY_HWID, null);
            if (!TextUtils.isEmpty(saved)) return saved;
            String id = null;
            try {
                id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Throwable ignore) {}
            String hwid;
            if (!TextUtils.isEmpty(id) && !"9774d56d682e549c".equals(id)) {
                hwid = sha256("happ|" + id).substring(0, 32);
            } else {
                hwid = UUID.randomUUID().toString().replace("-", "");
            }
            prefs.edit().putString(KEY_HWID, hwid).apply();
            return hwid;
        } catch (Throwable ignore) {
            return fallback();
        }
    }

    private static String fallback() {
        return "00000000000000000000000000000000";
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
