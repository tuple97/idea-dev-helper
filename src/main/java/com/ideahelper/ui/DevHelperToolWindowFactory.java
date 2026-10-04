package com.ideahelper.ui;

import java.util.Collections;

import com.ideahelper.cb.CodeBuddyPanel;
import com.ideahelper.jrebel.JRebelPanel;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** 右侧工具栏「Dev Helper」窗口：内含 CodeBuddy 与 JRebel 两个页签。 */
public class DevHelperToolWindowFactory implements ToolWindowFactory {

    /** 项目主页（标题栏 GitHub 图标指向这里） */
    private static final String GITHUB_URL = "https://github.com/tuple97/idea-dev-helper";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 2022.1 的 ContentFactory 通过 ContentManager 获取（ContentFactory.getInstance() 是后续版本才有的）
        ContentFactory factory = toolWindow.getContentManager().getFactory();

        CodeBuddyPanel codeBuddyPanel = new CodeBuddyPanel(project);
        Content codeBuddyContent = factory.createContent(codeBuddyPanel, "CodeBuddy", true);
        toolWindow.getContentManager().addContent(codeBuddyContent);

        JRebelPanel jRebelPanel = new JRebelPanel(project);
        Content jRebelContent = factory.createContent(jRebelPanel, "JRebel", false);
        toolWindow.getContentManager().addContent(jRebelContent);

        Disposer.register(toolWindow.getDisposable(), codeBuddyPanel);
        Disposer.register(toolWindow.getDisposable(), jRebelPanel);

        // 标题栏最右侧的 GitHub 图标：点击跳转项目主页，标识插件来源
        toolWindow.setTitleActions(Collections.singletonList(new AnAction(
                "GitHub 项目主页", "在浏览器中打开项目主页 " + GITHUB_URL, Icons.GITHUB) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                BrowserUtil.browse(GITHUB_URL);
            }
        }));
    }
}
