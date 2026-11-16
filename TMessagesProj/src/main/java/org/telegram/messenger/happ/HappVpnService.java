package org.telegram.messenger.happ;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.HappLogsActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public class HappVpnService extends VpnService {
    public static final String ACTION_START = "org.telegram.messenger.happ.START";
    public static final String ACTION_STOP = "org.telegram.messenger.happ.STOP";
    public static final String EXTRA_LINK = "link";
    private static final String CHANNEL_ID = "happ_vpn";
    private static final int NOTIF_ID = 7011;
    private static final String TAG = "happ-vpn";

    private static final AtomicBoolean coreEnvInit = new AtomicBoolean(false);

    private ParcelFileDescriptor tunFd;
    private CoreController xrayController;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        FileLog.d(TAG + " onStartCommand action=" + action);
        HappLogsActivity.appendDebugLog(this, "onStartCommand action=" + action);
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }
        String link = intent != null ? intent.getStringExtra(EXTRA_LINK) : null;
        if (link == null || link.trim().isEmpty()) {
            failAndStop("Empty server link");
            return START_NOT_STICKY;
        }
        startForegroundCompat("Happ VPN");
        final String l = link;
        new Thread(() -> startVpn(l), "happ-vpn-start").start();
        return START_STICKY;
    }

    private void toast(final String msg) {
        uiHandler.post(() -> {
            try {
                Toast.makeText(ApplicationLoader.applicationContext != null ? ApplicationLoader.applicationContext : HappVpnService.this, msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {}
        });
    }

    private void failAndStop(String reason) {
        FileLog.e(TAG + " fail: " + reason);
        HappLogsActivity.appendDebugLog(this, "FAIL: " + reason);
        try { android.util.Log.e("happ-vpn", "fail: " + reason); } catch (Throwable ignore) {}
        HappVpnManager.setRunning(false);
        if (reason != null) toast("Happ VPN: " + reason);
        stopForegroundSafe();
        stopSelf();
    }

    private void startVpn(String link) {
        try {
            HappLinkParser.Profile profile = HappLinkParser.parse(link);
            if (profile == null || !profile.isSupported()) {
                failAndStop("Неподдерживаемый профиль (нужен VLESS/SS)");
                return;
            }
            String config = HappXrayConfigBuilder.build(profile, "happ-tun", 1500);
            FileLog.d(TAG + " xray config generated for " + profile.type + " " + profile.server);
            HappLogsActivity.appendDebugLog(this, "config generated: " + profile.type + " " + profile.server + ":" + profile.port);
            writeConfigFile(config);
            updateForegroundNotification(displayName(profile, "Happ VPN") + " (Xray)");
            startXray(config, profile);
            HappVpnManager.setRunning(true);
            FileLog.d(TAG + " service started (Xray)");
            HappLogsActivity.appendDebugLog(this, "VPN started (Xray)");
            toast("Happ VPN: подключено (Xray)");
        } catch (Throwable e) {
            FileLog.e(TAG + " startVpn error", e);
            HappLogsActivity.appendDebugLog(this, "startVpn error: " + e);
            try { android.util.Log.e("happ-vpn", "startVpn error", e); } catch (Throwable ignore) {}
            writeErrorFile(e);
            String msg = e.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
            failAndStop(msg);
        }
    }

    private String displayName(HappLinkParser.Profile p, String def) {
        if (p == null) return def;
        if (p.name != null && !p.name.trim().isEmpty()) return p.name.trim();
        if (p.server != null) return p.server + ":" + p.port;
        return def;
    }

    private void writeConfigFile(String config) {
        try {
            File cf = new File(getExternalFilesDir(null), "happ_last_config.json");
            FileWriter fw = new FileWriter(cf, false);
            fw.write(config);
            fw.close();
        } catch (Throwable ignore) {}
    }

    private void writeErrorFile(Throwable e) {
        try {
            File f = new File(getExternalFilesDir(null), "happ_last_error.txt");
            FileWriter fw = new FileWriter(f, false);
            fw.write(String.valueOf(e) + "\n");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            fw.write(sw.toString());
            fw.close();
        } catch (Throwable ignore) {}
    }

    // Xray expects xray.xudp.basekey env as base64(rawurl, no padding) of 32-byte key
    private String getOrCreateXudpBaseKey(File dir) {
        File f = dir != null ? new File(dir, "xudp_basekey.b64") : null;
        try {
            if (f != null && f.exists()) {
                byte[] data = new byte[(int) Math.min(f.length(), 128)];
                FileInputStream fis = new FileInputStream(f);
                int n = fis.read(data);
                fis.close();
                if (n > 0) {
                    String s = new String(data, 0, n, StandardCharsets.US_ASCII).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Throwable ignore) {}
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = android.util.Base64.encodeToString(key, android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
        try {
            if (dir != null && !dir.exists()) dir.mkdirs();
            if (f != null) {
                FileOutputStream fos = new FileOutputStream(f, false);
                fos.write(encoded.getBytes(StandardCharsets.US_ASCII));
                fos.flush();
                fos.close();
            }
        } catch (Throwable t) {
            FileLog.e(TAG + " save xudp basekey failed", t);
        }
        return encoded;
    }

    private static boolean isIpv4Literal(String host) {
        if (host == null) return false;
        String[] parts = host.trim().split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) return false;
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    private static boolean addRoutesExcludingIpv4(Builder builder, String ipv4) throws Exception {
        String[] parts = ipv4.trim().split("\\.");
        long ip = 0;
        for (String p : parts) ip = (ip << 8) | (Integer.parseInt(p) & 0xff);
        for (int prefix = 0; prefix < 32; prefix++) {
            long bit = 1L << (31 - prefix);
            long net = ip & ~(bit - 1);
            if ((ip & bit) == 0) net |= bit;
            else net &= ~bit;
            String addr = ((net >> 24) & 0xff) + "." + ((net >> 16) & 0xff) + "." + ((net >> 8) & 0xff) + "." + (net & 0xff);
            builder.addRoute(addr, prefix + 1);
        }
        return true;
    }

    private void initCoreEnvIfNeeded() {
        if (coreEnvInit.get()) return;
        synchronized (HappVpnService.class) {
            if (coreEnvInit.get()) return;
            File internal = getFilesDir();
            File external = getExternalFilesDir(null);
            if (external == null) external = internal;
            String baseKey = getOrCreateXudpBaseKey(internal);
            try {
                Libv2ray.initCoreEnv(external.getAbsolutePath(), baseKey);
            } catch (Throwable t) {
                FileLog.e(TAG + " initCoreEnv(external,basekey) failed", t);
                try {
                    Libv2ray.initCoreEnv(internal.getAbsolutePath(), baseKey);
                } catch (Throwable t2) {
                    FileLog.e(TAG + " initCoreEnv(internal,basekey) failed", t2);
                }
            }
            coreEnvInit.set(true);
        }
    }

    private void startXray(String config, HappLinkParser.Profile profile) throws Exception {
        stopXray();
        closeTun();
        if (prepare(this) != null) {
            throw new IllegalStateException("VPN permission not granted");
        }
        initCoreEnvIfNeeded();

        Builder builder = new Builder().setSession("Happ VPN (Xray)");
        try { builder.setMtu(1500); } catch (Throwable ignore) {}
        try { builder.addAddress("172.19.0.1", 30); } catch (Throwable ignore) {}
        try {
            boolean routed = false;
            String server = profile != null ? profile.server : null;
            if (server != null && isIpv4Literal(server)) {
                routed = addRoutesExcludingIpv4(builder, server);
            }
            if (!routed) {
                builder.addRoute("0.0.0.0", 0);
            }
        } catch (Throwable t) {
            FileLog.e(TAG + " addRoute failed, fallback default", t);
            try { builder.addRoute("0.0.0.0", 0); } catch (Throwable ignore) {}
        }
        try { builder.addDnsServer("8.8.8.8"); } catch (Throwable ignore) {}
        try { builder.addDnsServer("1.1.1.1"); } catch (Throwable ignore) {}

        tunFd = builder.establish();
        if (tunFd == null) throw new IllegalStateException("Failed to establish VPN (tunFd null)");

        CoreCallbackHandler cb = new CoreCallbackHandler() {
            @Override public long onEmitStatus(long code, String message) {
                FileLog.d("happ-xray: [" + code + "] " + message);
                try { android.util.Log.d("happ-xray", "[" + code + "] " + message); } catch (Throwable ignore) {}
                return 0;
            }
            @Override public long shutdown() { FileLog.d("happ-xray: shutdown"); return 0; }
            @Override public long startup() { FileLog.d("happ-xray: startup"); return 0; }
        };
        xrayController = Libv2ray.newCoreController(cb);
        xrayController.startLoop(config, tunFd.getFd());
    }

    private void stopXray() {
        try {
            if (xrayController != null) {
                try { xrayController.stopLoop(); } catch (Throwable t) { FileLog.e(t); }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            xrayController = null;
        }
    }

    private void stopVpn() {
        HappVpnManager.setRunning(false);
        stopXray();
        closeTun();
        stopForegroundSafe();
        stopSelf();
    }

    private void closeTun() {
        try {
            if (tunFd != null) tunFd.close();
        } catch (Throwable ignore) {
        }
        tunFd = null;
    }

    private void startForegroundCompat(String title) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Happ VPN", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
            startForeground(NOTIF_ID, buildNotification(title, "Connecting..."));
        } catch (Throwable e) {
            FileLog.e(TAG + " startForeground failed", e);
        }
    }

    private void updateForegroundNotification(String title) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID, buildNotification(title, "VPN active"));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pi = null;
        if (i != null) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            pi = PendingIntent.getActivity(this, 0, i, flags);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle(title != null ? title : "Happ VPN")
                .setContentText(text != null ? text : "VPN active")
                .setSmallIcon(R.drawable.notification)
                .setOngoing(true);
        if (pi != null) b.setContentIntent(pi);
        return b.build();
    }

    private void stopForegroundSafe() {
        try { stopForeground(true); } catch (Throwable ignore) {}
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopVpn();
        super.onRevoke();
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (SERVICE_INTERFACE.equals(action)) {
            return super.onBind(intent);
        }
        return null;
    }
}
