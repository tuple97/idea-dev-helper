package com.ideahelper.cb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ideahelper.util.Json;

/** CodeBuddy 计费 / 签到 / 喵喵接口调用。 */
public final class CodeBuddyApi {

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36";

    private static final String[] PACKAGE_CODES = {
            "TCACA_code_008_cfWoLwvjU4",
            "TCACA_code_009_0XmEQc2xOf",
            "TCACA_code_038_OhvqZtiPKr",
            "TCACA_code_007_nzdH5h4Nl0",
            "TCACA_code_028_NtpWi0jzXs",
            "TCACA_code_029_6wCGEWquYy",
            "TCACA_code_030_BjSt89qTvr"
    };

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CodeBuddyApi() {
    }

    private static String ua(CodeBuddySettings s) {
        String v = s.getUserAgent();
        return v.isEmpty() ? DEFAULT_UA : v;
    }

    /** 带鉴权的请求：统一注入 Authorization，401/403 时清缓存重试一次 */
    private static HttpResponse<String> authFetch(CodeBuddySettings s, Models.AuthState auth,
                                                  String url, String method, String body,
                                                  Map<String, String> extraHeaders) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            // 重试时强制重新读取登录态：账号切换 / token 换发后即可自愈
            Models.AuthState a = (attempt == 0) ? auth : CodeBuddyAuth.resolve(s, true);
            if (a.token == null) {
                throw new Models.ApiException("NO_CREDENTIALS", true);
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("authorization", "Bearer " + a.token)
                    .header("user-agent", ua(s));
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    builder.header(e.getKey(), e.getValue());
                }
            }
            if ("POST".equalsIgnoreCase(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body,
                        StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            HttpResponse<String> resp = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                // 下一次循环会重新实时读取登录态，账号切换 / token 换发后即可自愈
                continue;
            }
            return resp;
        }
        throw new Models.ApiException("AUTH_EXPIRED", true);
    }

    private static Map<String, String> baseHeaders(String apiBase, String referer) {
        Map<String, String> h = new HashMap<>();
        h.put("accept", "application/json, text/plain, */*");
        h.put("content-type", "application/json");
        h.put("origin", apiBase);
        h.put("referer", apiBase + referer);
        h.put("x-client-platform", "web");
        return h;
    }

    private static double parseDouble(String v) {
        if (v == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (Throwable t) {
            return 0d;
        }
    }

    // ============================================================
    // 用量
    // ============================================================
    public static Models.UsageResult fetchUsage(CodeBuddySettings s, Models.AuthState auth)
            throws Exception {
        String apiBase = s.getApiBase();

        JsonObject body = new JsonObject();
        body.addProperty("PageNumber", 1);
        body.addProperty("PageSize", 200);
        body.addProperty("ProductCode", "p_tcaca");
        JsonArray status = new JsonArray();
        status.add(0);
        status.add(3);
        body.add("Status", status);
        body.addProperty("OnlyValidPeriod", true);
        JsonArray codes = new JsonArray();
        for (String c : PACKAGE_CODES) {
            codes.add(c);
        }
        body.add("PackageCodes", codes);
        body.addProperty("NeedInUsage", true);

        HttpResponse<String> resp = authFetch(s, auth,
                apiBase + "/billing/meter/get-user-resource", "POST", body.toString(),
                baseHeaders(apiBase, "/profile/plans-usage"));

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new Models.ApiException("HTTP_" + resp.statusCode(), false);
        }

        Models.UsageResult result = new Models.UsageResult();
        result.accounts = new ArrayList<>();
        JsonObject json = Json.parseObject(resp.body());
        JsonObject data = Json.obj(json, "data");
        JsonObject response = Json.obj(data, "Response");
        JsonObject innerData = Json.obj(response, "Data");
        JsonArray accounts = Json.array(innerData, "Accounts");
        if (accounts == null) {
            return result;
        }
        for (JsonElement el : accounts) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject a = el.getAsJsonObject();
            Models.Account acc = new Models.Account();
            acc.name = Json.str(a, "PackageName", "-");
            acc.endTime = Json.str(a, "CycleEndTime", "-");

            String cycleRemain = Json.str(a, "CycleCapacityRemainPrecise");
            String cycleSize = Json.str(a, "CycleCapacitySizePrecise");
            double accRemain;
            double accSize;
            if (cycleRemain != null && cycleSize != null) {
                acc.hasCycle = true;
                accRemain = parseDouble(cycleRemain);
                accSize = parseDouble(cycleSize);
            } else {
                accRemain = parseDouble(Json.str(a, "CapacityRemainPrecise"));
                accSize = parseDouble(Json.str(a, "CapacitySizePrecise"));
            }
            acc.cycleRemain = accRemain;
            acc.cycleSize = accSize;
            result.remain += accRemain;
            if (accRemain > 0) {
                result.total += accSize;
            }
            result.accounts.add(acc);
        }
        return result;
    }

    // ============================================================
    // 签到
    // ============================================================
    public static Models.CheckinResult fetchCheckinStatus(CodeBuddySettings s, Models.AuthState auth)
            throws Models.ApiException {
        String apiBase = s.getApiBase();
        try {
            HttpResponse<String> resp = authFetch(s, auth,
                    apiBase + "/billing/meter/checkin-status", "POST", "{}",
                    baseHeaders(apiBase, "/profile/plans-usage"));
            JsonObject json = Json.parseObject(resp.body());
            if (codeOf(json) == 10001) {
                return claimed();
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return unknown("HTTP_" + resp.statusCode());
            }
            Boolean checked = Json.bool(Json.obj(json, "data"), "today_checked_in", Boolean.FALSE);
            return Boolean.TRUE.equals(checked) ? claimed() : unclaimed();
        } catch (Models.ApiException e) {
            if (e.authExpired) {
                throw e;
            }
            return unknown(e.getMessage());
        } catch (Exception e) {
            return unknown(String.valueOf(e.getMessage()));
        }
    }

    public static Models.CheckinResult doCheckin(CodeBuddySettings s, Models.AuthState auth)
            throws Models.ApiException {
        String apiBase = s.getApiBase();
        try {
            HttpResponse<String> resp = authFetch(s, auth,
                    apiBase + "/billing/meter/daily-checkin", "POST", "{}",
                    baseHeaders(apiBase, "/profile/plans-usage"));
            JsonObject json = Json.parseObject(resp.body());
            int code = codeOf(json);
            if (code == 10001) {
                return claimed();
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return unknown("HTTP_" + resp.statusCode());
            }
            if (code == 0) {
                Models.CheckinResult r = claimed();
                JsonObject d = Json.obj(json, "data");
                if (d != null && d.has("credit") && !d.get("credit").isJsonNull()) {
                    r.credit = d.get("credit").getAsDouble();
                }
                r.freshlyClaimed = true;
                return r;
            }
            Models.CheckinResult st = fetchCheckinStatus(s, auth);
            if (st.state == Models.CheckinResult.State.CLAIMED) {
                return st;
            }
            return unknown("code=" + code + " msg=" + Json.str(json, "msg", ""));
        } catch (Models.ApiException e) {
            if (e.authExpired) {
                throw e;
            }
            return unknown(e.getMessage());
        } catch (Exception e) {
            return unknown(String.valueOf(e.getMessage()));
        }
    }

    private static int codeOf(JsonObject json) {
        if (json == null || !json.has("code") || json.get("code").isJsonNull()) {
            return Integer.MIN_VALUE;
        }
        try {
            return json.get("code").getAsInt();
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    private static Models.CheckinResult claimed() {
        Models.CheckinResult r = new Models.CheckinResult();
        r.state = Models.CheckinResult.State.CLAIMED;
        return r;
    }

    private static Models.CheckinResult unclaimed() {
        Models.CheckinResult r = new Models.CheckinResult();
        r.state = Models.CheckinResult.State.UNCLAIMED;
        return r;
    }

    private static Models.CheckinResult unknown(String error) {
        Models.CheckinResult r = new Models.CheckinResult();
        r.state = Models.CheckinResult.State.UNKNOWN;
        r.error = error;
        return r;
    }

    // ============================================================
    // 喵喵旅行
    // ============================================================
    public static JsonObject buddyCall(CodeBuddySettings s, Models.AuthState auth,
                                       String sub, String method, String body) throws Exception {
        String apiBase = s.getApiBase();
        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json, text/plain, */*");
        headers.put("content-type", "application/json");
        headers.put("origin", apiBase);
        headers.put("referer", apiBase + "/profile/growth-center");
        headers.put("x-client-platform", "web");
        HttpResponse<String> resp = authFetch(s, auth,
                apiBase + "/activity/growth/buddy/travel/" + sub, method, body, headers);
        return Json.parseObject(resp.body());
    }

    /** 解析旅行时长（小时）：优先 duration_hours，其次由到达/出发差值推算 */
    public static Double resolveTravelHours(Models.BuddyStatus st) {
        if (st == null) {
            return null;
        }
        if (st.durationHours != null && st.durationHours > 0) {
            return st.durationHours;
        }
        if (st.arriveAt != null && st.departAt != null && st.arriveAt > st.departAt) {
            double h = (st.arriveAt - st.departAt) / 3600.0;
            return h > 0 ? Math.round(h * 10) / 10.0 : null;
        }
        return null;
    }

    public static List<Models.Account> emptyAccounts() {
        return new ArrayList<>();
    }
}
