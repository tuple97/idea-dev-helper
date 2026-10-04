package com.ideahelper.cb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonObject;
import com.ideahelper.util.Json;

/**
 * 定位并读取 IntelliJ 系 IDE 中 CodeBuddy 插件保存的登录态。
 *
 * <p>IDEA 版 CodeBuddy（`coding-copilot-jetbrains`）把 VS Code 的 SecretStorage 模拟成
 * IntelliJ 的 {@code PersistentStateComponent}，落盘为纯 XML：</p>
 *
 * <pre>
 * &lt;component name="SecretStorage"&gt;
 *   &lt;Scores&gt;
 *     &lt;Entry key="Tencent-Cloud.coding-copilot.new.accessToken" value="{JSON}" /&gt;
 *   &lt;/Scores&gt;
 * &lt;/component&gt;
 * </pre>
 *
 * <p>文件位置：{@code <IDE 配置目录>/options/secret-storage.xml}，其中 Windows 下配置目录为
 * {@code %APPDATA%\JetBrains\<IDE><版本>}，macOS 为 {@code ~/Library/Application Support/JetBrains/<...>}，
 * Linux 为 {@code ~/.config/JetBrains/<...>}。value 中的 JSON 取 {@code auth.accessToken} 即 JWT。</p>
 *
 * <p>本类刻意不依赖任何 IntelliJ API，便于独立测试。</p>
 */
public final class SecretStorageLocator {

    private static final String SECRET_FILE = "secret-storage.xml";

    /** 候选键名（按优先级）。同一份 SecretStorage 里通常只有一个存在。 */
    private static final String[] KEYS = {
            "Tencent-Cloud.coding-copilot.new.accessToken",
            "Tencent-Cloud.coding-copilot.accessToken",
            "planning-genie.new.accessTokencn",
            "planning-genie.new.accessToken"
    };

    private SecretStorageLocator() {
    }

    /** 读取结果 */
    public static final class Result {
        public final String token;
        public final Long expiresAt;
        public final String file;

        Result(String token, Long expiresAt, String file) {
            this.token = token;
            this.expiresAt = expiresAt;
            this.file = file;
        }
    }

