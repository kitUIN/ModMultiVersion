package io.github.kituin.modmultiversion

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.io.size
import io.github.kituin.modmultiversion.LineHelper.Companion.hasKey
import io.github.kituin.modmultiversion.LineHelper.Companion.interpret
import io.github.kituin.modmultiversion.LineHelper.Companion.isComment
import io.github.kituin.modmultiversion.LineHelper.Companion.replacement
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

class FileSaveListener(private val project: Project?) : BulkFileListener {
    private var projectPath = project?.basePath

    private fun copyFile(
        sourceFile: File, moduleContentRoot: VirtualFile, targetFileName: String,
        loader: String?
    ) {
        Loaders.values().forEach { loaderF ->
            if (loader != null && loader != loaderF.value) return
            moduleContentRoot.findDirectory(loaderF.value)?.children?.forEach { loaderFile ->
                if (loaderFile.isDirectory && loaderFile.name.startsWith(loaderF.value)) {
                    copy(
                        sourceFile, Path("${loaderFile.path}/$targetFileName"),
                        loaderFile.name, loaderF.value, true
                    )
                }
            }
        }
    }


    private fun copy(
        sourceFile: File,
        targetFilePath: Path,
        folderName: String,
        loader: String,
        forward: Boolean = true
    ) {
        var inBlock = false
        var inIfBlock = false
        var oneWay = false
        val newLines = mutableListOf<String>()
        val lines = sourceFile.readLines()
        val folder = targetFilePath.parent.pathString
        val fileName = targetFilePath.name
        val fileNameWithoutExtension = targetFilePath.nameWithoutExtension
        val map = mutableMapOf(
            "$$" to folderName,
            "\$folder" to folder.removePrefix("$projectPath/"),
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
                    val flag = if (forward && hasKey(lineContent, Keys.EXCLUDE)) {
                        val delete = interpret(lineContent, Keys.EXCLUDE, map)
                        if (delete && targetFile.exists()) targetFile.delete()
                        delete
                    } else if (forward && hasKey(lineContent, Keys.ONLY)) {
                        val delete = !interpret(lineContent, Keys.ONLY, map)
                        if (delete && targetFile.exists()) targetFile.delete()
                        delete
                    } else if (hasKey(lineContent, Keys.ONEWAY)) {
                        oneWay = forward
                        !forward
                    } else if (forward && hasKey(lineContent, Keys.RENAME)) {
                        val rename = replacement(lineContent, Keys.RENAME, map)
                        targetFile = File(folder, rename)
                        false
                    } else false
                    if (flag) return
                }
                when {
                    hasKey(lineContent, Keys.PRINT) -> {
                        newLines.add(it + replacement(lineContent, Keys.RENAME, map))
                        return@let
                    }

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

    private fun processFile(relativePath: String, sourceFile: File, moduleContentRoot: VirtualFile) {
        val targetFileName = relativePath.substringAfter("origin/", relativePath)
        val loader = Loaders.values().firstOrNull { relativePath.startsWith(it.value) }?.value
        if (relativePath.startsWith("${loader}/origin/")) {
            copyFile(sourceFile, moduleContentRoot, targetFileName, loader)
        } else {
            // 反向更新
            val sourcePath = Path(relativePath)
            if (sourcePath.nameCount < 3) return
            val folder = sourcePath.getName(1).name
            val subPath = sourcePath.subpath(2, sourcePath.nameCount).pathString
            val forwardPathName = "$projectPath/$loader/origin/$subPath"
            var forwardPath = Path(forwardPathName)
            if (moduleContentRoot.findFile(forwardPathName) == null) {
                forwardPath = Path(projectPath!!, "origin", subPath)
                if (!forwardPath.exists()) return
            }
            copy(sourceFile, forwardPath, folder, loader!!, false)
        }
    }

    override fun after(events: List<VFileEvent>) {
        if (project == null) return
        events.forEach { event ->
            val file = event.file ?: return
            projectPath = project.basePath ?: return
            if (!file.isDirectory && file.path.startsWith(projectPath!!)) {
                val moduleContentRoot =
                    ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
                val relativePath = file.path.removePrefix("$projectPath/")
                val sourceFile = File(file.path)
                processFile(relativePath, sourceFile, moduleContentRoot)
            }
        }
    }
}