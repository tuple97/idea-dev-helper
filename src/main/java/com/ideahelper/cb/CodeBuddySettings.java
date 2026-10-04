package com.ideahelper.cb;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/** CodeBuddy 相关配置，持久化在 IDE 配置目录下的 {@code codebuddy-helper.xml}。 */
@Service(Service.Level.APP)
@State(name = "CodeBuddyHelperSettings", storages = @Storage("codebuddy-helper.xml"))
public final class CodeBuddySettings implements PersistentStateComponent<CodeBuddySettings.State> {

    /** IDEA 版 CodeBuddy 的默认接口域名（与 WorkBuddy 同后台，两者均可） */
    public static final String DEFAULT_API_BASE = "https://www.codebuddy.cn";

    public static final class State {
        public String apiBase = DEFAULT_API_BASE;
        /** 登录态位置（IDE 配置目录 / options 目录 / secret-storage.xml）；留空=自动识别 */
        public String stateDbPath = "";
        /** 手动填写的 accessToken（自动读取失败时兜底） */
        public String accessToken = "";
        /** 请求 UA；留空用内置默认值 */
        public String userAgent = "";
        public int refreshIntervalMinutes = 30;
        public boolean autoCheckin = true;
        public boolean buddyTravel = true;
    }

    private State state = new State();

    public static CodeBuddySettings getInstance() {
        return ApplicationManager.getApplication().getService(CodeBuddySettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State s) {
        this.state = s;
    }

    public String getApiBase() {
        String v = state.apiBase;
        if (v == null || v.trim().isEmpty()) {
            v = DEFAULT_API_BASE;
        }
        v = v.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    public String getStateDbPath() {
        return state.stateDbPath == null ? "" : state.stateDbPath.trim();
    }

    public String getAccessToken() {
        return state.accessToken == null ? "" : state.accessToken.trim();
    }

    public String getUserAgent() {
        return state.userAgent == null ? "" : state.userAgent.trim();
    }

    public int getRefreshIntervalMinutes() {
        return state.refreshIntervalMinutes;
    }

    public boolean isAutoCheckin() {
        return state.autoCheckin;
    }

    public boolean isBuddyTravel() {
        return state.buddyTravel;
    }

    public void update(String apiBase, String stateDbPath, String accessToken, String userAgent,
                       int refreshIntervalMinutes, boolean autoCheckin, boolean buddyTravel) {
        state.apiBase = apiBase;
        state.stateDbPath = stateDbPath;
        state.accessToken = accessToken;
        state.userAgent = userAgent;
        state.refreshIntervalMinutes = refreshIntervalMinutes;
        state.autoCheckin = autoCheckin;
        state.buddyTravel = buddyTravel;
    }

    public void setAccessToken(String token) {
        state.accessToken = token;
    }
}
