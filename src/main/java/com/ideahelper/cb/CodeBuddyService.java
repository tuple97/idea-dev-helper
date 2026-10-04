package com.ideahelper.cb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ideahelper.util.Json;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * CodeBuddy 核心服务：负责拉取用量、自动签到、喵喵旅行，并缓存最近一次结果。
 * 同时管理一个定时刷新任务，并向监听的 UI（状态栏 / 工具窗）广播更新。
 */
@Service(Service.Level.APP)
public final class CodeBuddyService {

    public interface Listener {
        void onCodeBuddyUpdate();
    }

    private static final String ERR_NO_CREDENTIALS = "NO_CREDENTIALS";
    private static final String ERR_AUTH_EXPIRED = "AUTH_EXPIRED";

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object updateLock = new Object();
    private final Object timerLock = new Object();

    private volatile boolean updating;
    /** 刷新进行中又收到刷新请求时标记，结束后自动再跑一次，保证「点刷新必生效」 */
    private volatile boolean pendingRefresh;
    /** 被延后那次刷新是否需要强制重读登录态 */
    private volatile boolean pendingForceAuth;
    private volatile Models.AuthState auth;
    private volatile Models.UsageResult usage;
    private volatile Models.CheckinResult checkin;
    private volatile Models.BuddyStatus buddy;
    private volatile String error;
    private volatile String note;
    private volatile long updatedAt;

    private ScheduledFuture<?> timer;
    private boolean timerStarted;

    // 喵喵「一趟旅行一次」等守卫
    private String lastBuddyClaimKey = "";
    private String lastBuddyAutoDate = "";
    private boolean lastBuddyWasTraveling;

    public static CodeBuddyService getInstance() {
        return ApplicationManager.getApplication().getService(CodeBuddyService.class);
    }

