package io.github.kituin.modmutilversion

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.File


class FileSaveListener(private val project: Project?) : BulkFileListener {
    private var loaders = arrayOf("fabric", "neoforge", "forge", "quilt")
    private fun copyFile(
        sourceFile: File,
        moduleContentRoot: VirtualFile,
        targetFileName: String,
        loader: String,
        relativePath: String
    ) {
        val setting = project?.service<SyncSetting>() ?: return
        moduleContentRoot.findDirectory(loader)?.children?.forEach { loaderFile ->
            if (loaderFile.isDirectory && loaderFile.name.startsWith(loader)) {
                val loaderName = "$loader/${loaderFile.name}"
                var targetFileName = "${loaderFile.path}/$targetFileName"
                if (relativePath.endsWith(".json5") && setting.state.replaceJson5) {
                    targetFileName = targetFileName.replace(".json5", ".json")
                }
                val targetFile = File(targetFileName)
                if (setting.state.black[relativePath]?.contains(loaderName) == true) {
//                    println("black")
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    return@forEach
                }
                if (setting.state.white.containsKey(relativePath) && !setting.state.white[relativePath]!!.contains(
                        loaderName
                    )
                ) {
//                    println("white")
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    return@forEach
                }
                copy(sourceFile, targetFile, loaderFile.name, setting.state.oneWay.contains(relativePath))
                loaderFile.refresh(true, false)
            }
        }
    }


    private fun copy(
        sourceFile: File, targetFile: File,
        folder: String,
        forward: Boolean = true,
        oneWay: Boolean = false
    ) {
        sourceFile.copyTo(targetFile, overwrite = true)
        val lines = targetFile.readLines()
        var inBlock = false
        var inIfBlock = false
        var inOtherBlock = false
        var inOtherIfBlock = false
        val newLines = lines.map { line ->
            when {
                line.startsWith("//") -> {
                    if (line.startsWith("// IF")) {
                        if (folder in line.trim().removePrefix("// IF ").split(" || ")) {
                            inBlock = true
                            inIfBlock = true
                        }
                    } else if (line.startsWith("// ELSE IF")) {
                        inBlock = false
                        if (!inIfBlock && folder in line.trim().removePrefix("// ELSE IF ").split(" || ")) {
                            inBlock = true
                            inIfBlock = true
                        }
                    } else if (line.startsWith("// ELSE")) {
                        inBlock = false
                        if (!inIfBlock) {
                            inBlock = true
                            inIfBlock = true
                        }
                    } else if (line.startsWith("// END IF")) {
                        inBlock = false
                        inIfBlock = false
                    }
                    if (inBlock && forward) line.removePrefix("//") else (if (oneWay) "" else line)
                }

                line.startsWith("#") -> {
                    if (line.startsWith("# IF")) {
                        if (folder in line.trim().removePrefix("# IF ").split(" || ")) {
                            inOtherBlock = true
                            inOtherIfBlock = true
                        }
                    } else if (line.startsWith("# ELSE IF")) {
                        inOtherBlock = false
                        if (!inOtherIfBlock && folder in line.trim().removePrefix("# ELSE IF ").split(" || ")) {
                            inOtherBlock = true
                            inOtherIfBlock = true
                        }
                    } else if (line.startsWith("# ELSE")) {
                        inOtherBlock = false
                        if (!inOtherIfBlock) {
                            inOtherBlock = true
                            inOtherIfBlock = true
                        }
                    } else if (line.startsWith("# END IF")) {
                        inOtherBlock = false
                        inOtherIfBlock = false
                    }
                    if (inOtherBlock && forward) line.removePrefix("#") else (if (oneWay) "" else line)
                }

                inBlock && !forward -> {
                    "//$line"
                }

                inOtherBlock && !forward -> {
                    "#$line"
                }

                else -> {
                    line
                }
            }
        }
        targetFile.writeText(newLines.joinToString("\n"))
    }

    override fun after(events: List<VFileEvent>) {
        if (project == null) return
        val projectPath = project.basePath
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        events.forEach { event ->
            val file = event.file
            if (file != null && projectPath != null && !file.isDirectory && file.path.startsWith(projectPath)) {
//                println(file.path)
                val moduleContentRoot = projectFileIndex.getContentRootForFile(file) ?: return
                val relativePath = file.path.removePrefix("$projectPath/")
                val sourceFile = File(file.path)
                // 默认设置
                if (relativePath.startsWith("origin/")) {
                    val targetFileName = relativePath.removePrefix("origin/")
                    for (loader in loaders) {
                        if (moduleContentRoot.findFile("$loader/$relativePath") != null) {
                            // 如果有加载器特有的文件,则不复制
                            continue
                        }
                        copyFile(sourceFile, moduleContentRoot, targetFileName, loader, file.path)
                    }
                } else {
                    val loader = loaders.firstOrNull { relativePath.startsWith(it) } ?: return
                    if (relativePath.startsWith("$loader/origin/")) {
                        val targetFileName = relativePath.removePrefix("$loader/origin/")
                        copyFile(sourceFile, moduleContentRoot, targetFileName, loader, file.path)
                    } else { // 反向更新
                        var folder: String
                        val subList = relativePath.split("/").let {
                            if (it.count() < 2) return
                            folder = it[1]
                            it.subList(2, it.count())
                        }
                        val subPath = subList.joinToString(separator = "/")
                        val forwardPath = "$projectPath/$loader/origin/$subPath"
                        val setting = project.service<SyncSetting>()
                        if (moduleContentRoot.findFile(forwardPath) != null &&
                            !setting.state.oneWay.contains(forwardPath)
                        ) {
                            // 如果有加载器特有的文件
                            copy(sourceFile, File(forwardPath), folder, false)
                        } else {
                            val targetName = "$projectPath/origin/$subPath"
                            val target = File(targetName)
                            if (target.exists() && !setting.state.oneWay.contains(targetName)) {
                                copy(sourceFile, target, folder, false)
                            }
                        }
                    }
                }

            }
        }
    }
}