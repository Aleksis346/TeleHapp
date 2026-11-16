package org.telegram.messenger.happ;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class HappLinkParser {

    public static class Profile {
        public String type; // vless, ss
        public String name;
        public String server;
        public int port;
        public String uuid; // vless
        public String method; // ss
        public String password; // ss/vless? (none)
        public String security = ""; // tls/reality/none
        public String sni = "";
        public String fp = "";
        public String publicKey = ""; // reality pbk
        public String shortId = ""; // reality sid
        public String flow = "";
        public String network = "tcp"; // tcp/ws/grpc
        public String path = "";
        public String hostHeader = "";

        public boolean isSupported() {
            return "vless".equals(type) || "ss".equals(type);
        }
    }

    public static Profile parse(String link) {
        if (link == null) return null;
        String t = link.trim();
        try {
            if (t.toLowerCase().startsWith("vless://")) {
                return parseVless(t);
            } else if (t.toLowerCase().startsWith("ss://")) {
                return parseSs(t);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static Profile parseVless(String link) throws Exception {
        Uri uri = Uri.parse(link);
        Profile p = new Profile();
        p.type = "vless";
        p.name = uri.getFragment();
        p.uuid = uri.getUserInfo();
        p.server = uri.getHost();
        p.port = uri.getPort();
        if (TextUtils.isEmpty(p.server) || p.port <= 0 || TextUtils.isEmpty(p.uuid)) return null;
        p.security = value(uri, "security", "");
        p.sni = value(uri, "sni", value(uri, "host", ""));
        p.fp = value(uri, "fp", "");
        p.publicKey = value(uri, "pbk", value(uri, "publicKey", ""));
        p.shortId = value(uri, "sid", value(uri, "shortId", ""));
        p.flow = value(uri, "flow", "");
        p.network = value(uri, "type", value(uri, "net", "tcp"));
        p.path = value(uri, "path", "");
        p.hostHeader = value(uri, "host", "");
        return p;
    }

    private static Profile parseSs(String link) throws Exception {
        // handle ss://base64#name and ss://method:pass@host:port#name
        String raw = link.substring(5); // after ss://
        String name = null;
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            name = Uri.decode(raw.substring(hash + 1));
            raw = raw.substring(0, hash);
        }
        int q = raw.indexOf('?');
        if (q >= 0) raw = raw.substring(0, q); // ignore plugin params for MVP
        int at = raw.lastIndexOf('@');
        Profile p = new Profile();
        p.type = "ss";
        p.name = name;
        String userInfo;
        String hostPort;
        if (at >= 0) {
            userInfo = raw.substring(0, at);
            hostPort = raw.substring(at + 1);
            // userInfo may be base64url or plain
            if (!userInfo.contains(":")) {
                String dec = decodeMaybe(userInfo);
                if (dec != null) userInfo = dec;
            }
        } else {
            String dec = decodeMaybe(raw);
            if (dec == null) return null;
            int at2 = dec.lastIndexOf('@');
            if (at2 < 0) return null;
            userInfo = dec.substring(0, at2);
            hostPort = dec.substring(at2 + 1);
        }
        int colon = userInfo.indexOf(':');
        if (colon < 0) return null;
        p.method = userInfo.substring(0, colon);
        p.password = userInfo.substring(colon + 1);
        // hostPort host:port (ipv6 in brackets)
        if (hostPort.startsWith("[")) {
            int rb = hostPort.indexOf(']');
            if (rb < 0) return null;
            p.server = hostPort.substring(1, rb);
            int pc = hostPort.indexOf(':', rb);
            if (pc < 0) return null;
            p.port = Integer.parseInt(hostPort.substring(pc + 1).trim());
        } else {
            int pc = hostPort.lastIndexOf(':');
            if (pc < 0) return null;
            p.server = hostPort.substring(0, pc);
            p.port = Integer.parseInt(hostPort.substring(pc + 1).trim());
        }
        if (TextUtils.isEmpty(p.server) || p.port <= 0 || TextUtils.isEmpty(p.method)) return null;
        return p;
    }

    private static String value(Uri uri, String key, String def) {
        String v = uri.getQueryParameter(key);
        return v != null ? v : def;
    }

    private static String decodeMaybe(String s) {
        try {
            byte[] b = Base64.decode(fixBase64(s), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (b == null || b.length == 0) b = Base64.decode(fixBase64(s), Base64.DEFAULT);
            if (b != null && b.length > 0) return new String(b, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String fixBase64(String s) {
        String t = s.replace('-', '+').replace('_', '/').trim();
        int mod = t.length() % 4;
        if (mod == 2) t += "==";
        else if (mod == 3) t += "=";
        else if (mod == 1) t = t.substring(0, t.length() - 1);
        return t;
    }
}
