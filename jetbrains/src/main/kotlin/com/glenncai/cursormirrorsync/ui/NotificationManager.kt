package com.glenncai.cursormirrorsync.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.glenncai.cursormirrorsync.core.Constants

/**
 * Manages notifications for the Cursor Mirror Sync plugin.
 * Handles notification throttling and provides convenient methods for common notification types.
 */
class NotificationManager(private val project: Project) {
    
    companion object {
        private const val NOTIFICATION_GROUP_ID = Constants.NOTIFICATION_GROUP_ID
        private const val CONFIG_NOTIFICATION_GROUP_ID = Constants.CONFIG_NOTIFICATION_GROUP_ID
        private const val NOTIFICATION_THROTTLE_MS = Constants.NOTIFICATION_THROTTLE_MS
        private const val INFO_NOTIFICATION_THROTTLE_MS = Constants.INFO_NOTIFICATION_THROTTLE_MS
        private const val MESSAGE_HASH_CLEANUP_INTERVAL_MS = Constants.MESSAGE_HASH_CLEANUP_INTERVAL_MS
    }

    private val log: Logger = Logger.getInstance(NotificationManager::class.java)

    // Notification throttling
    private var lastConnectedNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
    private var lastDisconnectedNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
    private var lastErrorNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
    private var lastInfoNotificationTime = Constants.INITIAL_NOTIFICATION_TIME

    // Message content deduplication
    private val recentMessageHashes = mutableMapOf<Int, Long>()
    private var lastHashCleanupTime = Constants.INITIAL_NOTIFICATION_TIME

    /**
     * Shows a throttled notification to prevent spam.
     * Connection and disconnection notifications are throttled separately.
     */
    fun showThrottledNotification(message: String, type: NotificationType, isConnected: Boolean) {
        val currentTime = System.currentTimeMillis()

        // Check if we should throttle this notification
        val shouldShow = if (isConnected) {
            currentTime - lastConnectedNotificationTime > NOTIFICATION_THROTTLE_MS
        } else {
            currentTime - lastDisconnectedNotificationTime > NOTIFICATION_THROTTLE_MS
        }

        if (shouldShow) {
            // Update the last notification time
            if (isConnected) {
                lastConnectedNotificationTime = currentTime
            } else {
                lastDisconnectedNotificationTime = currentTime
            }

            // Show the notification
            showNotification(message, type, NOTIFICATION_GROUP_ID)
        }
    }

    /**
     * Shows an immediate notification without throttling.
     * Used for configuration changes and other important updates.
     */
    fun showImmediateNotification(message: String, type: NotificationType, groupId: String = CONFIG_NOTIFICATION_GROUP_ID) {
        ApplicationManager.getApplication().invokeLater {
            showNotification(message, type, groupId)
        }
    }

    /**
     * Shows a connection status notification.
     */
    fun showConnectionNotification(isConnected: Boolean) {
        val message = if (isConnected) "Connected to VSCode" else "Disconnected from VSCode"
        val type = if (isConnected) NotificationType.INFORMATION else NotificationType.WARNING
        showThrottledNotification(message, type, isConnected)
    }

    /**
     * Shows a configuration change notification.
     */
    fun showConfigurationChangeNotification(settingName: String, enabled: Boolean) {
        val status = if (enabled) "enabled" else "disabled"
        val message = "$settingName $status by VSCode"
        showImmediateNotification(message, NotificationType.INFORMATION)
    }

    /**
     * Shows an error notification with deduplication.
     */
    fun showErrorNotification(message: String) {
        val currentTime = System.currentTimeMillis()
        if (shouldShowDeduplicatedNotification(message, NOTIFICATION_THROTTLE_MS, lastErrorNotificationTime, currentTime)) {
            lastErrorNotificationTime = currentTime
            showImmediateNotification(message, NotificationType.ERROR)
        }
    }

    /**
     * Shows a warning notification.
     */
    fun showWarningNotification(message: String) {
        showThrottledNotification(message, NotificationType.WARNING, false)
    }

    /**
     * Shows an information notification with deduplication.
     */
    fun showInfoNotification(message: String) {
        val currentTime = System.currentTimeMillis()
        if (shouldShowDeduplicatedNotification(message, INFO_NOTIFICATION_THROTTLE_MS, lastInfoNotificationTime, currentTime)) {
            lastInfoNotificationTime = currentTime
            showImmediateNotification(message, NotificationType.INFORMATION)
        }
    }

    /**
     * Shows a notification for maximum reconnection attempts reached.
     */
    fun showMaxReconnectAttemptsNotification() {
        showWarningNotification("Max reconnection attempts reached")
    }

    /**
     * Shows a notification for selection sync status change.
     */
    fun showSelectionSyncNotification(enabled: Boolean) {
        showConfigurationChangeNotification("Text selection sync", enabled)
    }

    /**
     * Checks if a notification should be shown based on time throttling and content deduplication.
     */
    private fun shouldShowDeduplicatedNotification(message: String, throttleMs: Long, lastNotificationTime: Long, currentTime: Long): Boolean {
        // Early return for time-based throttling
        if (currentTime - lastNotificationTime <= throttleMs) {
            return false
        }

        // Early return for content-based deduplication
        val messageHash = message.hashCode()
        val lastHashTime = recentMessageHashes[messageHash]
        if (lastHashTime != null && currentTime - lastHashTime <= throttleMs) {
            return false
        }

        // Update message hash cache and perform cleanup
        recentMessageHashes[messageHash] = currentTime
        cleanupOldMessageHashes(currentTime)

        return true
    }

    /**
     * Cleans up old message hashes to prevent memory accumulation.
     */
    private fun cleanupOldMessageHashes(currentTime: Long) {
        if (currentTime - lastHashCleanupTime > MESSAGE_HASH_CLEANUP_INTERVAL_MS) {
            val iterator = recentMessageHashes.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                // Remove hashes older than the maximum throttle time
                if (currentTime - entry.value > NOTIFICATION_THROTTLE_MS) {
                    iterator.remove()
                }
            }
            lastHashCleanupTime = currentTime
        }
    }

    /**
     * Core method to show a notification.
     */
    private fun showNotification(message: String, type: NotificationType, groupId: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(groupId)
                .createNotification(message, type)
                .notify(project)

        } catch (e: Exception) {
            log.error("Failed to show notification: $message", e)
        }
    }

    /**
     * Resets notification throttling timers and clears message hash cache.
     * Useful for testing or when you want to ensure the next notification is shown.
     */
    fun resetThrottling() {
        lastConnectedNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
        lastDisconnectedNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
        lastErrorNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
        lastInfoNotificationTime = Constants.INITIAL_NOTIFICATION_TIME
        lastHashCleanupTime = Constants.INITIAL_NOTIFICATION_TIME
        recentMessageHashes.clear()
    }
}
