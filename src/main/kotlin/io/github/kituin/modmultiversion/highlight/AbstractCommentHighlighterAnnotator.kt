package io.github.kituin.modmultiversion.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement

abstract class AbstractCommentHighlighterAnnotator : Annotator {
    private val commentHighlighter: CommentHighlighter = ApplicationManager.getApplication().getService(
        CommentHighlighter::class.java
    )

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (isCommentHighlightingElement(element)) {
            val comment = extractCommentTextFromElement(element)
            val startOffset = element.textRange.startOffset
            val filePath = element.containingFile.virtualFile.path
            val basePath = element.project.basePath
            val highlights = commentHighlighter.getHighlights(
                comment, startOffset,
                filePath,
                filePath.removePrefix("$basePath/"),
                holder
            )
            for (highlight in highlights) {
                highlight.create()
            }
        }
    }

    protected fun extractCommentTextFromElement(element: PsiElement): String {
        return element.text
    }

    protected abstract fun isCommentHighlightingElement(element: PsiElement): Boolean
}
