package com.agentworkspace.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.agentworkspace.MainActivity
import com.agentworkspace.R
import com.agentworkspace.runtime.domain.RunStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunNotificationController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Agent runs",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress and controls for active AgentWorkspace tasks"
                setShowBadge(false)
            },
        )
    }

    fun build(runId: String, taskTitle: String, status: RunStatus): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(taskTitle.ifBlank { "AgentWorkspace is working" })
            .setContentText(status.displayText())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(runId))
            .setOnlyAlertOnce(true)
            .setOngoing(!status.isTerminal)
            .setAutoCancel(status.isTerminal)

        if (!status.isTerminal) {
            if (status == RunStatus.PAUSED) {
                builder.addAction(0, "Resume", serviceIntent(runId, AgentRunService.ACTION_RESUME, 1))
            } else {
                builder.addAction(0, "Pause", serviceIntent(runId, AgentRunService.ACTION_PAUSE, 1))
            }
            builder.addAction(0, "Cancel", serviceIntent(runId, AgentRunService.ACTION_CANCEL, 2))
        }

        return builder.build()
    }

    fun notificationId(runId: String): Int =
        runId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)

    fun show(runId: String, taskTitle: String, status: RunStatus) {
        manager.notify(notificationId(runId), build(runId, taskTitle, status))
    }

    fun cancel(runId: String) {
        manager.cancel(notificationId(runId))
    }

    private fun openAppIntent(runId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(AgentRunService.EXTRA_RUN_ID, runId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            notificationId(runId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(runId: String, action: String, offset: Int): PendingIntent {
        val intent = Intent(context, AgentRunService::class.java)
            .setAction(action)
            .putExtra(AgentRunService.EXTRA_RUN_ID, runId)
        return PendingIntent.getService(
            context,
            notificationId(runId) + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun RunStatus.displayText(): String =
        name.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }

    private companion object {
        const val CHANNEL_ID = "agent_runs"
    }
}
