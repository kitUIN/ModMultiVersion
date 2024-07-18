package io.github.kituin.modmultiversion

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
import kotlin.io.path.*

class LineCtx(
    var targetFile: File,
    val map: MutableMap<String, String>,
    val forward: Boolean,
    val newLines: MutableList<String> = mutableListOf(),
    var inBlock: Boolean = false,
    var inIfBlock: Boolean = false,
    var oneWay: Boolean = false,
    var header: Boolean = false
)

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

    private fun createMap(folderName: String, targetFilePath: Path, loader: String): MutableMap<String, String> {
        val folder = targetFilePath.parent.pathString
        val fileName = targetFilePath.name
        val fileNameWithoutExtension = targetFilePath.nameWithoutExtension
        return mutableMapOf(
            "$$" to folderName,
            "\$folder" to folder.removePrefix("$projectPath/"),
            "\$loader" to loader,
            "\$fileName" to fileName,
            "\$fileNameWithoutExtension" to fileNameWithoutExtension
        )
    }

    private fun BlackOrWhiteList(lineContent: String, key: Keys, lineCtx: LineCtx): Boolean {
        if (lineCtx.forward && hasKey(lineContent, key)) {
            var delete = interpret(lineContent, key, lineCtx.map)
            if (key == Keys.ONLY) delete = !delete
            if (delete && lineCtx.targetFile.exists()) lineCtx.targetFile.delete()
            lineCtx.header = delete
            return true
        }
        return false
    }

    private fun processHeader(lineContent: String, lineCtx: LineCtx) {
        // 文件头部进行检测
        if (BlackOrWhiteList(lineContent, Keys.EXCLUDE, lineCtx) || BlackOrWhiteList(lineContent, Keys.ONLY, lineCtx)) {
            return
        } else if (lineCtx.forward && hasKey(lineContent, Keys.RENAME)) {
            val rename = replacement(lineContent, Keys.RENAME, lineCtx.map)
            lineCtx.targetFile = File(lineCtx.map["\$folder"], rename)
            return
        } else if (hasKey(lineContent, Keys.ONEWAY)) {
            lineCtx.oneWay = lineCtx.forward
            lineCtx.header = !lineCtx.forward
            return
        }
    }

    private fun processLine(
        prefix: String,
        line: String,
        trimmedLine: String,
        lineContent: String,
        lineCtx: LineCtx
    ) {
        when {
            hasKey(lineContent, Keys.PRINT) -> {
                lineCtx.newLines.add(prefix + replacement(lineContent, Keys.RENAME, lineCtx.map))
                return
            }

            hasKey(lineContent, Keys.ELSE_IF) ->
                lineCtx.inBlock = lineCtx.inIfBlock && interpret(lineContent, Keys.ELSE_IF, lineCtx.map)

            hasKey(lineContent, Keys.IF) -> {
                lineCtx.inBlock = interpret(lineContent, Keys.IF, lineCtx.map)
                lineCtx.inIfBlock = true
            }

            hasKey(lineContent, Keys.ELSE) && lineCtx.inIfBlock -> lineCtx.inBlock = !lineCtx.inBlock
            hasKey(lineContent, Keys.END_IF) -> {
                lineCtx.inBlock = false
                lineCtx.inIfBlock = false
            }

            lineCtx.inBlock -> {
                lineCtx.newLines.add(if (lineCtx.forward) trimmedLine.removePrefix(prefix) else "$prefix$line")
                return
            }

            else -> {
                lineCtx.newLines.add(line)
                return
            }
        }
        lineCtx.newLines.add(line)
    }

    private fun copy(
        sourceFile: File,
        targetFilePath: Path,
        folderName: String,
        loader: String,
        forward: Boolean = true
    ) {
        val lines = sourceFile.readLines()
        val map = createMap(folderName, targetFilePath, loader)
        val lineCtx = LineCtx(targetFilePath.toFile(), map, forward)
        for (i in lines.indices) {
            val line = lines[i]
            val trimmedLine = line.trimStart()
            val prefix = isComment(trimmedLine)
            prefix?.let {
                val lineContent = trimmedLine.removePrefix(it).trimStart()
                if (i <= 3) {
                    processHeader(lineContent, lineCtx)
                    if (lineCtx.header) return
                } else processLine(prefix, line, trimmedLine, lineContent, lineCtx)
            } ?: lineCtx.newLines.add(line)
        }
        lineCtx.targetFile.writeText(lineCtx.newLines.joinToString("\n"))
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