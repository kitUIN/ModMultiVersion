package io.github.kituin.modmultiversion

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Comparing
import io.github.kituin.modmultiversion.storage.LoadersPluginState
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

fun Boolean.toInt() = if (this) 1 else 0
fun Int.toBoolean() = this == 1

class ModMultiVersionConfig(private val project: Project) : SearchableConfigurable {
    private val mainPanel: JPanel
    private val commentModeOptions = arrayOf(
        ModMultiVersionBundle.message("settings.commentMode.home"),
        ModMultiVersionBundle.message("settings.commentMode.code"))
    private val commentWithOneSpaceOptions = arrayOf(
        ModMultiVersionBundle.message("settings.commentWithOneSpace.false"),
        ModMultiVersionBundle.message("settings.commentWithOneSpace.true"))
    private val commentMode = ComboBox(commentModeOptions)
    private val commentWithOneSpace = ComboBox(commentWithOneSpaceOptions)
    private var commentModeConfig: Int = 0
    private var commentWithOneSpaceConfig: Int = 0

    override fun isModified(): Boolean = isModified(commentMode, commentModeConfig)

    private fun isModified(comboBox: ComboBox<String>, value: Int): Boolean {
        return !Comparing.equal(comboBox.selectedIndex, value)
    }

    override fun getDisplayName(): String = ModMultiVersionBundle.message("projectName")
    override fun getId(): String = "io.github.kituin.modmultiversion.config"

    init {
        val flow = GridLayout(20, 2)
        mainPanel = JPanel(flow)
        val commentModePanel = JPanel(FlowLayout(FlowLayout.LEADING))
        commentModePanel.add(JLabel(ModMultiVersionBundle.message("settings.commentMode")))
        commentModePanel.add(commentMode, null)
        commentModePanel.add(commentWithOneSpace, null)
        commentMode.addActionListener {
            run {
                if (commentMode.getSelectedIndex() == 1) {
                    commentWithOneSpace.setEnabled(true);
                } else {
                    commentWithOneSpace.setEnabled(false);
                }
            }
        }
        mainPanel.add(commentModePanel)
    }

    override fun apply() {
        commentModeConfig = when (commentMode.selectedIndex) {
            0 -> 0
            1 -> 1
            else -> 0
        }
        project.getService(LoadersPluginState::class.java).commentBeforeCode = commentModeConfig.toBoolean()
        commentWithOneSpaceConfig = when (commentWithOneSpace.selectedIndex) {
            0 -> 0
            1 -> 1
            else -> 0
        }
        project.getService(LoadersPluginState::class.java).commentWithOneSpace = commentWithOneSpaceConfig.toBoolean()
    }

    override fun reset() {
        commentModeConfig = project.getService(LoadersPluginState::class.java).commentBeforeCode.toInt()
        commentMode.selectedIndex = commentModeConfig
        commentWithOneSpaceConfig = project.getService(LoadersPluginState::class.java).commentWithOneSpace.toInt()
        commentWithOneSpace.selectedIndex = commentWithOneSpaceConfig
    }

    override fun createComponent(): JComponent = mainPanel
}