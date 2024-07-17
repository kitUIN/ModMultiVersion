package io.github.kituin.modmultiversion

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*


class MyDialogWrapper(
    private val project: Project,
    private val isBlack: Boolean,
    private val path: String
) : DialogWrapper(true) {
    private val panel = JPanel()
    private val defaultListModel = DefaultListModel<String>()
    private val list = JBList(defaultListModel)
    private val textField = JBTextField()

    init {
        title = "输入字符串列表"
        list.setEmptyText("没有字符串")

        val listPanel = JPanel(BorderLayout())
        listPanel.add(JScrollPane(list), BorderLayout.CENTER)
        listPanel.preferredSize = Dimension(400, 200)

        val inputPanel = JPanel()
        inputPanel.layout = BoxLayout(inputPanel, BoxLayout.X_AXIS)
        inputPanel.add(textField)
        inputPanel.add(JButton("添加").apply {
            addActionListener {
                val text = textField.text
                if (text.isNotBlank()) {
                    // 添加字符串到列表中
                    defaultListModel.addElement(text)
                    textField.text = ""
                }
            }
        })

        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(listPanel)
        panel.add(inputPanel)

        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel
    }

    override fun doOKAction() {
        if (list.isEmpty) {
            Messages.showMessageDialog(
                "列表不能为空",
                "错误",
                Messages.getErrorIcon()
            )
        } else {
            if (isBlack) {
                project.service<SyncSetting>().state.black[path] = defaultListModel.toArray().toList() as List<String>
            } else {
                project.service<SyncSetting>().state.white[path] = defaultListModel.toArray().toList() as List<String>
            }
            super.doOKAction()
        }
    }
}
