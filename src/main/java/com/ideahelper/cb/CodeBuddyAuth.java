package com.ideahelper.cb;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.ideahelper.cb.Models.AuthMode;
import com.ideahelper.cb.Models.AuthState;

/**
 * CodeBuddy 登录态解析。
 *
 * <p>数据来源：<b>IntelliJ 系 IDE 中 CodeBuddy 插件（coding-copilot-jetbrains）</b>保存的登录态，
 * 即 {@code <IDE 配置目录>/options/secret-storage.xml}（详见 {@link SecretStorageLocator}）。
 * 取其中的 {@code auth.accessToken}（JWT）作为 {@code Authorization: Bearer} 调用计费接口。</p>
 *
 * <p><b>带内存缓存</b>：常规刷新（定时刷新、状态栏点击、工具窗内其它操作）直接用缓存，
 * 避免每次都读文件、解析 XML；只有工具窗里的「刷新」按钮会以 {@code force = true} 调用，
 * 强制重新读取，从而支持在 CodeBuddy 中切换账号后立即生效。
 * 另外任何接口返回 401/403 时也会强制重读一次并重试。</p>
 */
public final class CodeBuddyAuth {

    /** 缓存有效期：到期后即使不强制也会重新读取一次 */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private static volatile AuthState cache;
    private static volatile long cacheAt;

    private CodeBuddyAuth() {
    }

    public static AuthState resolve(CodeBuddySettings settings) {
        return resolve(settings, false);
    }

    /**
     * @param force true 时忽略缓存，重新读取登录态
     */
    public static AuthState resolve(CodeBuddySettings settings, boolean force) {
        long now = System.currentTimeMillis();
        AuthState cached = cache;
        if (!force && cached != null && now - cacheAt < CACHE_TTL_MS) {
            return cached;
        }
        AuthState state = readState(settings, now);
        cache = state;
        cacheAt = now;
        return state;
    }

    /** 清空缓存（下次调用将重新读取登录态） */
    public static void invalidate() {
        cache = null;
        cacheAt = 0;
    }

    private static AuthState readState(CodeBuddySettings settings, long now) {
        String manual = settings.getAccessToken();

        SecretStorageLocator.Result found = SecretStorageLocator.find(settings.getStateDbPath());
        if (found != null && found.token != null) {
            Long exp = found.expiresAt != null ? found.expiresAt : jwtExpiry(found.token);
            if (exp == null || exp > now) {
                return new AuthState(AuthMode.TOKEN_AUTO, found.token, exp, null);
            }
            // 自动读取到的 token 已过期，再看有没有可用的手动 token
            if (!manual.isEmpty()) {
                Long manualExp = jwtExpiry(manual);
                if (manualExp == null || manualExp > now) {
                    return new AuthState(AuthMode.TOKEN_MANUAL, manual, manualExp,
                            "自动读取到的 token 已过期");
                }
            }
            return new AuthState(AuthMode.NONE, null, null, "登录态已过期，请在 CodeBuddy 中重新登录");
        }

        String note = settings.getStateDbPath().isEmpty()
                ? "未在 IntelliJ 配置目录中找到 CodeBuddy 登录态（options/secret-storage.xml）"
                : "指定的位置未找到 CodeBuddy 登录态：" + settings.getStateDbPath();

        if (!manual.isEmpty()) {
            Long exp = jwtExpiry(manual);
            if (exp == null || exp > now) {
                return new AuthState(AuthMode.TOKEN_MANUAL, manual, exp, note);
            }
            return new AuthState(AuthMode.NONE, null, null, "手动填写的 token 已过期");
        }
        return new AuthState(AuthMode.NONE, null, null, note);
    }

    /** 解析 JWT 的 exp（毫秒时间戳） */
    public static Long jwtExpiry(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            com.google.gson.JsonObject payload = com.ideahelper.util.Json
                    .parseObject(new String(decoded, StandardCharsets.UTF_8));
            com.google.gson.JsonElement exp = payload == null ? null : payload.get("exp");
            if (exp != null && exp.isJsonPrimitive() && exp.getAsJsonPrimitive().isNumber()) {
                return exp.getAsLong() * 1000L;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }
}
