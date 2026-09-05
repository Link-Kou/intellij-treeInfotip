package com.plugins.infotip.gui.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * A <code>NotesToolWindowFactory</code> Class
 * <p>
 * 「TreeInfotip 备注」工具窗口，三个 tab：
 * </p>
 * <ol>
 *   <li><b>文件成员</b>（默认打开）：当前文件的方法和属性，每项后面跟它的注释，
 *   见 {@link MemberTreeView}</li>
 *   <li><b>目录备注</b>：{@code DirectoryV3.xml} 里配的那些规则，见 {@link NoteTreeView}</li>
 *   <li><b>说明</b>：菜单怎么用、配置文件有哪些参数，见 {@link HelpView}</li>
 * </ol>
 * <p>
 * 文件成员排在前面并默认选中：目录备注是配好就不怎么动的东西，而文件成员是看代码时一直要看的。
 * 说明排最后，它是查一次就不用再看的。
 * </p>
 * <p>
 * 三个 tab 都不加 {@code setCloseable(true)}——关掉之后没有入口再打开，只能重启 IDE。
 * </p>
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class NotesToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        final ContentFactory contentFactory = ContentFactory.getInstance();
        final ContentManager manager = toolWindow.getContentManager();
        final Content members = contentFactory.createContent(MemberTreeView.createPanel(project), "文件成员", false);
        members.setCloseable(false);
        manager.addContent(members);
        final Content notes = contentFactory.createContent(NoteTreeView.createPanel(project), "目录备注", false);
        notes.setCloseable(false);
        manager.addContent(notes);
        final Content help = contentFactory.createContent(HelpView.createPanel(project), "说明", false);
        help.setCloseable(false);
        manager.addContent(help);
        //addContent 的顺序就是 tab 顺序，但选中项要显式设，不设的话平台会恢复上次关窗口时选的那个
        manager.setSelectedContent(members);
    }
}
