package io.github.kituin.modmultiversion.storage

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.annotations.Nullable

@Service(Service.Level.PROJECT)
@State(name = "LoadersPluginState", storages = [Storage("ModMultiLoaders.xml")])
class LoadersPluginState : PersistentStateComponent<LoadersPluginState> {
    var loaders: MutableList<String> = mutableListOf("fabric","forge","neoforge","quilt")

    @Nullable
    override fun getState(): LoadersPluginState {
        return this
    }

    override fun loadState(state: LoadersPluginState) {
        loaders = state.loaders
    }
}
