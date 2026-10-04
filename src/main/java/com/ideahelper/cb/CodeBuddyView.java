package com.ideahelper.cb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** CodeBuddy 的文案/格式化工具，供状态栏与工具窗共用。 */
public final class CodeBuddyView {

    private CodeBuddyView() {
    }

    public static String formatNumber(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return "0";
        }
        BigDecimal bd = BigDecimal.valueOf(n).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private static String formatDateTime(long ts) {
        if (ts <= 0) {
            return "-";
        }
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date(ts));
    }

    /** 状态栏文字（不含图标） */
    public static String statusText(CodeBuddyService s) {
        String err = s.getError();
        if ("NO_CREDENTIALS".equals(err)) {
            return "未登录";
        }
        if ("AUTH_EXPIRED".equals(err)) {
            return "登录已过期";
        }
        Models.UsageResult u = s.getUsage();
        if (u != null) {
            return formatNumber(u.remain);
        }
        if (err != null && !err.isEmpty()) {
            return "获取失败";
        }
        return "…";
    }

    /** 状态栏悬停提示（HTML）：只展示总积分、剩余积分、剩余占比与更新时间 */
    public static String tooltipHtml(CodeBuddyService s) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='width:240px'>");

        String err = s.getError();
        if ("NO_CREDENTIALS".equals(err)) {
            sb.append("未找到登录凭据。请在 CodeBuddy 中登录，或在工具窗里手动填写 Access Token。");
            String note = s.getNote();
            if (note != null && !note.isEmpty()) {
                sb.append("<br/><i>").append(escape(note)).append("</i>");
            }
            sb.append("</body></html>");
            return sb.toString();
        }
        if ("AUTH_EXPIRED".equals(err)) {
            sb.append("登录已过期，请重新登录 CodeBuddy，或手动填写 Access Token。");
            sb.append("</body></html>");
            return sb.toString();
        }

        Models.UsageResult u = s.getUsage();
        if (u == null) {
            sb.append(err != null && !err.isEmpty() ? escape(err) : "暂无数据，正在获取…");
            sb.append("</body></html>");
            return sb.toString();
        }

        double pct = u.total > 0 ? (u.remain / u.total) * 100 : 0;
        sb.append("总积分：").append(formatNumber(u.total)).append("<br/>");
        sb.append("剩余积分：<b>").append(formatNumber(u.remain)).append("</b><br/>");
        sb.append("剩余占比：").append(String.format("%.1f", pct)).append("%<br/>");
        sb.append("更新时间：").append(formatDateTime(s.getUpdatedAt()));
        if (err != null && !err.isEmpty()) {
            sb.append("<br/><font color='#c00000'>").append(escape(err)).append("</font>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public static List<Models.Account> visibleAccounts(Models.UsageResult u) {
        java.util.List<Models.Account> list = new java.util.ArrayList<>();
        for (Models.Account a : u.accounts) {
            if (a.cycleRemain > 0) {
                list.add(a);
            }
        }
        list.sort((x, y) -> Long.compare(parseExpiry(x.endTime), parseExpiry(y.endTime)));
        return list;
    }

    private static long parseExpiry(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) {
            return Long.MAX_VALUE;
        }
        String normalized = s.contains("T") ? s : s.replace(' ', 'T');
        try {
            return java.time.LocalDateTime.parse(normalized).atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Throwable t) {
            return Long.MAX_VALUE;
        }
    }

    public static String checkinText(CodeBuddyService s) {
        if (!CodeBuddySettings.getInstance().isAutoCheckin()) {
            return "未开启";
        }
        Models.CheckinResult c = s.getCheckin();
        if (c == null) {
            return "未知";
        }
        switch (c.state) {
            case CLAIMED:
                return c.credit != null && c.credit > 0
                        ? "已签到 (+" + formatNumber(c.credit) + ")" : "已签到";
            case UNCLAIMED:
                return "未签到";
            default:
                return c.error != null ? "错误 (" + c.error + ")" : "错误";
        }
    }

    public static String buddyText(CodeBuddyService s) {
        Models.BuddyStatus b = s.getBuddy();
        if (b == null) {
            return "未知";
        }
        if ("traveling".equals(b.state)) {
            long now = b.serverNow != null ? b.serverNow : System.currentTimeMillis() / 1000;
            long remain = Math.max(0, (b.arriveAt != null ? b.arriveAt : 0) - now);
            long hh = remain / 3600;
            long mm = (remain % 3600) / 60;
            long ss = remain % 60;
            return String.format("旅行中 倒计时 %02d:%02d:%02d", hh, mm, ss);
        }
        if (b.dailyLimitReached) {
            if (b.claimCredit != null && b.claimCredit > 0) {
                return "今日已完成 · 领取 +" + formatNumber(b.claimCredit);
            }
            return "今日已完成";
        }
        StringBuilder sb = new StringBuilder();
        if (b.claimCredit != null) {
            sb.append(b.claimCredit > 0 ? "已领 +" + formatNumber(b.claimCredit) : "无可领取");
        } else {
            sb.append("可领取");
        }
        if (b.departHours != null) {
            sb.append(" · 已出发 ").append(formatNumber(b.departHours)).append("h");
        } else {
            sb.append(" · 可出发");
        }
        if (b.error != null && !b.error.isEmpty()) {
            sb.append(" · ").append(b.error);
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
