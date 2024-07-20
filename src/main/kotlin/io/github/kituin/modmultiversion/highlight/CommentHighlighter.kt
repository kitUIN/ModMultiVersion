package io.github.kituin.modmultiversion.highlight

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import io.github.kituin.modmultiversion.Keys
import io.github.kituin.modmultiversion.LineHelper
import io.github.kituin.modmultiversioninterpreter.*

class CommentCtx(var inBlock: Boolean = false, var inIfBlock: Boolean = false)


@Service
class CommentHighlighter {

    private fun commentStart(startOffset: Int, length: Int, holder: AnnotationHolder): AnnotationBuilder {
        return holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, startOffset + length))
            .textAttributes(COMMENT_START)
    }

    private fun parseAdd(startOffset: Int, token: Token, holder: AnnotationHolder): AnnotationBuilder {
        return holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, startOffset + token.value.length))
            .textAttributes(
                when (token.belong) {
                    TokenBelong.VARIANT -> DefaultLanguageHighlighterColors.LOCAL_VARIABLE
                    TokenBelong.BRACKETS -> DefaultLanguageHighlighterColors.BRACKETS
                    TokenBelong.OPERATOR -> DefaultLanguageHighlighterColors.KEYWORD
                    else -> DefaultLanguageHighlighterColors.STRING
                }
            )
    }

    private fun parseError(
        startOffset: Int,
        token: Token,
        holder: AnnotationHolder,
        message: String
    ): AnnotationBuilder {
        return holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(TextRange(startOffset, startOffset + token.value.length))

    }

    private fun keywordAdd(startOffset: Int, length: Int, holder: AnnotationHolder): AnnotationBuilder {
        return holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, startOffset + length))
            .textAttributes(DefaultLanguageHighlighterColors.KEYWORD)
    }

    fun getHighlights(
        text: String,
        startOffset: Int,
        filePath: String,
        projectFilePath: String,
        holder: AnnotationHolder
    ): List<AnnotationBuilder> {
        val highlightAnnotationData = mutableListOf<AnnotationBuilder>()
        val commentStart = LineHelper.isComment(text) ?: return highlightAnnotationData
        if (text.length == commentStart.length) return highlightAnnotationData
        var firstIndex = text.substring(commentStart.length).indexOfFirst { it != ' ' }.takeIf { it >= 0 } ?: 0
        firstIndex += commentStart.length
        val body = text.substring(firstIndex)
        val mark = MARKS.getOrDefault(filePath, false)
        val key: Keys? = Keys.entries.firstOrNull { body.startsWith(it.value) }
        if (key == null && mark || key != null) highlightAnnotationData.add(
            commentStart(startOffset, commentStart.length, holder)
        )
        if (key == null) return highlightAnnotationData
        highlightAnnotationData.add(keywordAdd(startOffset + firstIndex, key.value.length, holder))
        firstIndex += key.value.length
        if (key == Keys.IF) {
            MARKS[filePath] = true
        } else if (key == Keys.END_IF) {
            MARKS[filePath] = false
        }
        try {
            val parser = Parser(Lexer(text.substring(firstIndex)))
            parser.tokenList.forEach {
                if (it.startPos >= 0) highlightAnnotationData.add(
                    parseAdd(startOffset + firstIndex + it.startPos, it, holder)
                )
            }
            parser.parse()
        } catch (e: ParseException) {
            if (e.token.type != TokenType.EOF) {
                highlightAnnotationData.add(
                    parseError(
                        startOffset + firstIndex + e.token.startPos,
                        e.token,
                        holder,
                        "Error"
                    )
                )
            }
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