    /** 按优先级依次尝试候选文件，返回第一个读出 token 的结果 */
    public static Result find(String overridePath) {
        for (Path file : candidateFiles(overridePath)) {
            Result r = readFile(file);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /** 候选的 secret-storage.xml 列表 */
    public static List<Path> candidateFiles(String overridePath) {
        List<Path> files = new ArrayList<>();
        if (overridePath != null && !overridePath.trim().isEmpty()) {
            Path normalized = normalizeOverride(overridePath);
            if (normalized != null) {
                files.add(normalized);
            }
            return files;
        }
        for (Path dir : configDirs()) {
            files.add(dir.resolve("options").resolve(SECRET_FILE));
        }
        return files;
    }

    /**
     * 归一化用户指定的位置：允许填 {@code secret-storage.xml} 文件本身、
     * {@code options} 目录、或 IDE 的配置目录。
     */
    public static Path normalizeOverride(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        cleaned = cleaned.replaceFirst("(?i)^file:/{2,}", "");
        Path p = Paths.get(cleaned);
        String name = p.getFileName() == null ? "" : p.getFileName().toString();
        if (name.equalsIgnoreCase(SECRET_FILE)) {
            return p;
        }
        if (name.equalsIgnoreCase("options")) {
            return p.resolve(SECRET_FILE);
        }
        // 视作 IDE 配置目录
        return p.resolve("options").resolve(SECRET_FILE);
    }

    /** 所有可能的 IDE 配置目录：当前 IDE 最前，其余按最近修改时间倒序 */
    public static List<Path> configDirs() {
        List<Path> dirs = new ArrayList<>();
        Path current = currentConfigDir();
        if (current != null) {
            dirs.add(current);
        }
        Path root = jetBrainsRoot();
        if (root != null && Files.isDirectory(root)) {
            List<Path> others = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        others.add(p);
                    }
                }
            } catch (IOException ignored) {
                // ignore
            }
            others.sort((a, b) -> Long.compare(lastModified(b), lastModified(a)));
            for (Path p : others) {
                if (!dirs.contains(p)) {
                    dirs.add(p);
                }
            }
        }
        return dirs;
    }

    private static Path currentConfigDir() {
        // IDE 启动时会设置 idea.config.path / idea.paths.selector
        String configPath = System.getProperty("idea.config.path");
        if (configPath != null && !configPath.isEmpty()) {
            Path p = Paths.get(configPath);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        String selector = System.getProperty("idea.paths.selector");
        Path root = jetBrainsRoot();
        if (selector != null && !selector.isEmpty() && root != null) {
            Path p = root.resolve(selector);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    private static Path jetBrainsRoot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) {
                appData = Paths.get(home, "AppData", "Roaming").toString();
            }
            return Paths.get(appData, "JetBrains");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "JetBrains");
        }
        return Paths.get(home, ".config", "JetBrains");
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** 读取单个文件，返回 token 与过期时间；未命中返回 null */
    public static Result readFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        String xml;
        try {
            xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
        String value = findEntryValue(xml);
        if (value == null) {
            return null;
        }
        String token = extractToken(value);
        if (token == null) {
            return null;
        }
        return new Result(token, extractExpiresAt(value), file.toString());
    }

    /** 从 XML 文本中按键优先级取出 Entry 的 value */
    public static String findEntryValue(String xml) {
        NodeList entries = parseEntries(xml);
        if (entries == null) {
            return null;
        }
        for (String key : KEYS) {
            for (int i = 0; i < entries.getLength(); i++) {
                Element el = (Element) entries.item(i);
                String k = el.getAttribute("key");
                if (k != null && k.contains(key)) {
                    String v = el.getAttribute("value");
                    if (v != null && !v.isEmpty()) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private static NodeList parseEntries(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 关闭外部实体与 DTD，避免 XXE
            setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new java.io.StringReader(xml)));
            return doc.getElementsByTagName("Entry");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (Throwable ignored) {
            // 某些实现不支持该特性，忽略
        }
    }

    /** 从存储的 JSON 中提取 JWT：优先 auth.accessToken，其次 token，最后 accessToken */
    public static String extractToken(String jsonText) {
        JsonObject o = Json.parseObject(jsonText);
        if (o != null) {
            JsonObject auth = Json.obj(o, "auth");
            String t = normalizeToken(Json.str(auth, "accessToken"));
            if (t != null) {
                return t;
            }
            t = normalizeToken(Json.str(o, "token"));
            if (t != null) {
                return t;
            }
            t = normalizeToken(Json.str(o, "accessToken"));
            if (t != null) {
                return t;
            }
        }
        // JSON 不完整时正则兜底
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"auth\"\\s*:\\s*\\{[^{}]*?\"accessToken\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(jsonText);
        if (m.find()) {
            return normalizeToken(m.group(1));
        }
        m = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonText);
        return m.find() ? normalizeToken(m.group(1)) : null;
    }

    /**
     * 规范 token：IDEA 插件里部分字段形如 {@code <uid>+<JWT>}，
     * 这里从 "eyJ" 处截断，只保留 JWT 本体。
     */
    static String normalizeToken(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String v = raw.trim();
        int idx = v.indexOf("eyJ");
        if (idx > 0) {
            return v.substring(idx);
        }
        return v;
    }

    /** 从存储的 JSON 中提取过期时间（毫秒） */
    public static Long extractExpiresAt(String jsonText) {
        JsonObject o = Json.parseObject(jsonText);
        if (o == null) {
            return null;
        }
        Long v = longOf(Json.obj(o, "auth"), "expiresAt");
        if (v != null) {
            return v;
        }
        return longOf(o, "expiresAt");
    }

    private static Long longOf(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            long v = o.get(key).getAsLong();
            return v > 0 ? v : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
