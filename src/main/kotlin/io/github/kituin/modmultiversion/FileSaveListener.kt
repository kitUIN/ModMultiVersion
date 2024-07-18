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

class FileSaveListener(private val project: Project?) : BulkFileListener {
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
                copy(
                    sourceFile, targetFile, loaderFile.name, loader, true,
                    setting.state.oneWay.contains(relativePath)
                )
            }
        }
    }


    private fun copy(
        sourceFile: File, targetFile: File,
        folder: String,
        loader: String,
        forward: Boolean = true,
        oneWay: Boolean = false
    ) {
        var inBlock = false
        var inIfBlock = false
        val newLines = mutableListOf<String>()
        val lines = sourceFile.readLines()
        val map = mutableMapOf(
            "$$" to folder,
            "\$loader" to loader
        )
        for (i in lines.indices) {
            val line = lines[i]
            val trimmedLine = line.trimStart()
            val prefix = isComment(trimmedLine)
            prefix?.let {
                val lineContent = trimmedLine.removePrefix(it).trimStart()
                if (i <= 2) {
                    // 文件头部进行检测
                    var delete = false
                    val flag = if (haveKey(line, Keys.EXCLUDE) && forward) {
                        delete = interpret(line, Keys.EXCLUDE, map)
                        delete
                    } else if (haveKey(line, Keys.ONLY) && forward) {
                        delete = !interpret(line, Keys.ONLY, map)
                        delete
                    } else if (haveKey(line, Keys.ONEWAY) && !forward) {
                        interpret(line, Keys.ONEWAY, map)
                    } else false
                    if (flag) {
                        if (delete && targetFile.exists()) targetFile.delete()
                        return
                    }
                }
                when {
                    haveKey(lineContent, Keys.ELSE_IF) ->
                        inBlock = inIfBlock && interpret(lineContent, Keys.ELSE_IF, map)

                    haveKey(lineContent, Keys.IF) -> {
                        inBlock = interpret(lineContent, Keys.IF, map)
                        inIfBlock = true
                    }

                    haveKey(lineContent, Keys.ELSE) && inIfBlock -> inBlock = !inBlock
                    haveKey(lineContent, Keys.END_IF) -> {
                        inBlock = false
                        inIfBlock = false
                    }

                    inBlock -> {
                        newLines.add(if (forward) trimmedLine.removePrefix(it) else "$it$line")
                        return@let
                    }

                    else -> {
                        newLines.add(line)
                        return@let
                    }
                }
                if (!oneWay) newLines.add(line)
            } ?: newLines.add(line)
        }

        targetFile.writeText(newLines.joinToString("\n"))
    }


    override fun after(events: List<VFileEvent>) {
        if (project == null) return
        events.forEach { event ->
            val file = event.file ?: return
            val projectPath = project.basePath ?: return
            if (!file.isDirectory && file.path.startsWith(projectPath)) {
                val moduleContentRoot =
                    ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
                val relativePath = file.path.removePrefix("$projectPath/")
                val sourceFile = File(file.path)
                when {
                    relativePath.startsWith("origin/") -> {
                        val targetFileName = relativePath.removePrefix("origin/")
                        Loaders.values().forEach { loader ->
                            if (moduleContentRoot.findFile("${loader.value}/$relativePath") == null) {
                                copyFile(sourceFile, moduleContentRoot, targetFileName, loader.value, file.path)
                            }
                        }
                    }

                    else -> {
                        val loader = Loaders.values().firstOrNull { relativePath.startsWith(it.value) } ?: return
                        val prefix = "${loader.value}/origin/"
                        if (relativePath.startsWith(prefix)) {
                            copyFile(
                                sourceFile, moduleContentRoot, relativePath.removePrefix(prefix),
                                loader.value, file.path
                            )
                        } else {
                            // 反向更新
                            val (folder, subPath) = relativePath.split("/", limit = 3).let {
                                if (it.size < 3) return
                                it[1] to it.drop(2).joinToString("/")
                            }
                            val forwardPath = "$projectPath/$prefix$subPath"
                            val setting = project.service<SyncSetting>()
                            if (moduleContentRoot.findFile(forwardPath) != null &&
                                !setting.state.oneWay.contains(forwardPath)
                            ) {
                                copy(sourceFile, File(forwardPath), folder, loader.value, false)
                            } else {
                                val targetName = "$projectPath/origin/$subPath"
                                val target = File(targetName)
                                if (target.exists() && !setting.state.oneWay.contains(targetName)) {
                                    copy(sourceFile, target, folder, loader.value, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}