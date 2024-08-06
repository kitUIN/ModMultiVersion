package io.github.kituin.modmultiversion.highlight

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.util.TextRange
import io.github.kituin.modmultiversiontool.Keys
import io.github.kituin.modmultiversiontool.LineHelper
import io.github.kituin.modmultiversioninterpreter.*

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
        addData(text, firstIndex, startOffset, holder, highlightAnnotationData)
        return highlightAnnotationData
    }

    private fun addData(
        text: String,
        firstIndex: Int,
        startOffset: Int,
        holder: AnnotationHolder,
        highlightAnnotationData: MutableList<AnnotationBuilder>
    ) {
        try {
            val parser = Parser(Lexer(text.substring(firstIndex)))
            for (k in 0 until parser.tokenList.size) {
                val it = parser.tokenList[k]
                if (it.startPos < 0) continue
                var pa = parseAdd(startOffset + firstIndex + it.startPos, it, holder)

                if (it.type == TokenType.STRING) {
                    pa = tooltipString(it, pa, k, parser)
                } else if (
                    it.type == TokenType.GREATER ||
                    it.type == TokenType.LESS ||
                    it.type == TokenType.LESS_EQUAL ||
                    it.type == TokenType.GREATER_EQUAL||
                    it.type == TokenType.ALSO_EQUAL ||
                    it.type == TokenType.NOT_EQUAL
                    ) {
                    if (k >= 1 && k < parser.tokenList.size - 2 &&
                        parser.tokenList[k - 1].startPos == -1 &&
                        parser.tokenList[k - 1].value == "$$" &&
                        parser.tokenList[k + 1].startPos != -1 &&
                        parser.tokenList[k + 1].type == TokenType.STRING
                        ) {
                        pa = pa.tooltip("$$ ${it.value} ${parser.tokenList[k + 1].value}的简写")
                    }
                }
                highlightAnnotationData.add(pa)
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
    }

    private fun tooltipString(it: Token, pa: AnnotationBuilder, k: Int, parser: Parser): AnnotationBuilder {
        if (it.value.startsWith("$")) return pa
        else if (k >= 2 && parser.tokenList[k - 2].startPos == -1 && parser.tokenList[k - 2].value == "$$") {
            return pa.tooltip("$$ ${parser.tokenList[k - 1].value} ${it.value}的简写")
        }
        return pa
    }


    companion object {
        @JvmStatic
        val MARKS: MutableMap<String, Boolean> = mutableMapOf()

        @JvmStatic
        val COMMENT_START: TextAttributesKey = createTextAttributesKey("PREFIX_COMMENT")

        val KEYWORDS = TokenType.entries.map { it.value }.sortedByDescending { it.length }
    }
}
