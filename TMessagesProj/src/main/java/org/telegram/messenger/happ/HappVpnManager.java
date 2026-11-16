package org.telegram.messenger.happ;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.List;

public class HappVpnManager {
    public static final String ACTION_STATE_CHANGED = "org.telegram.messenger.happ.STATE_CHANGED";
    private static final String PREFS = "happ_servers";
    private static final String KEY_SELECTED = "selected_server_v1";
    private static final String KEY_RUNNING = "vpn_running_v1";

    private static volatile boolean running;

    public static boolean isRunning() {
        return running || isServiceRunning();
    }

    public static boolean isServiceRunning() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return false;
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            List<ActivityManager.RunningServiceInfo> list = am.getRunningServices(100);
            if (list != null) {
                String name = HappVpnService.class.getName();
                for (ActivityManager.RunningServiceInfo info : list) {
                    if (info != null && info.service != null && name.equals(info.service.getClassName())) {
                        running = true;
                        return true;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        running = false;
        return false;
    }

    public static void setRunning(boolean value) {
        running = value;
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_RUNNING, value).apply();
        } catch (Exception ignore) {
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                Intent intent = new Intent(ACTION_STATE_CHANGED);
                intent.setPackage(ctx.getPackageName());
                ctx.sendBroadcast(intent);
            }
        } catch (Exception ignore) {
        }
    }

    public static void setSelectedIndex(int index) {
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putInt(KEY_SELECTED, index).commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static int getSelectedIndex() {
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return prefs.getInt(KEY_SELECTED, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String getSelectedServer() {
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String data = prefs.getString("servers_v1", "");
            if (TextUtils.isEmpty(data)) return null;
            String trimmed = data.trim();
            int idx = getSelectedIndex();
            if (idx < 0) return null;
            if (trimmed.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(trimmed);
                if (idx >= 0 && idx < arr.length()) {
                    String s = arr.optString(idx, null);
                    return s != null ? s.trim() : null;
                }
                return null;
            }
            String[] parts = data.split("\n");
            if (idx >= 0 && idx < parts.length) return parts[idx].trim();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Intent prepare(Context context) {
        try {
            return VpnService.prepare(context);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void start(Context context, String link) {
        if (context == null || TextUtils.isEmpty(link)) return;
        Intent intent = new Intent(context, HappVpnService.class);
        intent.setAction(HappVpnService.ACTION_START);
        intent.putExtra(HappVpnService.EXTRA_LINK, link);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            FileLog.e(e);
            try {
                context.startService(intent);
            } catch (Exception e2) {
                FileLog.e(e2);
            }
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, HappVpnService.class);
        intent.setAction(HappVpnService.ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
