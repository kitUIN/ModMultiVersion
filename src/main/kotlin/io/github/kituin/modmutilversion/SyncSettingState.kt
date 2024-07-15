package io.github.kituin.modmutilversion

import com.intellij.openapi.components.BaseState

class SyncSettingState : BaseState() {
    var white = HashMap<String,List<String>>()
    var black = HashMap<String,List<String>>()
}