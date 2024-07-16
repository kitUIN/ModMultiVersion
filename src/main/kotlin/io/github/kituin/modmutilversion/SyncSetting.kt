package io.github.kituin.modmutilversion

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "syncSetting", storages = [Storage("syncSetting.xml")])
class SyncSetting : PersistentStateComponent<SyncSetting> {
    var white = HashMap<String, List<String>>()
    var black = HashMap<String, List<String>>()
    var oneWay = ArrayList<String>()
    var replaceJson5: Boolean = true
    override fun getState(): SyncSetting {
        return this
    }

    override fun loadState(state: SyncSetting) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

