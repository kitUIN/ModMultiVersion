package io.github.kituin.modmultiversion.highlight

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText

class GenericCommentHighlighterAnnotator : AbstractCommentHighlighterAnnotator() {
    override fun isCommentHighlightingElement(element: PsiElement): Boolean {
        return isCommentType(element) || isPlainTextHighlight(element)
    }

    private fun isPlainTextHighlight(element: PsiElement): Boolean {
        return element is PsiPlainText
    }

    private fun isCommentType(element: PsiElement): Boolean {
        return element is PsiComment
    }
}
