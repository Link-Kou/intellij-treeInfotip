package com.plugins.infotip;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * 项目启动的时候执行
 *
 * @author LK
 * @date 2018-04-07 1:18
 */
public class PluginStartupActivity implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        XmlFile fileDirectoryXml = FileDirectory.getFileDirectoryXml(project, false);
        if (null != fileDirectoryXml) {
            XmlParsing.parsing(project, fileDirectoryXml);
        }
        FileDirectory.treeChangeListener(project);
    }

}