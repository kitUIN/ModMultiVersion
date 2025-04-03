package io.github.kituin.modmultiversion

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import io.github.kituin.modmultiversiontool.FileHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.kituin.modmultiversion.storage.AliasState
import io.github.kituin.modmultiversion.storage.LoadersPluginState
import io.github.kituin.modmultiversion.tool.VFileCopyWorker
import io.github.kituin.modmultiversiontool.CommentMode
import java.io.File
import java.util.*
import kotlin.io.path.*


class FileSaveListener(private val project: Project?) : BulkFileListener {

    private val logger = Logger.getInstance(FileSaveListener::class.java)
    private fun getAlias(): SortedMap<String, MutableMap<String, String>?> {
        return project!!.getService(AliasState::class.java).alias.toSortedMap(compareByDescending { it })
    }

    private fun getCommentMode(): CommentMode {
        val service = project!!.getService(LoadersPluginState::class.java)
        return CommentMode(service.commentBeforeCode, service.commentWithOneSpace)
    }

    private fun copyFile(
        sourceFile: File,
        moduleContentRoot: VirtualFile,
        targetFileName: String,
        loader: String?,
        loaders: List<String>,
        fileHelper: FileHelper,
        ignore: List<String> = mutableListOf()
    ) {
        val manager = FileDocumentManager.getInstance()
        loaders.forEach { loaderF ->
            if ((loader == null && moduleContentRoot.findFile("${loaderF}/origin/$targetFileName") != null) ||
                (loader != null && loader != loaderF)
            ) return@forEach
            moduleContentRoot.findDirectory("${fileHelper.projectPath}/$loaderF")?.children?.forEach { loaderFile ->
                if (loaderFile.isDirectory && loaderFile.name.startsWith(loaderF) && !ignore.contains(loaderFile.name)) {
                    fileHelper.copy(
                        sourceFile, Path("${loaderFile.path}/$targetFileName"),
                        loaderFile.name, loaderF, getAlias(), true, getCommentMode()
                    )
                    logger.info("File Saved: ${loaderFile.path}/$targetFileName")
//                    moduleContentRoot.findFile("${loaderFile.path}/$targetFileName")?.let {
//                        manager.reloadFromDisk(manager.getDocument(it)!!)
//                        logger.info("File reloadFromDisk: ${loaderFile.path}/$targetFileName")
//                    }
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
            fileHelper.copy(
                sourceFile,
                forwardPath,
                folder,
                loader!!,
                getAlias(),
                false,
                getCommentMode()
            )
            logger.info("File Reversed: ${forwardPath.invariantSeparatorsPathString}")
            copyFile(
                forwardPath.toFile(), moduleContentRoot, subPath,
                loaders.firstOrNull {
                    forwardPath.invariantSeparatorsPathString.removePrefix("${fileHelper.projectPath}/").startsWith(it)
                }, loaders, fileHelper, mutableListOf(folder)
            )
        }
    }

    override fun before(events: List<VFileEvent>) {
        for (event in events) {
            if (event is VFileCreateEvent) {
                // 处理新建文件事件
                logger.info("File created: ${event.path}")
            }
        }
    }


    override fun after(events: List<VFileEvent>) {
        if (project == null) return
        val loaders = project.getService(LoadersPluginState::class.java).loaders
        for (event in events) {
            if (event is VFileContentChangeEvent) {
                val file = event.file
                val projectPath = project.basePath ?: return
                if (!file.isDirectory && file.path.startsWith(projectPath)) {
                    val moduleContentRoot =
                        ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
                    val fileHelper = FileHelper(projectPath, VFileCopyWorker(moduleContentRoot))
                    val relativePath = file.path.removePrefix("$projectPath/")
                    val sourceFile = File(file.path)
                    processFile(relativePath, sourceFile, loaders, moduleContentRoot, fileHelper)
                }
            }
        }
    }
}