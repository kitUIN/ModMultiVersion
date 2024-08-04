package io.github.kituin.modmultiversion.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDirectory
import io.github.kituin.modmultiversion.LoadersPluginState

class AddLoader : AnAction("Set A Folder As A Listening Loader") {
    override fun actionPerformed(e: AnActionEvent) {
        if (e.project == null) return
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        val dir = psiElement as PsiDirectory
        val service = e.project!!.service<LoadersPluginState>()
        if (service.loaders.contains(dir.virtualFile.name)) {
            service.loaders.remove(dir.virtualFile.name)
        } else {
            service.loaders.add(dir.virtualFile.name)
        }
    }

    override fun update(e: AnActionEvent) {
        if (e.project == null) return
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.presentation.isEnabledAndVisible = psiElement is PsiDirectory
        val dir = psiElement as PsiDirectory
        if (e.project!!.service<LoadersPluginState>().loaders.contains(dir.virtualFile.name)) {
            e.presentation.text = "取消文件夹${dir.virtualFile.name}为监听的加载器"
        } else {
            e.presentation.text = "将文件夹${dir.virtualFile.name}设置为监听的加载器"
        }
    }
}