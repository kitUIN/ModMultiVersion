package io.github.kituin.modmultiversion

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.kituin.modmultiversion.LineHelper.Companion.haveKey
import io.github.kituin.modmultiversion.LineHelper.Companion.interpret
import io.github.kituin.modmultiversion.LineHelper.Companion.isComment
import java.io.File
import io.github.kituin.modmultiversioninterpreter.Interpreter

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
                var targetFName = "${loaderFile.path}/$targetFileName"
                if (relativePath.endsWith(".json5") && setting.state.replaceJson5) {
                    targetFName = targetFileName.replace(".json5", ".json")
                }
                val targetFile = File(targetFName)
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
                copy(sourceFile, targetFile, loaderFile.name, true, setting.state.oneWay.contains(relativePath))
                // loaderFile.refresh(true, false)
            }
        }
    }


    private fun copy(
        sourceFile: File, targetFile: File,
        folder: String,
        forward: Boolean = true,
        oneWay: Boolean = false
    ) {
        var inBlock = false
        var inIfBlock = false
        val newLines = mutableListOf<String>()
        val lines = sourceFile.readLines()
        val map = mutableMapOf("$$" to folder)
        if (lines.isNotEmpty()) {
            val firstLine = lines[0].trimStart()
            val flag = if (haveKey(firstLine, Keys.EXCLUDE)) {
                interpret(firstLine, Keys.EXCLUDE, map)
            } else if (haveKey(firstLine, Keys.ONLY)) {
                !interpret(firstLine, Keys.ONLY, map)
            } else false
            if (flag) return
        }
        for (line in lines) {
            val trimmedLine = line.trimStart()
            val prefix = isComment(trimmedLine)
            if (prefix == null) {
                newLines.add(line)
                continue
            }
            val lineContent = trimmedLine.removePrefix(prefix).trimStart()
            if (haveKey(lineContent, Keys.ELSE_IF, true)) {
                inBlock = inIfBlock && interpret(lineContent, Keys.ELSE_IF, map)
            } else if (haveKey(lineContent, Keys.IF, true)) {
                inBlock = interpret(lineContent, Keys.IF, map)
                inIfBlock = true
            } else if (haveKey(lineContent, Keys.ELSE, true)) {
                if (inIfBlock) {
                    inBlock = !inBlock
                }
            } else if (haveKey(lineContent, Keys.END_IF, true)) {
                inBlock = false
                inIfBlock = false
            } else if (inBlock) {
                newLines.add(if (forward) trimmedLine.removePrefix(prefix) else "$prefix$line")
                continue
            } else {
                newLines.add(line)
                continue
            }
            if (!oneWay) newLines.add(line)
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