package com.ideahelper.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 对 IntelliJ 平台内置 Gson 的一层薄封装，提供空安全的取值方法。
 * 不引入任何第三方依赖，Gson 由 IntelliJ 平台自带。
 */
public final class Json {

    public static final Gson GSON = new Gson();

    private Json() {
    }

    public static JsonObject parseObject(String text) {
        if (text == null) {
            return null;
        }
        try {
            JsonElement e = JsonParser.parseString(text);
            return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static JsonObject obj(JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement e = parent.get(key);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
    }

    public static JsonArray array(JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement e = parent.get(key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : null;
    }

    public static String str(JsonObject parent, String key, String def) {
        if (parent == null) {
            return def;
        }
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsString();
        } catch (Throwable t) {
            return def;
        }
    }

    public static String str(JsonObject parent, String key) {
        return str(parent, key, null);
    }

    public static double dbl(JsonObject parent, String key, double def) {
        if (parent == null) {
            return def;
        }
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsDouble();
        } catch (Throwable t) {
            try {
                return Double.parseDouble(e.getAsString());
            } catch (Throwable t2) {
                return def;
            }
        }
    }

    public static long lng(JsonObject parent, String key, long def) {
        if (parent == null) {
            return def;
        }
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsLong();
        } catch (Throwable t) {
            return def;
        }
    }

    public static Boolean bool(JsonObject parent, String key, Boolean def) {
        if (parent == null) {
            return def;
        }
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsBoolean();
        } catch (Throwable t) {
            return def;
        }
    }
}
