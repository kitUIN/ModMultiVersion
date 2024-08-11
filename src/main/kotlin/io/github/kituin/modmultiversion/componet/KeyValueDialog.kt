package io.github.kituin.modmultiversion.componet

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class KeyValueDialog(key:String?,value:String?) : DialogWrapper(true) {
    val model = Model()
    init {
        if(key!=null && value!=null)
        {
            model.keyText = key
            model.valueText = value
        }
        init()
        title = "Enter Key and Value"
    }

    override fun createCenterPanel(): JComponent {
        val contentPanel = panel {
            row("Key:") { textField().bindText(model::keyText) }
            row("Value:") { textField().bindText(model::valueText) }
        }
        return contentPanel
    }

    class Model {
        var keyText: String = ""
        var valueText: String = ""
    }

}