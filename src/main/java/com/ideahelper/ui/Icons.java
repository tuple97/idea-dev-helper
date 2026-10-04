package com.ideahelper.ui;

import javax.swing.Icon;

import com.intellij.openapi.util.IconLoader;

/** 插件图标。 */
public final class Icons {

    /** 工具箱图标（来源：toolbox-wails/build/appicon.svg），逻辑尺寸 16x16。 */
    public static final Icon TOOLBOX = IconLoader.getIcon("/icons/appicon.svg", Icons.class);

    /**
     * GitHub 图标（13x13）。深浅主题各一份：
     * {@code github.svg} / {@code github_dark.svg}，由 IconLoader 自动按主题选择。
     */
    public static final Icon GITHUB = IconLoader.getIcon("/icons/github.svg", Icons.class);

    private Icons() {
    }
}
