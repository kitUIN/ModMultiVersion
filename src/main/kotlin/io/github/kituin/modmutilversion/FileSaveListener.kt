package io.github.kituin.modmutilversion

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
    private fun copyFile(sourceFile: File, moduleContentRoot: VirtualFile, targetFileName: String, loader: String) {
        moduleContentRoot.findDirectory(loader)?.children?.forEach { loaderFile ->
            if (loaderFile.isDirectory && loaderFile.name.startsWith(loader)) {
                val targetFile = File("${loaderFile.path}/$targetFileName")
                copy(sourceFile, targetFile, loaderFile.name)
            }
        }
    }

    private fun copy(sourceFile: File, targetFile: File, folder: String, forward: Boolean = true) {
        sourceFile.copyTo(targetFile, overwrite = true)
        val lines = targetFile.readLines()
        var inBlock = false
        var inIfBlock = false
        var inOtherBlock = false
        var inOtherIfBlock = false
        val newLines = lines.map { line ->
            when {
                line.startsWith("//") -> {
                    var l = line
                    if (l.startsWith("// IF")) {
                        if (folder in l.trim().removePrefix("// IF ").split(" || ")) {
                            inBlock = true
                            inIfBlock = true
                        }
                    } else if (l.startsWith("// ELSE IF")) {
                        inBlock = false
                        if (!inIfBlock && folder in l.trim().removePrefix("// ELSE IF").split(" || ")) {
                            inBlock = true
                            inIfBlock = true
                        }
                    } else if (l.startsWith("// ELSE")) {
                        inBlock = false
                        if (!inIfBlock){
                            inBlock = true
                            inIfBlock = true
                        }
                    } else if (l.trim() == "// END IF") {
                        inBlock = false
                        inIfBlock = false
                    } else if (inBlock && forward) {
                        l = line.removePrefix("//")
                    }
                    l
                }

                line.startsWith("#") -> {
                    var l = line
                    if (l.startsWith("# IF")) {
                        if (folder in l.trim().removePrefix("# IF ").split(" || ")) {
                            inOtherBlock = true
                            inOtherIfBlock = true
                        }
                    } else if (l.startsWith("# ELSE IF")) {
                        inOtherBlock = false
                        if (!inOtherIfBlock && folder in l.trim().removePrefix("# ELSE IF").split(" || ")) {
                            inOtherBlock = true
                            inOtherIfBlock = true
                        }
                    } else if (l.startsWith("# ELSE")) {
                        inOtherBlock = false
                        if(!inOtherIfBlock){
                            inOtherBlock = true
                            inOtherIfBlock = true
                        }
                    } else if (l == "# END IF") {
                        inOtherBlock = false
                        inOtherIfBlock = false
                    } else if (inOtherBlock && forward) {
                        l = line.removePrefix("#")
                    }
                    l
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
                println(file.path)
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
                        copyFile(sourceFile, moduleContentRoot, targetFileName, loader)
                    }
                } else {
                    val loader = loaders.firstOrNull { relativePath.startsWith(it) } ?: return
                    if (relativePath.startsWith("$loader/origin/")) {
                        val targetFileName = relativePath.removePrefix("$loader/origin/")
                        copyFile(sourceFile, moduleContentRoot, targetFileName, loader)
                    } else { // 反向更新
                        var folder: String
                        val subList = relativePath.split("/").let {
                            if (it.count() < 2) return
                            folder = it[1]
                            it.subList(2, it.count())
                        }
                        val subPath = subList.joinToString(separator = "/")
                        val forwardPath = "$projectPath/$loader/origin/$subPath"
                        if (moduleContentRoot.findFile(forwardPath) != null) {
                            // 如果有加载器特有的文件
                            copy(sourceFile, File(forwardPath), folder, false)
                        } else {
                            val target = File("$projectPath/origin/$subPath")
                            if (target.exists()) {
                                copy(sourceFile, target, folder, false)
                            }
                        }
                    }
                }

            }
        }
    }
}