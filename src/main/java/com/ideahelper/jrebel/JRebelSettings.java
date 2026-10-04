package com.ideahelper.jrebel;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/** JRebel 激活服务配置，持久化在 {@code jrebel-helper.xml}。 */
@Service(Service.Level.APP)
@State(name = "JRebelHelperSettings", storages = @Storage("jrebel-helper.xml"))
public final class JRebelSettings implements PersistentStateComponent<JRebelSettings.State> {

    public static final class State {
        public int port = 12345;
        /** 激活地址的协议：http / https */
        public String schema = "http";
        /** 导出主机（首页展示用），留空则用请求 Host */
        public String exportHost = "";
        /** 离线天数（1-180） */
        public int offlineDays = 30;
        /** 0: 自动, 1: 强制离线, 2: 强制在线, 3: oldGuid */
        public int workMode = 0;
        /** IDE 启动后自动开启服务 */
        public boolean autoStart = false;
    }

    private State state = new State();

    public static JRebelSettings getInstance() {
        return ApplicationManager.getApplication().getService(JRebelSettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State s) {
        this.state = s;
    }

    public int getPort() {
        return state.port;
    }

    public String getSchema() {
        return state.schema == null || state.schema.isEmpty() ? "http" : state.schema;
    }

    public String getExportHost() {
        return state.exportHost == null ? "" : state.exportHost.trim();
    }

    public int getOfflineDays() {
        return state.offlineDays;
    }

    public int getWorkMode() {
        return state.workMode;
    }

    public boolean isAutoStart() {
        return state.autoStart;
    }

    public void update(int port, String schema, String exportHost, int offlineDays, int workMode,
                       boolean autoStart) {
        state.port = port;
        state.schema = schema;
        state.exportHost = exportHost;
        state.offlineDays = offlineDays;
        state.workMode = workMode;
        state.autoStart = autoStart;
    }
}
