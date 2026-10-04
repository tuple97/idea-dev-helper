package com.ideahelper.cb;

import java.util.List;

/** CodeBuddy 相关的数据模型集合。 */
public final class Models {

    private Models() {
    }

    public enum AuthMode {
        /** 自动读取到 CodeBuddy 登录态 */
        TOKEN_AUTO,
        /** 用户手动填写的 accessToken */
        TOKEN_MANUAL,
        /** 无任何可用凭据 */
        NONE
    }

    /** 鉴权状态 */
    public static final class AuthState {
        public final AuthMode mode;
        public final String token;
        /** JWT 过期时间（毫秒），可能为 null */
        public final Long expiresAt;
        /** 自动读取失败时的原因（用于提示） */
        public final String note;

        public AuthState(AuthMode mode, String token, Long expiresAt, String note) {
            this.mode = mode;
            this.token = token;
            this.expiresAt = expiresAt;
            this.note = note;
        }
    }

    /** 单个套餐/资源包 */
    public static final class Account {
        public String name = "-";
        /** 本周期剩余（优先） */
        public double cycleRemain;
        /** 本周期总量 */
        public double cycleSize;
        /** 到期时间字符串 */
        public String endTime = "-";
        /** 是否有周期字段 */
        public boolean hasCycle;
    }

    /** 用量结果 */
    public static final class UsageResult {
        public double remain;
        public double total;
        public List<Account> accounts;
    }

    /** 签到结果 */
    public static final class CheckinResult {
        public enum State {
            CLAIMED, UNCLAIMED, UNKNOWN
        }

        public State state = State.UNKNOWN;
        /** 签到获得的积分（可能为 null） */
        public Double credit;
        /** 本次刷新新签到成功 */
        public boolean freshlyClaimed;
        /** 失败原因 */
        public String error;
    }

    /** 喵喵旅行状态 */
    public static final class BuddyStatus {
        public String state;
        public Long departAt;
        public Long arriveAt;
        public Long serverNow;
        public Double durationHours;
        public Double rewardCredit;
        public boolean dailyLimitReached;
        public String locationName;

        /** 本次刷新领取到账积分（null 表示未领取） */
        public Double claimCredit;
        /** 本次刷新派出的旅行时长 */
        public Double departHours;
        /** 操作失败原因 */
        public String error;
    }

    /** 接口调用异常；authExpired=true 表示需要重新登录 */
    public static final class ApiException extends Exception {
        public final boolean authExpired;

        public ApiException(String message, boolean authExpired) {
            super(message);
            this.authExpired = authExpired;
        }
    }
}
