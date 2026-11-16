package org.telegram.messenger.happ;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Xray-core JSON config builder (best-effort for VLESS/Reality and Shadowsocks).
 */
public class HappXrayConfigBuilder {

    public static String build(HappLinkParser.Profile p, String tunName, int mtu) throws Exception {
        if (p == null || !p.isSupported()) {
            throw new IllegalArgumentException("Unsupported profile");
        }

        JSONObject root = new JSONObject();

        JSONObject log = new JSONObject();
        log.put("loglevel", "debug");
        log.put("access", "");
        log.put("error", "");
        root.put("log", log);

        // Inbounds: TUN for VPN fd + loopback socks for local checks
        JSONArray inbounds = new JSONArray();
        JSONObject sniffing = new JSONObject();
        sniffing.put("enabled", true);
        JSONArray destOverride = new JSONArray();
        destOverride.put("http");
        destOverride.put("tls");
        destOverride.put("quic");
        sniffing.put("destOverride", destOverride);
        sniffing.put("routeOnly", false);

        JSONObject tun = new JSONObject();
        tun.put("tag", "tun-in");
        tun.put("protocol", "tun");
        tun.put("listen", "127.0.0.1");
        tun.put("port", 0);
        JSONObject tunSettings = new JSONObject();
        tunSettings.put("name", tunName != null ? tunName : "tun0");
        tunSettings.put("MTU", mtu > 0 ? mtu : 1500);
        tun.put("settings", tunSettings);
        tun.put("sniffing", sniffing);
        inbounds.put(tun);

        JSONObject socks = new JSONObject();
        socks.put("tag", "socks-in");
        socks.put("listen", "127.0.0.1");
        socks.put("port", 10808);
        socks.put("protocol", "socks");
        JSONObject socksSettings = new JSONObject();
        socksSettings.put("udp", true);
        socksSettings.put("auth", "noauth");
        socks.put("settings", socksSettings);
        socks.put("sniffing", new JSONObject(sniffing.toString()));
        inbounds.put(socks);
        root.put("inbounds", inbounds);

        // Outbounds
        JSONArray outbounds = new JSONArray();
        JSONObject proxy = new JSONObject();
        proxy.put("tag", "proxy");
        if ("vless".equals(p.type)) {
            proxy.put("protocol", "vless");
            JSONObject settings = new JSONObject();
            JSONArray vnext = new JSONArray();
            JSONObject server = new JSONObject();
            server.put("address", p.server);
            server.put("port", p.port);
            JSONArray users = new JSONArray();
            JSONObject user = new JSONObject();
            user.put("id", p.uuid);
            user.put("encryption", "none");
            String flow = p.flow;
            String sec = p.security != null ? p.security.toLowerCase() : "";
            // keep profile flow as-is; defaulting for Reality can be server-incompatible in some cases
            if (flow != null && !flow.isEmpty()) {
                user.put("flow", flow);
            }
            user.put("level", 0);
            users.put(user);
            server.put("users", users);
            vnext.put(server);
            settings.put("vnext", vnext);
            proxy.put("settings", settings);
            proxy.put("streamSettings", buildStreamSettings(p));
        } else if ("ss".equals(p.type)) {
            proxy.put("protocol", "shadowsocks");
            JSONObject settings = new JSONObject();
            JSONArray servers = new JSONArray();
            JSONObject s = new JSONObject();
            s.put("address", p.server);
            s.put("port", p.port);
            s.put("method", p.method);
            s.put("password", p.password != null ? p.password : "");
            s.put("level", 0);
            servers.put(s);
            settings.put("servers", servers);
            proxy.put("settings", settings);
            proxy.put("streamSettings", new JSONObject().put("network", "tcp").put("security", "none"));
        } else {
            throw new IllegalArgumentException("Unsupported profile type: " + p.type);
        }
        outbounds.put(proxy);

        JSONObject direct = new JSONObject();
        direct.put("tag", "direct");
        direct.put("protocol", "freedom");
        direct.put("settings", new JSONObject());
        outbounds.put(direct);

        JSONObject block = new JSONObject();
        block.put("tag", "block");
        block.put("protocol", "blackhole");
        JSONObject blockSettings = new JSONObject();
        JSONObject resp = new JSONObject();
        resp.put("type", "http");
        blockSettings.put("response", resp);
        block.put("settings", blockSettings);
        outbounds.put(block);
        root.put("outbounds", outbounds);

        // DNS
        JSONObject dns = new JSONObject();
        JSONArray dnsServers = new JSONArray();
        dnsServers.put("1.1.1.1");
        dnsServers.put("8.8.8.8");
        dns.put("servers", dnsServers);
        root.put("dns", dns);

        // Routing
        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", "AsIs");
        JSONArray rules = new JSONArray();
        if (p.server != null && p.server.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            JSONObject bypass = new JSONObject();
            bypass.put("type", "field");
            JSONArray ip = new JSONArray();
            ip.put(p.server);
            bypass.put("ip", ip);
            bypass.put("outboundTag", "direct");
            rules.put(bypass);
        }
        JSONObject dnsOut = new JSONObject();
        dnsOut.put("type", "field");
        dnsOut.put("port", "53");
        dnsOut.put("outboundTag", "proxy");
        rules.put(dnsOut);
        routing.put("rules", rules);
        root.put("routing", routing);

        return root.toString();
    }

    private static JSONObject buildStreamSettings(HappLinkParser.Profile p) throws Exception {
        JSONObject ss = new JSONObject();
        String net = p.network != null ? p.network.toLowerCase() : "tcp";
        String sec = p.security != null ? p.security.toLowerCase() : "";
        ss.put("network", net.isEmpty() ? "tcp" : net);

        if ("tls".equals(sec)) {
            ss.put("security", "tls");
            JSONObject tls = new JSONObject();
            if (p.sni != null && !p.sni.isEmpty()) tls.put("serverName", p.sni);
            if (p.fp != null && !p.fp.isEmpty()) tls.put("fingerprint", p.fp);
            ss.put("tlsSettings", tls);
        } else if ("reality".equals(sec)) {
            ss.put("security", "reality");
            JSONObject reality = new JSONObject();
            reality.put("show", false);
            reality.put("fingerprint", (p.fp != null && !p.fp.isEmpty()) ? p.fp : "chrome");
            if (p.sni != null && !p.sni.isEmpty()) reality.put("serverName", p.sni);
            if (p.publicKey != null) reality.put("publicKey", p.publicKey);
            reality.put("shortId", p.shortId != null ? p.shortId : "");
            reality.put("spiderX", "/");
            ss.put("realitySettings", reality);
        } else {
            ss.put("security", "none");
        }

        if ("ws".equals(net)) {
            JSONObject ws = new JSONObject();
            if (p.path != null && !p.path.isEmpty()) ws.put("path", p.path);
            if (p.hostHeader != null && !p.hostHeader.isEmpty()) {
                JSONObject headers = new JSONObject();
                headers.put("Host", p.hostHeader);
                ws.put("headers", headers);
            }
            ss.put("wsSettings", ws);
        } else if ("grpc".equals(net)) {
            JSONObject grpc = new JSONObject();
            if (p.path != null && !p.path.isEmpty()) grpc.put("serviceName", p.path);
            ss.put("grpcSettings", grpc);
        } else {
            // tcp
            if ("reality".equals(sec) || "tls".equals(sec)) {
                // keep defaults; headerType none
            }
        }
        return ss;
    }
}
