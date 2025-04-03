package io.github.kituin.modmultiversion.tool

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.writeBytes
import io.github.kituin.modmultiversiontool.IFileCopyWorker
import java.nio.file.Path
import kotlin.io.path.pathString

class VFileCopyWorker(private val moduleContentRoot: VirtualFile) : IFileCopyWorker {


    override fun copy(targetFilePath: Path, content: ByteArray) {
        moduleContentRoot.findFile(targetFilePath.pathString)?.let {
//            println("Copying ${targetFilePath.pathString} to $it")
            it.writeBytes(content)
            it.refresh(false, true)
        }
    }

    override fun isSame(targetFilePath: Path, content: ByteArray): Boolean {
        moduleContentRoot.findFile(targetFilePath.pathString)?.let {
//            println("isSame ${targetFilePath.pathString} to $it")
            return it.readBytes().contentEquals(content)
        }
        return true
    }
}