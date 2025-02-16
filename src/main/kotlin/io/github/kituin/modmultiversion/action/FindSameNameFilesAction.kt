package io.github.kituin.modmultiversion.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.ui.popup.PopupFactoryImpl
import io.github.kituin.modmultiversion.ModMultiVersionBundle
import io.github.kituin.modmultiversion.toInt
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString


class FileNode(
    val name: String,
    val file: VirtualFile? = null,
    val children: MutableList<FileNode> = mutableListOf(),
)

@Suppress("ActionPresentationInstantiatedInCtor")
class FindSameNameFilesAction : AnAction(ModMultiVersionBundle.message("menu.sameFiles")) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val subPath = Path(currentFile.path.removePrefix(project.basePath!!))
        if (subPath.count() < 3) return

        val sameNameFiles = searchSameFiles(subPath, project)

        // 创建菜单
        if (sameNameFiles.isNotEmpty()) {
            val actionGroup = DefaultActionGroup()
            sameNameFiles.forEach { file ->
                if (file.value.children.isNotEmpty()) {
                    val actionChildGroup = DefaultActionGroup(file.key, true)
                    file.value.children.forEach { child ->
                        child.file?.let {
                            actionChildGroup.add(object : AnAction(child.name) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    OpenFileDescriptor(project, it).navigate(true)
                                }
                            })
                        }
                    }
                    actionGroup.add(actionChildGroup)
                }
                else{
                    file.value.file?.let {
                        actionGroup.add(object : AnAction(file.key) {
                            override fun actionPerformed(e: AnActionEvent) {
                                OpenFileDescriptor(project, it).navigate(true)
                            }
                        })
                    }
                }
            }

            // 显示弹出菜单
            val popup = PopupFactoryImpl.getInstance().createActionGroupPopup(
                ModMultiVersionBundle.message("menu.sameFiles"),
                actionGroup,
                e.dataContext,
                ActionSelectionAid.NUMBERING,
                false
            )
            popup.showInBestPositionFor(e.dataContext)
        }
    }

    private fun searchSameFiles(
        subPath: Path,
        project: Project
    ): MutableMap<String, FileNode> {
        val loaderPath = subPath.subpath(0, 1).name
        val searchFile = subPath.subpath((loaderPath != "origin").toInt() + 1, subPath.count()).pathString
        // 查找同名文件
        val sameNameFiles = mutableMapOf<String, FileNode>()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
        baseDir?.children?.forEach outer@{ dir ->
            if (!dir.isDirectory) return@outer
            if(loaderPath != "origin"){
                extractedFilesFromLoader(dir, sameNameFiles, searchFile, subPath.subpath(1, 2).name)
            }else{
                extractedFilesFromOrigin(dir, sameNameFiles, searchFile)
            }
        }
        return sameNameFiles
    }

    private fun extractedFilesFromLoader(
        dir: VirtualFile,
        sameNameFiles: MutableMap<String, FileNode>,
        searchFile: String,
        loaderVersionPath: String
    ) {
        if (dir.name != "origin") {
            sameNameFiles[dir.name] = FileNode(dir.name)
            dir.children?.forEach { file ->
                if (!file.isDirectory) return@forEach
                val navigateFile = file.findFile(searchFile)
                if (navigateFile != null && file.name != loaderVersionPath) {
                    sameNameFiles[dir.name]?.children?.add(FileNode(file.name, navigateFile))
                }
            }
            if (sameNameFiles[dir.name]?.children?.isEmpty() == true) {
                sameNameFiles.remove(dir.name)
            }
        } else {
            val navigateFile = dir.findFile(searchFile)
            if (navigateFile != null) {
                sameNameFiles[dir.name] = FileNode(dir.name, navigateFile)
            }
        }
    }
    private fun extractedFilesFromOrigin(
        dir: VirtualFile,
        sameNameFiles: MutableMap<String, FileNode>,
        searchFile: String
    ) {
        if (dir.name != "origin") {
            sameNameFiles[dir.name] = FileNode(dir.name)
            dir.children?.forEach { file ->
                println(file)
                println(searchFile)
                if (!file.isDirectory) return@forEach
                val navigateFile = file.findFile(searchFile)
                if (navigateFile != null) {
                    sameNameFiles[dir.name]?.children?.add(FileNode(file.name, navigateFile))
                }
            }
            if (sameNameFiles[dir.name]?.children?.isEmpty() == true) {
                sameNameFiles.remove(dir.name)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT // 指定在 EDT 中更新
    }
}
