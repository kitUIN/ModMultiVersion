package io.github.kituin.modmultiversion.storage;

import org.jetbrains.annotations.Nullable
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property

@Service(Service.Level.PROJECT)
@State(name = "AliasState", storages = [Storage("ModAliasState.xml")])
class AliasState : PersistentStateComponent<AliasState> {
    @Property
    var alias: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    @Nullable
    override fun getState(): AliasState {
        return this
    }

    override fun loadState(state: AliasState) {
        alias = state.alias
    }
}