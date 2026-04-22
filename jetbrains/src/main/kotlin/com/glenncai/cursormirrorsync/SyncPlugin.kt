package com.glenncai.cursormirrorsync

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.glenncai.cursormirrorsync.core.SyncService

class SyncPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<SyncService>()
    }
}
