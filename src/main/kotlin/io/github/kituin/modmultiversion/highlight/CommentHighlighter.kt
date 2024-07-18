package io.github.kituin.modmultiversion.highlight

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import io.github.kituin.modmultiversion.Keys
import io.github.kituin.modmultiversion.LineHelper
import io.github.kituin.modmultiversioninterpreter.TokenType

class CommentCtx(var inBlock: Boolean = false, var inIfBlock: Boolean = false)


@Service
class CommentHighlighter {

    private fun commentStart(startOffset: Int, commentStart: String): Pair<TextRange, TextAttributesKey> {
        return Pair(TextRange(startOffset, startOffset + commentStart.length), COMMENT_START)
    }

    private fun keywordAdd(
        startOffset: Int,
        firstIndex: Int,
        length: Int
    ): Pair<TextRange, TextAttributesKey> {
        return Pair(
            TextRange(startOffset + firstIndex, startOffset + firstIndex + length),
            DefaultLanguageHighlighterColors.KEYWORD
        )
    }

    fun getHighlights(text: String, startOffset: Int, filePath: String): List<Pair<TextRange, TextAttributesKey>> {
        val highlightAnnotationData = mutableListOf<Pair<TextRange, TextAttributesKey>>()
        val commentStart = LineHelper.isComment(text) ?: return highlightAnnotationData
        if (text.length == commentStart.length) return highlightAnnotationData
        var firstIndex = text.substring(commentStart.length).indexOfFirst { it != ' ' }.takeIf { it >= 0 } ?: 0
        firstIndex += commentStart.length
        val body = text.substring(firstIndex)
        println(body)
        val mark = MARKS.getOrDefault(filePath, false)
        val key: Keys? = Keys.entries.firstOrNull { body.startsWith(it.value) }
        if (key == null && mark || key != null) highlightAnnotationData.add(
            commentStart(startOffset, commentStart)
        )
        if (key == null) return highlightAnnotationData
        highlightAnnotationData.add(keywordAdd(startOffset, firstIndex, key.value.length))
        firstIndex += key.value.length
        if (key == Keys.IF) {
            MARKS[filePath] = true
        }else if (key == Keys.END_IF){
            MARKS[filePath]=false
        }
        while (firstIndex < text.length) {
            KEYWORDS.firstOrNull { keyword ->
                text.startsWith(keyword, firstIndex) && keyword.length <= text.length - firstIndex
            }?.also { keyword ->
                highlightAnnotationData.add(keywordAdd(startOffset, firstIndex, keyword.length))
                firstIndex += keyword.length - 1
            }
            firstIndex++
        }
        return highlightAnnotationData
    }


    companion object {
        @JvmStatic
        val MARKS: MutableMap<String, Boolean> = mutableMapOf()

        @JvmStatic
        val COMMENT_START: TextAttributesKey = createTextAttributesKey("PREFIX_COMMENT")

        val KEYWORDS = TokenType.entries.map { it.value }.sortedByDescending { it.length }
    }
}
