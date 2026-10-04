package com.ideahelper.jrebel;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 本地 JRebel 激活服务（{@code main.go} / {@code jrebelhandler.go} 的 Java 移植），
 * 基于 JDK 内置的 {@code com.sun.net.httpserver.HttpServer}，不依赖任何第三方 HTTP 框架。
 * 默认仅监听回环地址，避免触发系统防火墙弹窗。
 *
 * <p>刻意保持为纯 Java 单例（不依赖 IntelliJ 平台 API），便于独立测试。</p>
 */
public final class JRebelLicenseServer {

    private static final JRebelLicenseServer INSTANCE = new JRebelLicenseServer();

    private static final String SERVER_GUID = "a1b4aea8-b031-4302-b602-670a990272cb";
    private static final char[] RANDOM_CHARSET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();
    private static final Random RANDOM = new Random();

    private HttpServer server;
    private int port;
    private String error;
    private String schema = "http";
    private String exportHost = "";
    private String uuid = UUID.randomUUID().toString();

    private JRebelLicenseServer() {
    }

    public static JRebelLicenseServer getInstance() {
        return INSTANCE;
    }

    // ============================================================
    // 生命周期
    // ============================================================
    public synchronized String start(int portToUse, String schemaToUse, String exportHostToUse) {
        if (server != null) {
            return null;
        }
        error = null;
        this.schema = (schemaToUse == null || schemaToUse.isEmpty()) ? "http" : schemaToUse;
        this.exportHost = exportHostToUse == null ? "" : exportHostToUse;
        try {
            HttpServer s = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), portToUse), 0);
            registerRoutes(s);
            s.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "JRebel-License-Server");
                t.setDaemon(true);
                return t;
            }));
            s.start();
            this.server = s;
            this.port = s.getAddress().getPort();
            return null;
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.toString() : t.getMessage();
            return error;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Throwable ignored) {
                // ignore
            }
            server = null;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized String getError() {
        return error;
    }

    public synchronized String getUuid() {
        return uuid;
    }

    public synchronized String newUuid() {
        uuid = UUID.randomUUID().toString();
        return uuid;
    }

    /** 激活地址：优先使用正在监听的端口，否则使用配置端口 */
    public synchronized String activationUrl(String schemaToUse, int configuredPort) {
        int p = server != null ? port : configuredPort;
        return (schemaToUse == null || schemaToUse.isEmpty() ? "http" : schemaToUse)
                + "://localhost:" + p + "/" + uuid;
    }

    // ============================================================
    // 路由
    // ============================================================
    private void registerRoutes(HttpServer s) {
        s.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path) || path.isEmpty()) {
                    handleIndex(exchange);
                } else if (path.endsWith("/jrebel/leases") || path.endsWith("/agent/leases")) {
                    handleLeases(exchange);
                } else if (path.endsWith("/jrebel/leases/1") || path.endsWith("/agent/leases/1")) {
                    handleLeases1(exchange);
                } else if (path.endsWith("/jrebel/validate-connection")) {
                    send(exchange, 200, "application/json; charset=utf-8", validateJson());
                } else if (path.endsWith("/rpc/ping.action")) {
                    handleTicket(exchange, "PingResponse");
                } else if (path.endsWith("/rpc/obtainTicket.action")) {
                    handleTicket(exchange, "ObtainTicketResponse");
                } else if (path.endsWith("/rpc/releaseTicket.action")) {
                    handleTicket(exchange, "ReleaseTicketResponse");
                } else {
                    // 其它路径（如 /{uuid}）返回首页，便于用浏览器验证
                    handleIndex(exchange);
                }
            } catch (Throwable t) {
                try {
                    send(exchange, 403, "text/plain; charset=utf-8", String.valueOf(t.getMessage()));
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        });
    }

    private void handleLeases(HttpExchange exchange) throws Exception {
        Map<String, String> p = parseForm(readBody(exchange));
        String clientRandomness = p.getOrDefault("randomness", "");
        String username = p.getOrDefault("username", "");
        String guid = p.getOrDefault("guid", "");
        if (clientRandomness.isEmpty() || username.isEmpty() || guid.isEmpty()) {
            send(exchange, 403, "text/plain; charset=utf-8", "");
            return;
        }

        boolean offline = resolveOffline(p);

        long validFromNum = 0L;
        long validUntilNum = 0L;
        String validFrom = "";
        String validUntil = "";
        if (offline) {
            String clientTime = p.getOrDefault("clientTime", "");
            long startTime;
            try {
                startTime = Long.parseLong(clientTime);
            } catch (Throwable t) {
                startTime = System.currentTimeMillis() / 1000 * 1000;
            }
            long offlineDays;
            try {
                offlineDays = Long.parseLong(p.getOrDefault("offlineDays", ""));
            } catch (Throwable t) {
                offlineDays = 7;
            }
            long expireTime = startTime + offlineDays * 24L * 60L * 60L * 1000L;
            validFromNum = startTime;
            validUntilNum = expireTime;
            validFrom = clientTime;
            validUntil = String.valueOf(expireTime);
        }

        String serverRandomness = newServerRandomness();
        String signature = leaseSignature(clientRandomness, serverRandomness, guid, offline,
                validFrom, validUntil);

        JsonObject o = new JsonObject();
        o.addProperty("serverVersion", "3.2.4");
        o.addProperty("serverProtocolVersion", "1.1");
        o.addProperty("serverGuid", SERVER_GUID);
        o.addProperty("groupType", "managed");
        o.addProperty("id", 1);
        o.addProperty("licenseType", 1);
        o.addProperty("evaluationLicense", false);
        o.addProperty("signature", signature);
        o.addProperty("serverRandomness", serverRandomness);
        o.addProperty("seatPoolType", "standalone");
        o.addProperty("statusCode", "SUCCESS");
        o.addProperty("offline", offline);
        o.add("validFrom", offline ? new JsonPrimitive(validFromNum) : JsonNull.INSTANCE);
        o.add("validUntil", offline ? new JsonPrimitive(validUntilNum) : JsonNull.INSTANCE);
        o.addProperty("company", username);
        o.addProperty("orderId", "");
        o.add("zeroIds", new JsonArray());
        o.addProperty("licenseValidFrom", 1490544001000L);
        o.addProperty("licenseValidUntil", 1891839999000L);

        send(exchange, 200, "application/json; charset=utf-8", o.toString());
    }

    private boolean resolveOffline(Map<String, String> p) {
        String product = p.getOrDefault("product", "");
        if ("XRebel".equals(product)) {
            return false;
        }
        switch (workMode) {
            case 1:
                return true;
            case 2:
                return false;
            case 3:
                return !p.getOrDefault("oldGuid", "").isEmpty();
            default:
                return Boolean.parseBoolean(p.getOrDefault("offline", "false"));
        }
    }

    private void handleLeases1(HttpExchange exchange) throws Exception {
        Map<String, String> p = parseForm(readBody(exchange));
        String username = p.getOrDefault("username", "");
        JsonObject o = new JsonObject();
        o.addProperty("serverVersion", "3.2.4");
        o.addProperty("serverProtocolVersion", "1.1");
        o.addProperty("serverGuid", SERVER_GUID);
        o.addProperty("groupType", "managed");
        o.addProperty("statusCode", "SUCCESS");
        o.add("msg", JsonNull.INSTANCE);
        o.add("statusMessage", JsonNull.INSTANCE);
        o.addProperty("signature", "dGVzdA==");
        if (!username.isEmpty()) {
            o.addProperty("company", username);
        }
        send(exchange, 200, "application/json; charset=utf-8", o.toString());
    }

    private void handleTicket(HttpExchange exchange, String responseTag) throws Exception {
        Map<String, String> p = parseForm(readBody(exchange));
        String salt = p.getOrDefault("salt", "");
        if (salt.isEmpty()) {
            send(exchange, 403, "text/plain; charset=utf-8", "");
            return;
        }
        String xmlContent;
        if ("PingResponse".equals(responseTag)) {
            xmlContent = "<PingResponse><message></message><responseCode>OK</responseCode>"
                    + "<salt>" + salt + "</salt></PingResponse>";
        } else if ("ObtainTicketResponse".equals(responseTag)) {
            String username = p.getOrDefault("userName", "");
            xmlContent = "<ObtainTicketResponse><message></message>"
                    + "<prolongationPeriod>607875500</prolongationPeriod>"
                    + "<responseCode>OK</responseCode><salt>" + salt + "</salt>"
                    + "<ticketId>1</ticketId>"
                    + "<ticketProperties>licensee=" + username + "\tlicenseType=0\t</ticketProperties>"
                    + "</ObtainTicketResponse>";
        } else {
            xmlContent = "<ReleaseTicketResponse><message></message><responseCode>OK</responseCode>"
                    + "<salt>" + salt + "</salt></ReleaseTicketResponse>";
        }
        String signature = toHex(JRebelSigner.signMd5(xmlContent));
        String body = "<!-- " + signature + " -->\n" + xmlContent;
        send(exchange, 200, "text/html; charset=utf-8", body);
    }

    private void handleIndex(HttpExchange exchange) throws Exception {
        String host;
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");
        if (exportHost.isEmpty()) {
            host = schema + "://" + (hostHeader != null ? hostHeader : "localhost");
        } else {
            host = schema + "://" + exportHost;
        }
        String url = host + "/" + getUuid();
        String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<title>JRebel License Server</title>"
                + "<style>body{font-family:Segoe UI,Tahoma,sans-serif;background:#f5f7fa;color:#333;"
                + "padding:32px}.card{max-width:720px;margin:0 auto;background:#fff;border-radius:10px;"
                + "box-shadow:0 4px 16px rgba(0,0,0,.08);padding:28px}"
                + "h1{color:#4a6bff;font-size:22px;margin-top:0}"
                + ".url{display:block;margin:12px 0;padding:12px;background:#f0f2f7;border-radius:6px;"
                + "word-break:break-all;font-family:Consolas,monospace}</style></head><body>"
                + "<div class=\"card\"><h1>JRebel License Server</h1>"
                + "<p>服务运行正常，端口 <b>" + port + "</b>。</p>"
                + "<p>JRebel 2018.1 及之后版本的激活地址（邮箱任意填写）：</p>"
                + "<span class=\"url\">" + url + "</span>"
                + "<p>旧版本（JRebel 7.1 及以前）激活地址：</p>"
                + "<span class=\"url\">" + host + "/{tokenname}</span>"
                + "</div></body></html>";
        send(exchange, 200, "text/html; charset=utf-8", html);
    }

    /** 由工具窗设置覆盖的工作模式（0 自动 1 强制离线 2 强制在线 3 oldGuid） */
    private volatile int workMode = 0;

    public void setWorkMode(int mode) {
        this.workMode = mode;
    }

    // ============================================================
    // 工具方法
    // ============================================================
    private static String leaseSignature(String clientRandomness, String serverRandomness,
                                         String guid, boolean offline, String validFrom,
                                         String validUntil) throws Exception {
        String data = offline
                ? clientRandomness + ";" + serverRandomness + ";" + guid + ";" + offline + ";"
                + validFrom + ";" + validUntil
                : clientRandomness + ";" + serverRandomness + ";" + guid + ";" + offline;
        return JRebelSigner.signSha1Base64(data);
    }

    private static String newServerRandomness() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 11; i++) {
            sb.append(RANDOM_CHARSET[RANDOM.nextInt(RANDOM_CHARSET.length)]);
        }
        sb.append('=');
        return sb.toString();
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String readBody(HttpExchange exchange) throws Exception {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body)
            throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 解析 form-urlencoded 请求体 */
    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return map;
        }
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            try {
                if (idx < 0) {
                    map.put(URLDecoder.decode(pair, "UTF-8"), "");
                } else {
                    String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(k, v);
                }
            } catch (Throwable ignored) {
                // ignore malformed pair
            }
        }
        return map;
    }

    private static String validateJson() {
        JsonObject o = new JsonObject();
        o.addProperty("serverVersion", "3.2.4");
        o.addProperty("serverProtocolVersion", "1.1");
        o.addProperty("serverGuid", SERVER_GUID);
        o.addProperty("groupType", "managed");
        o.addProperty("statusCode", "SUCCESS");
        o.addProperty("company", "Administrator");
        o.addProperty("canGetLease", true);
        o.addProperty("licenseType", 1);
        o.addProperty("evaluationLicense", false);
        o.addProperty("seatPoolType", "standalone");
        return o.toString();
    }
}
