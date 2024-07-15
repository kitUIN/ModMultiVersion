package io.github.kituin.modmutilversion

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "syncSetting")
class SyncSetting(private val project: Project) : SimplePersistentStateComponent<SyncSettingState>(SyncSettingState()) {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): SyncSetting = project.service()
    }
}

