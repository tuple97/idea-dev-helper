package com.ideahelper.cb;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

/** 工具窗中的 CodeBuddy 面板：余量展示、签到、刷新与设置。 */
public class CodeBuddyPanel extends JPanel implements Disposable, CodeBuddyService.Listener {

    private final Project project;
    private final CodeBuddyService.Listener listener = this::onCodeBuddyUpdate;

    private final JLabel creditsLabel = new JLabel("-");
    private final JLabel detailLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel authLabel = new JLabel(" ");

    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"套餐", "剩余", "总量", "到期"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(tableModel);

    private final JTextField apiBaseField = new JTextField(26);
    private final JTextField stateDbField = new JTextField(26);
    private final JPasswordField tokenField = new JPasswordField(26);
    private final JTextField intervalField = new JTextField(6);
    private final JCheckBox autoCheckinBox = new JCheckBox("自动签到");
    private final JCheckBox buddyTravelBox = new JCheckBox("喵喵旅行");

    public CodeBuddyPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildSouth(), BorderLayout.SOUTH);

        loadSettingsToUi();
        CodeBuddyService.getInstance().addListener(listener);
        onCodeBuddyUpdate();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("CodeBuddy 剩余积分");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
        creditsLabel.setFont(creditsLabel.getFont().deriveFont(Font.BOLD, creditsLabel.getFont().getSize2D() + 16f));

        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(creditsLabel);
        header.add(Box.createVerticalStrut(2));
        header.add(detailLabel);
        header.add(Box.createVerticalStrut(4));
        header.add(statusLabel);
        header.add(authLabel);
        return header;
    }

    private JComponent buildCenter() {
        table.setFillsViewportHeight(true);
        table.setRowHeight(Math.max(20, table.getRowHeight()));
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new java.awt.Dimension(360, 160));
        return scrollPane;
    }

    private JComponent buildSouth() {
        JPanel south = new JPanel(new BorderLayout(0, 8));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton refreshButton = new JButton("刷新");
        // 工具窗里的刷新：强制重新读取登录态，切换 CodeBuddy 账号后点一下即生效
        refreshButton.addActionListener(e -> CodeBuddyService.getInstance().refreshWithFreshAuthAsync());
        JButton checkinButton = new JButton("签到");
        checkinButton.addActionListener(e -> doCheckin());
        JButton openPageButton = new JButton("打开用量网页");
        openPageButton.addActionListener(e -> BrowserUtil.browse(
                CodeBuddySettings.getInstance().getApiBase() + "/profile/plans-usage"));
        buttons.add(refreshButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(checkinButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(openPageButton);
        buttons.add(Box.createHorizontalGlue());
        south.add(buttons, BorderLayout.NORTH);

        south.add(buildSettings(), BorderLayout.CENTER);
        return south;
    }

    private JComponent buildSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("设置"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;

        int y = 0;
        addRow(panel, c, y++, "接口地址", apiBaseField);
        addRow(panel, c, y++, "登录态位置(可选)", stateDbField);
        addRow(panel, c, y++, "Access Token", tokenField);
        addRow(panel, c, y++, "刷新间隔(分钟)", intervalField);

        JPanel flags = new JPanel();
        flags.setLayout(new BoxLayout(flags, BoxLayout.X_AXIS));
        flags.add(autoCheckinBox);
        flags.add(Box.createHorizontalStrut(12));
        flags.add(buddyTravelBox);
        flags.add(Box.createHorizontalGlue());
        addRow(panel, c, y++, "自动任务", flags);

        JButton saveButton = new JButton("保存设置");
        saveButton.addActionListener(e -> saveSettings());
        JPanel saveRow = new JPanel();
        saveRow.setLayout(new BoxLayout(saveRow, BoxLayout.X_AXIS));
        saveRow.add(saveButton);
        saveRow.add(Box.createHorizontalGlue());
        addRow(panel, c, y, "", saveRow);
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int y, String label, JComponent comp) {
        c.gridx = 0;
        c.gridy = y;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(comp, c);
    }

    // ============================================================
    // 交互
    // ============================================================
    private static final class CheckinOutcome {
        final String message;
        final boolean error;

        CheckinOutcome(String message, boolean error) {
            this.message = message;
            this.error = error;
        }
    }

    private void doCheckin() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final CheckinOutcome outcome = performCheckin();
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                if (outcome.error) {
                    Messages.showErrorDialog(project, outcome.message, "CodeBuddy 签到");
                } else {
                    Messages.showInfoMessage(project, outcome.message, "CodeBuddy 签到");
                }
            });
        });
    }

    private CheckinOutcome performCheckin() {
        try {
            Models.CheckinResult r = CodeBuddyService.getInstance().manualCheckin();
            if (r.state == Models.CheckinResult.State.CLAIMED) {
                if (r.credit != null && r.credit > 0) {
                    return new CheckinOutcome(
                            "签到成功，获得 " + CodeBuddyView.formatNumber(r.credit) + " 积分", false);
                }
                return new CheckinOutcome(r.freshlyClaimed ? "签到成功" : "今天已经签过了", false);
            }
            if (r.state == Models.CheckinResult.State.UNCLAIMED) {
                return new CheckinOutcome("签到未成功，请稍后重试", true);
            }
            return new CheckinOutcome("签到失败：" + (r.error == null ? "未知错误" : r.error), true);
        } catch (Throwable e) {
            return new CheckinOutcome(
                    "签到失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()), true);
        }
    }

    private void saveSettings() {
        int interval;
        try {
            interval = Integer.parseInt(intervalField.getText().trim());
        } catch (Throwable t) {
            interval = 30;
        }
        if (interval < 0) {
            interval = 0;
        }
        CodeBuddySettings s = CodeBuddySettings.getInstance();
        s.update(apiBaseField.getText().trim(), stateDbField.getText().trim(),
                new String(tokenField.getPassword()).trim(), s.getUserAgent(), interval,
                autoCheckinBox.isSelected(), buddyTravelBox.isSelected());
        CodeBuddyService.getInstance().restartTimer();
        CodeBuddyService.getInstance().refreshAsync();
        Messages.showInfoMessage(project, "设置已保存", "CodeBuddy");
    }

    private void loadSettingsToUi() {
        CodeBuddySettings s = CodeBuddySettings.getInstance();
        apiBaseField.setText(s.getApiBase());
        stateDbField.setText(s.getStateDbPath());
        tokenField.setText(s.getAccessToken());
        intervalField.setText(String.valueOf(s.getRefreshIntervalMinutes()));
        autoCheckinBox.setSelected(s.isAutoCheckin());
        buddyTravelBox.setSelected(s.isBuddyTravel());
    }

    // ============================================================
    // 刷新 UI
    // ============================================================
    @Override
    public void onCodeBuddyUpdate() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            updateUi();
        });
    }

    private void updateUi() {
        CodeBuddyService s = CodeBuddyService.getInstance();
        String err = s.getError();
        Models.UsageResult u = s.getUsage();

        if ("NO_CREDENTIALS".equals(err)) {
            creditsLabel.setText("未登录");
            detailLabel.setText("未找到登录凭据：请登录 CodeBuddy，或手动填写 Access Token");
        } else if ("AUTH_EXPIRED".equals(err)) {
            creditsLabel.setText("登录已过期");
            detailLabel.setText("请重新登录 CodeBuddy，或手动填写 Access Token");
        } else if (u != null) {
            creditsLabel.setText(CodeBuddyView.formatNumber(u.remain));
            double pct = u.total > 0 ? u.remain / u.total * 100 : 0;
            detailLabel.setText("总量 " + CodeBuddyView.formatNumber(u.total)
                    + " · 剩余占比 " + String.format("%.1f", pct) + "%");
        } else if (err != null && !err.isEmpty()) {
            creditsLabel.setText("获取失败");
            detailLabel.setText(err);
        } else {
            creditsLabel.setText(s.isUpdating() ? "刷新中…" : "-");
            detailLabel.setText(" ");
        }

        tableModel.setRowCount(0);
        if (u != null) {
            for (Models.Account a : CodeBuddyView.visibleAccounts(u)) {
                tableModel.addRow(new Object[]{a.name, CodeBuddyView.formatNumber(a.cycleRemain),
                        CodeBuddyView.formatNumber(a.cycleSize), a.endTime});
            }
        }

        StringBuilder status = new StringBuilder();
        status.append("签到：").append(CodeBuddyView.checkinText(s));
        if (CodeBuddySettings.getInstance().isBuddyTravel()) {
            status.append("    喵喵：").append(CodeBuddyView.buddyText(s));
        }
        if (s.getUpdatedAt() > 0) {
            status.append("    更新：").append(
                    new SimpleDateFormat("HH:mm:ss").format(new Date(s.getUpdatedAt())));
        }
        statusLabel.setText(status.toString());

        Models.AuthState auth = s.getAuth();
        if (auth == null) {
            authLabel.setText(" ");
        } else {
            StringBuilder ab = new StringBuilder();
            ab.append("凭据：");
            if (auth.mode == Models.AuthMode.NONE) {
                ab.append("未登录");
                if (auth.note != null && !auth.note.isEmpty()) {
                    ab.append("（").append(auth.note).append("）");
                }
            } else {
                ab.append(auth.mode == Models.AuthMode.TOKEN_AUTO ? "自动读取" : "手动填写");
                if (auth.expiresAt != null) {
                    ab.append(" · 有效期至 ").append(
                            new SimpleDateFormat("yyyy-MM-dd").format(new Date(auth.expiresAt)));
                }
            }
            authLabel.setText(ab.toString());
        }
    }

    @Override
    public void dispose() {
        CodeBuddyService.getInstance().removeListener(listener);
    }
}
