package io.github.kituin.modmultiversion.componet

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class KeyDialog(before: String?) : DialogWrapper(true) {
    val model = Model()

    init {
        if(before != null) model.keyText = before
        init()
        title = "Enter Key"
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Key:") { textField().bindText(model::keyText) }
        }
    }

    class Model {
        var keyText: String = ""
    }

}