    // ============================================================
    // 监听与定时
    // ============================================================
    public void addListener(Listener listener) {
        listeners.add(listener);
        startTimerIfNeeded();
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void startTimerIfNeeded() {
        synchronized (timerLock) {
            if (timerStarted) {
                return;
            }
            timerStarted = true;
        }
        restartTimer();
    }

    /** 配置变更后重新排布定时任务 */
    public void restartTimer() {
        synchronized (timerLock) {
            if (timer != null) {
                timer.cancel(false);
                timer = null;
            }
            int mins = CodeBuddySettings.getInstance().getRefreshIntervalMinutes();
            if (mins > 0) {
                timer = AppExecutorUtil.getAppScheduledExecutorService()
                        .scheduleWithFixedDelay(() -> refreshSafely(false), mins, mins, TimeUnit.MINUTES);
            }
        }
    }

    private void fireUpdate() {
        for (Listener l : listeners) {
            try {
                l.onCodeBuddyUpdate();
            } catch (Throwable ignored) {
                // 单个监听器异常不影响其它
            }
        }
    }

    // ============================================================
    // 刷新
    // ============================================================
    /** 常规刷新：登录态走缓存 */
    public void refreshAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> refreshSafely(false));
    }

    /**
     * 强制重新读取登录态后再刷新。
     * 供工具窗里的「刷新」按钮使用：在 CodeBuddy 中切换账号后点一下即可立即生效。
     */
    public void refreshWithFreshAuthAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> refreshSafely(true));
    }

    private void refreshSafely(boolean forceAuth) {
        try {
            refresh(forceAuth);
        } catch (Throwable ignored) {
            // refresh() 内部已处理异常
        }
    }

    public void refresh() {
        refresh(false);
    }

    /**
     * @param forceAuth true 时忽略登录态缓存，重新读取 {@code secret-storage.xml}
     */
    public void refresh(boolean forceAuth) {
        synchronized (updateLock) {
            if (updating) {
                pendingRefresh = true;
                if (forceAuth) {
                    pendingForceAuth = true;
                }
                return;
            }
            updating = true;
        }
        try {
            CodeBuddySettings s = CodeBuddySettings.getInstance();
            Models.AuthState a = CodeBuddyAuth.resolve(s, forceAuth);
            this.auth = a;
            if (a.token == null) {
                this.error = ERR_NO_CREDENTIALS;
                this.note = a.note;
                return;
            }
            try {
                Models.CheckinResult ci = null;
                if (s.isAutoCheckin()) {
                    ci = ensureCheckin(s, a);
                }
                Models.BuddyStatus bs = null;
                if (s.isBuddyTravel()) {
                    bs = ensureBuddy(s, a);
                }
                Models.UsageResult ur = CodeBuddyApi.fetchUsage(s, a);
                this.checkin = ci;
                this.buddy = bs;
                this.usage = ur;
                this.error = null;
                this.note = null;
                this.updatedAt = System.currentTimeMillis();
            } catch (Models.ApiException e) {
                if (e.authExpired) {
                    this.error = ERR_NO_CREDENTIALS.equals(e.getMessage())
                            ? ERR_NO_CREDENTIALS : ERR_AUTH_EXPIRED;
                } else {
                    this.error = e.getMessage();
                }
            } catch (Throwable t) {
                this.error = String.valueOf(t.getMessage() == null ? t : t.getMessage());
            }
        } finally {
            updating = false;
            fireUpdate();
            boolean again;
            boolean againForce;
            synchronized (updateLock) {
                again = pendingRefresh;
                againForce = pendingForceAuth;
                pendingRefresh = false;
                pendingForceAuth = false;
            }
            if (again) {
                if (againForce) {
                    refreshWithFreshAuthAsync();
                } else {
                    refreshAsync();
                }
            }
        }
    }

    private Models.CheckinResult ensureCheckin(CodeBuddySettings s, Models.AuthState a)
            throws Models.ApiException {
        Models.CheckinResult status = CodeBuddyApi.fetchCheckinStatus(s, a);
        if (status.state == Models.CheckinResult.State.CLAIMED) {
            return status;
        }
        if (status.state == Models.CheckinResult.State.UNCLAIMED) {
            return CodeBuddyApi.doCheckin(s, a);
        }
        return status;
    }

    // ============================================================
    // 手动签到（供工具窗按钮调用）
    // ============================================================
    public Models.CheckinResult manualCheckin() throws Exception {
        CodeBuddySettings s = CodeBuddySettings.getInstance();
        Models.AuthState a = CodeBuddyAuth.resolve(s);
        this.auth = a;
        if (a.token == null) {
            throw new Models.ApiException(ERR_NO_CREDENTIALS, true);
        }
        Models.CheckinResult r = CodeBuddyApi.doCheckin(s, a);
        this.checkin = r;
        if (r.state == Models.CheckinResult.State.CLAIMED && r.freshlyClaimed) {
            // 签到成功后顺手刷新余量
            try {
                this.usage = CodeBuddyApi.fetchUsage(s, a);
                this.updatedAt = System.currentTimeMillis();
                this.error = null;
            } catch (Throwable ignored) {
                // ignore
            }
        }
        fireUpdate();
        return r;
    }

    // ============================================================
    // 喵喵旅行
    // ============================================================
    private Models.BuddyStatus ensureBuddy(CodeBuddySettings s, Models.AuthState a) throws Exception {
        JsonObject json = CodeBuddyApi.buddyCall(s, a, "status", "GET", null);
        if (codeOf(json) != 0) {
            return null;
        }
        Models.BuddyStatus st = parseBuddyStatus(Json.obj(json, "data"));
        if (st == null) {
            return null;
        }
        boolean isTraveling = "traveling".equals(st.state);
        boolean backFromTravel = st.state != null && !isTraveling;
        boolean travelJustEnded = lastBuddyWasTraveling && !isTraveling;
        String travelKey = String.valueOf(st.departAt != null ? st.departAt
                : (st.arriveAt != null ? st.arriveAt : st.state));

        if (backFromTravel && (travelJustEnded || !travelKey.equals(lastBuddyClaimKey))) {
            JsonObject c = CodeBuddyApi.buddyCall(s, a, "claim", "POST", "{}");
            if (codeOf(c) == 0) {
                Double credit = null;
                JsonObject cd = Json.obj(c, "data");
                if (cd != null && cd.has("credit") && !cd.get("credit").isJsonNull()) {
                    credit = cd.get("credit").getAsDouble();
                }
                if (credit == null || credit <= 0) {
                    credit = fetchTravelRewardCredit(s, a, st.departAt);
                }
                st.claimCredit = credit == null ? 0d : credit;
                lastBuddyClaimKey = travelKey;
            } else {
                String msg = Json.str(c, "msg", "");
                if (msg != null && msg.toLowerCase().contains("no unclaimed")) {
                    lastBuddyClaimKey = travelKey;
                } else if (msg != null && !msg.isEmpty()) {
                    st.error = msg;
                }
            }
        }

        boolean canDepart = backFromTravel && !st.dailyLimitReached;
        if (canDepart && !todayStr().equals(lastBuddyAutoDate)) {
            JsonObject dep = CodeBuddyApi.buddyCall(s, a, "depart", "POST", "{\"location_id\":1}");
            if (codeOf(dep) == 0) {
                Models.BuddyStatus st2 = fetchBuddyStatus(s, a);
                if (st2 != null) {
                    st.departHours = CodeBuddyApi.resolveTravelHours(st2);
                    st.state = st2.state;
                    st.arriveAt = st2.arriveAt;
                    st.departAt = st2.departAt;
                    st.serverNow = st2.serverNow;
                } else {
                    st.departHours = 0d;
                }
            }
            lastBuddyAutoDate = todayStr();
        }

        lastBuddyWasTraveling = "traveling".equals(st.state);
        return st;
    }

    private Models.BuddyStatus fetchBuddyStatus(CodeBuddySettings s, Models.AuthState a) {
        try {
            JsonObject json = CodeBuddyApi.buddyCall(s, a, "status", "GET", null);
            if (codeOf(json) != 0) {
                return null;
            }
            return parseBuddyStatus(Json.obj(json, "data"));
        } catch (Throwable t) {
            return null;
        }
    }

    private Double fetchTravelRewardCredit(CodeBuddySettings s, Models.AuthState a, Long departAt) {
        try {
            JsonObject json = CodeBuddyApi.buddyCall(s, a, "records?page=1&page_size=20", "GET", null);
            if (codeOf(json) != 0) {
                return null;
            }
            JsonArray records = Json.array(Json.obj(json, "data"), "records");
            if (records == null || records.size() == 0) {
                return null;
            }
            JsonObject hit = null;
            if (departAt != null) {
                for (JsonElement el : records) {
                    if (el.isJsonObject()) {
                        JsonObject r = el.getAsJsonObject();
                        Long da = optLong(r, "depart_at");
                        if (da != null && da.equals(departAt)) {
                            hit = r;
                            break;
                        }
                    }
                }
            }
            if (hit == null && records.get(0).isJsonObject()) {
                hit = records.get(0).getAsJsonObject();
            }
            if (hit == null) {
                return null;
            }
            return optDouble(hit, "reward_credit");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Models.BuddyStatus parseBuddyStatus(JsonObject d) {
        if (d == null) {
            return null;
        }
        Models.BuddyStatus st = new Models.BuddyStatus();
        st.state = Json.str(d, "state");
        st.departAt = optLong(d, "depart_at");
        st.arriveAt = optLong(d, "arrive_at");
        st.serverNow = optLong(d, "server_now");
        Double dh = optDouble(d, "duration_hours");
        if (dh == null) {
            dh = optDouble(Json.obj(d, "location"), "duration_hours");
        }
        st.durationHours = dh;
        st.rewardCredit = optDouble(d, "reward_credit");
        st.dailyLimitReached = Boolean.TRUE.equals(Json.bool(d, "daily_limit_reached", Boolean.FALSE));
        st.locationName = Json.str(Json.obj(d, "location"), "name");
        return st;
    }

    private static Long optLong(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return o.get(key).getAsLong();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Double optDouble(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return o.get(key).getAsDouble();
        } catch (Throwable t) {
            return null;
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

    private static String todayStr() {
        java.time.LocalDate d = java.time.LocalDate.now();
        return String.format("%04d-%02d-%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    // ============================================================
    // 访问器
    // ============================================================
    public Models.AuthState getAuth() {
        return auth;
    }

    public Models.UsageResult getUsage() {
        return usage;
    }

    public Models.CheckinResult getCheckin() {
        return checkin;
    }

    public Models.BuddyStatus getBuddy() {
        return buddy;
    }

    public String getError() {
        return error;
    }

    public String getNote() {
        return note;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isUpdating() {
        return updating;
    }
}
