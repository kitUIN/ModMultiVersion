package io.github.kituin.modmultiversion.window

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import io.github.kituin.modmultiversion.componet.KeyDialog
import io.github.kituin.modmultiversion.componet.KeyValueDialog
import io.github.kituin.modmultiversion.storage.AliasState
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


internal class AliasToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = CalendarToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class CalendarToolWindowContent(val project: Project) {
        val contentPanel: JPanel = JPanel()
        val tree: Tree = Tree()

        init {
            contentPanel.layout = BorderLayout(0, 20)
            contentPanel.add(createTree(), BorderLayout.CENTER)
            refreshTree()
        }

        private fun refreshTree() {
            val root = DefaultMutableTreeNode("root")
            val service = project.getService(AliasState::class.java)
            for ((key, values) in service.alias) {
                val k = DefaultMutableTreeNode(key)
                if (values != null) {
                    for ((k1, v1) in values) {
                        val v = DefaultMutableTreeNode(k1)
                        v.add(DefaultMutableTreeNode(v1))
                        k.add(v)
                    }
                }
                root.add(k)
            }
            tree.model = DefaultTreeModel(root)
        }

        private fun createActionGroup(): DefaultActionGroup {
            val actionGroup = DefaultActionGroup()
            actionGroup.add(object : AnAction("新增") {
                override fun actionPerformed(e: AnActionEvent) {
                    e.project?.let {
                        val (level, _) = getLevel(e)
                        if (level == 0) {
                            addKey(it)
                        } else if (level == 1) {
                            addKeyValue(it, e, true)
                        }
                        refreshTree()
                    }
                }

                override fun update(e: AnActionEvent) {
                    val (level, _) = getLevel(e)
                    e.presentation.isEnabledAndVisible = level in 0..1
                }
            })
            actionGroup.add(object : AnAction("修改") {
                override fun actionPerformed(e: AnActionEvent) {
                    e.project?.let {
                        val (level, _) = getLevel(e)
                        if (level == 1) {
                            addKey(it, e)
                        } else if (level == 2) {
                            addKeyValue(it, e)
                        }
                        refreshTree()
                    }
                }

                override fun update(e: AnActionEvent) {
                    val (level, _) = getLevel(e)
                    e.presentation.isEnabledAndVisible = level in 1..2
                }
            })
            actionGroup.add(object : AnAction("删除") {
                override fun actionPerformed(e: AnActionEvent) {
                    e.project?.let {
                        delKey(it, e)
                        refreshTree()
                    }
                }

                override fun update(e: AnActionEvent) {
                    val (level, _) = getLevel(e)
                    e.presentation.isEnabledAndVisible = level in 1..2
                }
            })
            return actionGroup
        }

        private fun delKey(it: Project, e: AnActionEvent? = null) {
            val service = it.getService(AliasState::class.java)
            val (level, node) = getLevel(e)
            if (node == null) return
            val name = node.userObject as String
            if (level == 1) {
                service.alias.remove(name)
            } else if (level == 2) {
                val parent = node.parent?.let { (it as DefaultMutableTreeNode).userObject as String }
                if (parent == null) return
                service.alias[parent]?.remove(name)
            }
        }

        private fun addKey(it: Project, e: AnActionEvent? = null) {
            val service = it.getService(AliasState::class.java)
            val (_, node) = getLevel(e)
            val before = (node?.userObject as? String)?.let { it.substring(1, it.length - 1) }
            val dialog = KeyDialog(before)
            if (dialog.showAndGet()) {
                if (dialog.model.keyText.isBlank()) {
                    Messages.showMessageDialog(
                        "key不能为空",
                        "Warn",
                        Messages.getWarningIcon()
                    )
                    return
                }
                val key = "#${dialog.model.keyText}#"
                if (!service.alias.containsKey(key)) {
                    if (before != null) {
                        service.alias[key] = service.alias["#$before#"] ?: mutableMapOf()
                        service.alias.remove("#$before#")
                    } else {
                        service.alias[key] = mutableMapOf()
                    }
                } else {
                    Messages.showMessageDialog(
                        "键${key}已经存在",
                        "Warn",
                        Messages.getWarningIcon()
                    )
                }
            }
        }

        private fun addKeyValue(it: Project, e: AnActionEvent, is_new: Boolean = false) {
            val service = it.getService(AliasState::class.java)
            val (_, node) = getLevel(e)
            if (node == null) return
            val parent = if (is_new) node.userObject as String else (node.parent as DefaultMutableTreeNode).userObject as String
            val (beforeKey, beforeValue) = if (!is_new) {
                node.userObject as String to service.alias[parent]?.get(node.userObject as String)
            } else {
                null to null
            }
            val dialog = KeyValueDialog(beforeKey,beforeValue)
            if (dialog.showAndGet()) {
                val key = dialog.model.keyText
                val value = dialog.model.valueText
                if (key.isBlank()) {
                    Messages.showMessageDialog(
                        "key不能为空",
                        "Warn",
                        Messages.getWarningIcon()
                    )
                    return
                }
                if (service.alias[parent] == null) {
                    service.alias[parent] = mutableMapOf(key to value)
                } else {
                    service.alias[parent]!![key] = value
                }
                if (!is_new) service.alias[parent]!!.remove(beforeKey)
            }
        }

        private fun getLevel(e: AnActionEvent?): Pair<Int, DefaultMutableTreeNode?> {
            if (e == null) return Pair(-1, null)
            val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree
            if (tree != null) {
                // 获取右键点击的节点
                val selectedPath = tree.selectionPath
                if (selectedPath != null) {
                    val node = selectedPath.lastPathComponent as DefaultMutableTreeNode
                    val level = selectedPath.pathCount - 1 // 获取节点层级
//                    println("node: ${node.userObject}, level: $level")
                    return Pair(level, node)
                }
            }
            return Pair(-1, null)
        }

        private fun createTree(): Tree {
            tree.setDragEnabled(true)
            tree.setExpandableItemsEnabled(true)
            PopupHandler.installPopupMenu(tree, createActionGroup(), ActionPlaces.UNKNOWN)
            return tree
        }


    }
}

