package com.glenncai.cursormirrorsync.core

import com.intellij.util.messages.Topic

/**
 * Listener contract for sync state change events published by [SyncService].
 * UI components (e.g. the status bar widget) subscribe through the project
 * message bus so the core service never needs a direct UI reference.
 */
fun interface SyncStateListener {
    fun onSyncStateChanged()
}

/**
 * MessageBus topic used to broadcast sync state changes across the project.
 * Keeping the topic in the core layer lets UI components depend on it without
 * the core layer depending on any UI type in return.
 */
object SyncStateTopic {
    @JvmField
    val TOPIC: Topic<SyncStateListener> = Topic.create(
        "CursorMirrorSync.SyncState",
        SyncStateListener::class.java
    )
}
