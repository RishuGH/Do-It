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
    const val TASK_NOTIFICATION_BASE_ID = 2000

    const val EXTRA_TASK_ID = "extra_task_id"
    const val KEY_TEXT_REPLY = "key_text_reply"

    const val ACTION_COMPLETE = "com.rishugh.doit.ACTION_COMPLETE"
    const val ACTION_DELETE = "com.rishugh.doit.ACTION_DELETE"
    const val ACTION_ADD_BELOW = "com.rishugh.doit.ACTION_ADD_BELOW"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "To-Do List Tasks"
            val descriptionText = "Individual notifications for each to-do task"
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
        val notificationManager = NotificationManagerCompat.from(context)

        val activeTasks = tasks.filter { !it.isCompleted }
        val completedTasks = tasks.filter { it.isCompleted }

        // 1. Cancel notifications for completed or removed tasks
        for (task in completedTasks) {
            try {
                notificationManager.cancel(TASK_NOTIFICATION_BASE_ID + task.id)
            } catch (e: Exception) {
                // ignore
            }
        }

        // 2. Post a separate notification card for each active task
        for (task in activeTasks) {
            val notificationId = TASK_NOTIFICATION_BASE_ID + task.id

            // Tapping notification body opens the app to this task
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context, notificationId, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_agenda)
                .setContentTitle(task.title)
                .setContentText("Tap actions below to complete, delete, or add task below")
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)

            // Action 1: Done
            val completeIntent = Intent(context, TodoActionReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context, notificationId * 10 + 1, completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_menu_save,
                "✓ Done",
                completePendingIntent
            )

            // Action 2: Delete
            val deleteIntent = Intent(context, TodoActionReceiver::class.java).apply {
                action = ACTION_DELETE
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val deletePendingIntent = PendingIntent.getBroadcast(
                context, notificationId * 10 + 2, deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_menu_delete,
                "🗑 Delete",
                deletePendingIntent
            )

            // Action 3: Add Task Below via RemoteInput
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Add task below...")
                .build()

            val addIntent = Intent(context, TodoActionReceiver::class.java).apply {
                action = ACTION_ADD_BELOW
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val addPendingIntent = PendingIntent.getBroadcast(
                context, notificationId * 10 + 3, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val addAction = NotificationCompat.Action.Builder(
                R.drawable.ic_input_add,
                "+ Add",
                addPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

            builder.addAction(addAction)

            try {
                notificationManager.notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        for (i in 0..100) {
            try {
                notificationManager.cancel(TASK_NOTIFICATION_BASE_ID + i)
            } catch (e: Exception) {
            }
        }
    }
}
