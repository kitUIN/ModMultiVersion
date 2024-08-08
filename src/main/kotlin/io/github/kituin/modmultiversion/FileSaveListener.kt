package io.github.kituin.modmultiversion

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import io.github.kituin.modmultiversiontool.FileHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.File
import kotlin.io.path.*


class FileSaveListener(private val project: Project?) : BulkFileListener {

    private fun copyFile(
        sourceFile: File,
        moduleContentRoot: VirtualFile,
        targetFileName: String,
        loader: String?,
        loaders: List<String>,
        fileHelper: FileHelper,
        ignore: List<String> = mutableListOf()
    ) {
        val manager = FileDocumentManager.getInstance();
        loaders.forEach { loaderF ->
            if ((loader == null && moduleContentRoot.findFile("${loaderF}/origin/$targetFileName") != null) ||
                (loader != null && loader != loaderF)
            ) return@forEach
            moduleContentRoot.findDirectory(loaderF)?.children?.forEach { loaderFile ->
                if (loaderFile.isDirectory && loaderFile.name.startsWith(loaderF) && !ignore.contains(loaderFile.name)) {
                    fileHelper.copy(
                        sourceFile, Path("${loaderFile.path}/$targetFileName"),
                        loaderFile.name, loaderF, true
                    )
                    moduleContentRoot.findFile("${loaderFile.path}/$targetFileName")?.let {
                        manager.reloadFromDisk(manager.getDocument(it)!!)
                    }
                }
            }

        }
    }

    private fun processFile(
        relativePath: String,
        sourceFile: File,
        loaders: List<String>,
        moduleContentRoot: VirtualFile,
        fileHelper: FileHelper
    ) {
        val targetFileName = relativePath.substringAfter("origin/", relativePath)
        val loader = loaders.firstOrNull { relativePath.startsWith(it) }
        if (relativePath.startsWith("${loader}/origin/") || relativePath.startsWith("origin/")) {
            copyFile(sourceFile, moduleContentRoot, targetFileName, loader, loaders, fileHelper)
        } else {
            // 反向更新
            val sourcePath = Path(relativePath)
            if (sourcePath.nameCount < 3) return
            val folder = sourcePath.getName(1).name
            val subPath = sourcePath.subpath(2, sourcePath.nameCount).invariantSeparatorsPathString
            val forwardPathName = "${fileHelper.projectPath}/$loader/origin/$subPath"
            var forwardPath = Path(forwardPathName)
            if (moduleContentRoot.findFile(forwardPathName) == null) {
                forwardPath = Path(fileHelper.projectPath, "origin", subPath)
                if (!forwardPath.exists()) return
            }
            fileHelper.copy(sourceFile, forwardPath, folder, loader!!, false)
            copyFile(forwardPath.toFile(), moduleContentRoot, subPath,
                loaders.firstOrNull {
                    forwardPath.invariantSeparatorsPathString.removePrefix("${fileHelper.projectPath}/").startsWith(it)
                }, loaders, fileHelper, mutableListOf(folder))

        }
    }

    override fun before(events: List<VFileEvent>) {
        for (event in events) {
            if (event is VFileCreateEvent) {
                // 处理新建文件事件
                println("File created: ${event.path}")
            }
        }
    }


    override fun after(events: List<VFileEvent>) {
        if (project == null) return
        val loaders = project.getService(LoadersPluginState::class.java).loaders
        for (event in events) {
            if (event is VFileContentChangeEvent) {
                println("File saved: ${event.path}")
                val file = event.file ?: return
                val projectPath = project.basePath ?: return
                val fileHelper = FileHelper(projectPath)
                if (!file.isDirectory && file.path.startsWith(projectPath)) {
                    val moduleContentRoot =
                        ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
                    val relativePath = file.path.removePrefix("$projectPath/")
                    val sourceFile = File(file.path)
                    processFile(relativePath, sourceFile, loaders, moduleContentRoot, fileHelper)
                }
            }
        }
    }
}