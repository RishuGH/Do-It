package com.rishugh.doit.notification

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.rishugh.doit.MainActivity
import com.rishugh.doit.data.Task
import com.rishugh.doit.receiver.TodoActionReceiver

object TodoNotificationHelper {
    const val CHANNEL_ID = "todo_channel_id"
    const val NOTIFICATION_ID = 1001

    const val EXTRA_TASK_ID = "extra_task_id"
    const val KEY_TEXT_REPLY = "key_text_reply"

    const val ACTION_COMPLETE = "com.rishugh.doit.ACTION_COMPLETE"
    const val ACTION_ADD = "com.rishugh.doit.ACTION_ADD"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "To-Do List"
            val descriptionText = "Shows active to-do tasks in notification"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTodoNotification(context: Context, tasks: List<Task>) {
        createNotificationChannel(context)

        val activeTasks = tasks.filter { !it.isCompleted }
        val completedCount = tasks.count { it.isCompleted }
        val totalCount = tasks.size

        val contentIntent = Intent(context, MainActivity::class.java).let { intent ->
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_agenda)
            .setContentTitle("To-Do List ($completedCount/$totalCount done)")
            .setContentText(if (activeTasks.isEmpty()) "All tasks completed! 🎉" else "${activeTasks.size} tasks remaining")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        val inboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle("To-Do List ($completedCount/$totalCount done)")
        
        if (activeTasks.isEmpty()) {
            inboxStyle.addLine("No pending tasks! Add one below.")
        } else {
            for (task in activeTasks.take(6)) {
                inboxStyle.addLine("• ${task.title}")
            }
            if (activeTasks.size > 6) {
                inboxStyle.addLine("... and ${activeTasks.size - 6} more")
            }
        }
        builder.setStyle(inboxStyle)

        // Action to complete top pending task
        if (activeTasks.isNotEmpty()) {
            val topTask = activeTasks.first()
            val completeIntent = Intent(context, TodoActionReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TASK_ID, topTask.id)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context, topTask.id, completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val actionText = if (topTask.title.length > 15) topTask.title.take(15) + "..." else topTask.title
            builder.addAction(
                R.drawable.ic_menu_save,
                "Done: $actionText",
                completePendingIntent
            )
        }

        // Action to add task via Direct Reply (RemoteInput)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Type new task...")
            .build()

        val replyIntent = Intent(context, TodoActionReceiver::class.java).apply {
            action = ACTION_ADD
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, 0, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val addAction = NotificationCompat.Action.Builder(
            R.drawable.ic_input_add,
            "Add Task",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        builder.addAction(addAction)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
