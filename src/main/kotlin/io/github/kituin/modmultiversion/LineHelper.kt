package io.github.kituin.modmultiversion

import io.github.kituin.modmultiversioninterpreter.Interpreter


class LineHelper {
    companion object {
        @JvmStatic
        fun isComment(line: String): String? = when {
            line.startsWith("//") -> "//"
            line.startsWith("#") -> "#"
            else -> null
        }


        @JvmStatic
        fun haveKey(line: String, key: Keys, unknownComment: Boolean = false): Boolean {
            if (unknownComment) {
                isComment(line)?.let {
                    return line.removePrefix(it).trimStart().startsWith(key.value)
                } ?: return false
            }
            return line.startsWith(key.value)
        }


        @JvmStatic
        fun interpret(line: String, key: Keys, replacement: Map<String, String>): Boolean =
            Interpreter(line.removePrefix(key.value), replacement).interpret()

    }
}