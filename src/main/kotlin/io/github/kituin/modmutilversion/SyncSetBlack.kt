package io.github.kituin.modmutilversion

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.isFile

class SyncSetBlack : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(PlatformDataKeys.VIRTUAL_FILE)
        if (project != null && file != null && file.canonicalPath != null) {
            val setting = project.service<SyncSetting>()
            if (setting.state.black.containsKey(file.canonicalPath)) {
                setting.state.black.remove(file.canonicalPath)
            }
            else{
                if (MyDialogWrapper(project,true, file.canonicalPath!!).showAndGet()) {
                    // user pressed OK
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val setting = project.service<SyncSetting>()
        val file = e.getData(PlatformDataKeys.VIRTUAL_FILE)
        val visible = file != null && file.isFile
        e.presentation.isEnabledAndVisible = visible
        if (visible) {
            if (setting.state.black.containsKey(file!!.canonicalPath)) {
                e.presentation.text = "取消多版本同步黑名单"
            } else {
                e.presentation.text = "设置多版本同步黑名单"
            }
        }
    }
}