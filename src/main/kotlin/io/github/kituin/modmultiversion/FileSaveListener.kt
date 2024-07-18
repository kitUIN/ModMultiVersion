package io.github.kituin.modmultiversion

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.kituin.modmultiversion.LineHelper.Companion.hasKey
import io.github.kituin.modmultiversion.LineHelper.Companion.interpret
import io.github.kituin.modmultiversion.LineHelper.Companion.isComment
import io.github.kituin.modmultiversion.LineHelper.Companion.replacement
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

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
                copy(
                    sourceFile, Path("${loaderFile.path}/$targetFileName"), loaderFile.name, loader, true,
                    setting.state.oneWay.contains(relativePath)
                )
            }
        }
    }


    private fun copy(
        sourceFile: File,
        targetFilePath: Path,
        folderName: String,
        loader: String,
        forward: Boolean = true,
        oneWay: Boolean = false
    ) {
        var inBlock = false
        var inIfBlock = false
        val newLines = mutableListOf<String>()
        val lines = sourceFile.readLines()
        val folder = targetFilePath.parent.pathString
        val fileName = targetFilePath.name
        val fileNameWithoutExtension = targetFilePath.nameWithoutExtension
        val map = mutableMapOf(
            "$$" to folderName,
            "\$folder" to folder,
            "\$loader" to loader,
            "\$fileName" to fileName,
            "\$fileNameWithoutExtension" to fileNameWithoutExtension
        )
        var targetFile = targetFilePath.toFile()
        for (i in lines.indices) {
            val line = lines[i]
            val trimmedLine = line.trimStart()
            val prefix = isComment(trimmedLine)
            prefix?.let {
                val lineContent = trimmedLine.removePrefix(it).trimStart()
                if (i <= 3) {
                    // 文件头部进行检测
                    val flag = if (hasKey(lineContent, Keys.EXCLUDE) && forward) {
                        val delete = interpret(lineContent, Keys.EXCLUDE, map)
                        if (delete && targetFile.exists()) targetFile.delete()
                        delete
                    } else if (hasKey(lineContent, Keys.ONLY) && forward) {
                        val delete = !interpret(lineContent, Keys.ONLY, map)
                        if (delete && targetFile.exists()) targetFile.delete()
                        delete
                    } else if (hasKey(lineContent, Keys.ONEWAY) && forward) {
                        interpret(lineContent, Keys.ONEWAY, map)
                    } else if (hasKey(lineContent, Keys.RENAME) && forward) {
                        val rename = replacement(lineContent, Keys.RENAME, map)
                        targetFile = File(folder, rename)
                        false
                    } else false
                    if (flag) return
                }
                when {
                    hasKey(lineContent, Keys.ELSE_IF) ->
                        inBlock = inIfBlock && interpret(lineContent, Keys.ELSE_IF, map)

                    hasKey(lineContent, Keys.IF) -> {
                        inBlock = interpret(lineContent, Keys.IF, map)
                        inIfBlock = true
                    }

                    hasKey(lineContent, Keys.ELSE) && inIfBlock -> inBlock = !inBlock
                    hasKey(lineContent, Keys.END_IF) -> {
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
                            if (moduleContentRoot.findFile(forwardPath) != null
                            ) {
                                copy(sourceFile, Path(forwardPath), folder, loader.value, false)
                            } else {
                                val targetName = "$projectPath/origin/$subPath"
                                val target = File(targetName)
                                if (target.exists()) {
                                    copy(sourceFile, target.toPath(), folder, loader.value, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}