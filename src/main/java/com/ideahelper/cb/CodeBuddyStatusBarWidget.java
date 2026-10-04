package com.ideahelper.cb;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ideahelper.ui.Icons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;

/**
 * 右下角状态栏组件：显示工具箱图标 + CodeBuddy 剩余积分，鼠标悬停展示明细，点击立即刷新。
 *
 * <p>在 2022.1 及之后的版本中，状态栏对实现了 {@link CustomStatusBarWidget} 的组件会直接
 * 使用 {@link #getComponent()}，因此这里让组件自身实现该接口（2022.1 还没有
 * {@code StatusBarWidget.CustomPresentation}）。</p>
 */
public class CodeBuddyStatusBarWidget extends JPanel implements CustomStatusBarWidget {

    public static final String ID = "DevHelperCodeBuddyUsage";

    private final Project project;
    private final JLabel label = new JLabel();
    private final CodeBuddyService.Listener listener = this::onServiceUpdate;
    private volatile boolean disposed;

    public CodeBuddyStatusBarWidget(Project project) {
        super(new BorderLayout());
        this.project = project;
        setOpaque(false);
        label.setIcon(Icons.TOOLBOX);
        label.setIconTextGap(4);
        add(label, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // JLabel 铺满整个区域，鼠标事件会落在 label 上而不会冒泡到面板，因此两个组件都要监听
        MouseAdapter clickToRefresh = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                CodeBuddyService.getInstance().refreshAsync();
            }
        };
        addMouseListener(clickToRefresh);
        label.addMouseListener(clickToRefresh);
        CodeBuddyService.getInstance().addListener(listener);
        updateContent();
        CodeBuddyService.getInstance().refreshAsync();
    }

    private void onServiceUpdate() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || disposed) {
                return;
            }
            updateContent();
        });
    }

    private void updateContent() {
        CodeBuddyService service = CodeBuddyService.getInstance();
        label.setText(CodeBuddyView.statusText(service));
        String tip = CodeBuddyView.tooltipHtml(service);
        setToolTipText(tip);
        label.setToolTipText(tip);
        revalidate();
        repaint();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public String ID() {
        return ID;
    }

    @Override
    public WidgetPresentation getPresentation() {
        return new WidgetPresentation() {
            @Override
            public String getTooltipText() {
                return CodeBuddyView.tooltipHtml(CodeBuddyService.getInstance());
            }

            @Override
            public Consumer<MouseEvent> getClickConsumer() {
                return e -> CodeBuddyService.getInstance().refreshAsync();
            }
        };
    }

    @Override
    public void install(StatusBar statusBar) {
        // 组件已在构造函数中注册服务监听，无需额外处理
    }

    @Override
    public void dispose() {
        disposed = true;
        CodeBuddyService.getInstance().removeListener(listener);
    }
}
