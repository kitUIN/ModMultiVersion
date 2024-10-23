package io.github.kituin.modmultiversion.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.ui.popup.PopupFactoryImpl
import io.github.kituin.modmultiversion.storage.LoadersPluginState
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

@Suppress("ActionPresentationInstantiatedInCtor")
class FindSameNameFilesAction : AnAction("同名文件") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val subPath = Path(currentFile.path.removePrefix(project.basePath!!))
        if (subPath.count() < 3) return
        val searchPath = subPath.subpath(0, 1).name
        val currentDir = subPath.subpath(1, 2).name
        if (searchPath == "origin") return
        val searchFile = subPath.subpath(2, subPath.count()).pathString
        // 查找同名文件
        val sameNameFiles = mutableMapOf<String, VirtualFile>()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
        baseDir?.findDirectory(searchPath)?.children?.forEach { file ->
            val navigateFile = file.findFile(searchFile)
            if (navigateFile != null && file.name != currentDir) {
                sameNameFiles[file.name] = navigateFile
            }
        }
        // 创建菜单
        if (sameNameFiles.isNotEmpty()) {
            val actionGroup = DefaultActionGroup()
            sameNameFiles.forEach { file ->
                actionGroup.add(object : AnAction(file.key) {
                    override fun actionPerformed(e: AnActionEvent) {
                        OpenFileDescriptor(project, file.value).navigate(true)
                    }
                })
            }

            // 显示弹出菜单
            val popup = PopupFactoryImpl.getInstance().createActionGroupPopup(
                "同名文件",
                actionGroup,
                e.dataContext,
                ActionSelectionAid.NUMBERING,
                false
            )
            popup.showInBestPositionFor(e.dataContext)
        }
    }


    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 指定在 EDT 中更新
    }
}
