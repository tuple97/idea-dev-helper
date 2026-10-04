package com.ideahelper.jrebel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

/** 工具窗中的 JRebel 面板：启停本地激活服务、生成并复制 UUID / 激活地址。 */
public class JRebelPanel extends JPanel implements Disposable {

    private final Project project;

    private final JTextField portField = new JTextField(8);
    private final JComboBox<String> schemaBox = new JComboBox<>(new String[]{"http", "https"});
    private final JTextField exportHostField = new JTextField(20);
    private final JTextField offlineDaysField = new JTextField(6);
    private final JComboBox<String> workModeBox =
            new JComboBox<>(new String[]{"自动", "强制离线", "强制在线", "oldGuid"});
    private final JCheckBox autoStartBox = new JCheckBox("打开本面板时自动开启服务");

    private final JLabel statusLabel = new JLabel(" ");
    private final JButton toggleButton = new JButton("启动服务");

    private final JTextField uuidField = new JTextField(38);
    private final JTextField urlField = new JTextField(38);

    public JRebelPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        loadSettingsToUi();

        JRebelSettings settings = JRebelSettings.getInstance();
        if (settings.isAutoStart()) {
            JRebelLicenseServer server = JRebelLicenseServer.getInstance();
            server.setWorkMode(settings.getWorkMode());
            server.start(settings.getPort(), settings.getSchema(), settings.getExportHost());
        }
        refreshStatus();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        JLabel title = new JLabel("JRebel 本地激活服务");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.add(title);
        top.add(Box.createHorizontalGlue());
        top.add(toggleButton);
        toggleButton.addActionListener(e -> toggleServer());

        header.add(top, BorderLayout.NORTH);
        header.add(statusLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildCenter() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("服务设置"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;

        int y = 0;
        addRow(panel, c, y++, "端口", portField);
        addRow(panel, c, y++, "协议", schemaBox);
        addRow(panel, c, y++, "导出主机(可选)", exportHostField);
        addRow(panel, c, y++, "离线天数(1-180)", offlineDaysField);
        addRow(panel, c, y++, "工作模式", workModeBox);

        JPanel flags = new JPanel();
        flags.setLayout(new BoxLayout(flags, BoxLayout.X_AXIS));
        flags.add(autoStartBox);
        flags.add(Box.createHorizontalGlue());
        addRow(panel, c, y++, "其它", flags);

        JPanel saveRow = new JPanel();
        saveRow.setLayout(new BoxLayout(saveRow, BoxLayout.X_AXIS));
        JButton applyButton = new JButton("保存并应用");
        applyButton.addActionListener(e -> applyAndRestart());
        saveRow.add(applyButton);
        saveRow.add(Box.createHorizontalGlue());
        addRow(panel, c, y, "", saveRow);
        return panel;
    }

    private JComponent buildBottom() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("激活信息（邮箱任意填写）"));

        uuidField.setEditable(false);
        urlField.setEditable(false);

        JButton generateButton = new JButton("生成 UUID");
        generateButton.addActionListener(e -> {
            JRebelLicenseServer.getInstance().newUuid();
            refreshStatus();
        });
        JButton copyUuidButton = new JButton("复制 UUID");
        copyUuidButton.addActionListener(e -> copy(uuidField.getText(), "UUID"));
        JButton copyUrlButton = new JButton("复制激活地址");
        copyUrlButton.addActionListener(e -> copy(urlField.getText(), "激活地址"));

        JPanel uuidRow = new JPanel(new BorderLayout(6, 0));
        uuidRow.add(new JLabel("UUID："), BorderLayout.WEST);
        uuidRow.add(uuidField, BorderLayout.CENTER);
        JPanel uuidButtons = new JPanel();
        uuidButtons.setLayout(new BoxLayout(uuidButtons, BoxLayout.X_AXIS));
        uuidButtons.add(generateButton);
        uuidButtons.add(Box.createHorizontalStrut(6));
        uuidButtons.add(copyUuidButton);
        uuidRow.add(uuidButtons, BorderLayout.EAST);
        panel.add(uuidRow);

        panel.add(Box.createVerticalStrut(6));

        JPanel urlRow = new JPanel(new BorderLayout(6, 0));
        urlRow.add(new JLabel("激活地址："), BorderLayout.WEST);
        urlRow.add(urlField, BorderLayout.CENTER);
        urlRow.add(copyUrlButton, BorderLayout.EAST);
        panel.add(urlRow);

        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int y, String label,
                               JComponent comp) {
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
    private void toggleServer() {
        JRebelLicenseServer server = JRebelLicenseServer.getInstance();
        if (server.isRunning()) {
            server.stop();
            refreshStatus();
            return;
        }
        if (!saveSettings()) {
            return;
        }
        JRebelSettings s = JRebelSettings.getInstance();
        server.setWorkMode(s.getWorkMode());
        String error = server.start(s.getPort(), s.getSchema(), s.getExportHost());
        if (error != null) {
            Messages.showErrorDialog(project, "启动失败：" + error, "JRebel 激活服务");
        }
        refreshStatus();
    }

    private void applyAndRestart() {
        JRebelLicenseServer server = JRebelLicenseServer.getInstance();
        boolean wasRunning = server.isRunning();
        if (wasRunning) {
            server.stop();
        }
        if (saveSettings() && wasRunning) {
            JRebelSettings s = JRebelSettings.getInstance();
            server.setWorkMode(s.getWorkMode());
            String error = server.start(s.getPort(), s.getSchema(), s.getExportHost());
            if (error != null) {
                Messages.showErrorDialog(project, "启动失败：" + error, "JRebel 激活服务");
            }
        }
        refreshStatus();
    }

    private boolean saveSettings() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (Throwable t) {
            Messages.showErrorDialog(project, "端口必须是 1-65535 的整数", "JRebel 激活服务");
            return false;
        }
        if (port < 1 || port > 65535) {
            Messages.showErrorDialog(project, "端口必须是 1-65535 的整数", "JRebel 激活服务");
            return false;
        }
        int offlineDays;
        try {
            offlineDays = Integer.parseInt(offlineDaysField.getText().trim());
        } catch (Throwable t) {
            offlineDays = 30;
        }
        JRebelSettings s = JRebelSettings.getInstance();
        s.update(port, (String) schemaBox.getSelectedItem(),
                exportHostField.getText().trim(), offlineDays, workModeBox.getSelectedIndex(),
                autoStartBox.isSelected());
        return true;
    }

    private void loadSettingsToUi() {
        JRebelSettings s = JRebelSettings.getInstance();
        portField.setText(String.valueOf(s.getPort()));
        schemaBox.setSelectedItem(s.getSchema());
        exportHostField.setText(s.getExportHost());
        offlineDaysField.setText(String.valueOf(s.getOfflineDays()));
        int mode = s.getWorkMode();
        workModeBox.setSelectedIndex(mode >= 0 && mode <= 3 ? mode : 0);
        autoStartBox.setSelected(s.isAutoStart());
    }

    private void refreshStatus() {
        JRebelLicenseServer server = JRebelLicenseServer.getInstance();
        boolean running = server.isRunning();
        toggleButton.setText(running ? "停止服务" : "启动服务");
        if (running) {
            statusLabel.setText("状态：运行中 · 端口 " + server.getPort()
                    + "（仅本机可访问，激活时数据库地址填下方激活地址即可）");
        } else if (server.getError() != null) {
            statusLabel.setText("状态：已停止（上次启动失败：" + server.getError() + "）");
        } else {
            statusLabel.setText("状态：已停止");
        }
        uuidField.setText(server.getUuid());
        JRebelSettings settings = JRebelSettings.getInstance();
        urlField.setText(server.activationUrl(settings.getSchema(), settings.getPort()));
    }

    private void copy(String text, String what) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            Messages.showInfoMessage(project, "已复制" + what + "：" + text, "JRebel 激活服务");
        } catch (Throwable t) {
            Messages.showErrorDialog(project, "复制失败：" + t.getMessage(), "JRebel 激活服务");
        }
    }

    @Override
    public void dispose() {
        // 服务为应用级，面板关闭时不停掉，便于持续激活
    }
}
