package com.rishugh.doit.notification

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    const val ACTION_ADD_TOP = "com.rishugh.doit.ACTION_ADD_TOP"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "To-Do List Tasks"
            val descriptionText = "Individual notifications for each to-do task"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildTaskNotification(
        context: Context,
        task: Task,
        whenTimestamp: Long = System.currentTimeMillis()
    ): Notification {
        createNotificationChannel(context)
        val notificationId = TASK_NOTIFICATION_BASE_ID + task.id

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.id)
            data = Uri.parse("todo://main/${task.id}")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_agenda)
            .setContentTitle(task.title)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setWhen(whenTimestamp)
            .setShowWhen(false)

        // Action 1: Done (Compact Emoji)
        val completeIntent = Intent(context, TodoActionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra(EXTRA_TASK_ID, task.id)
            data = Uri.parse("todo://action/complete/${task.id}")
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1, completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            R.drawable.ic_menu_save,
            "✅ Done",
            completePendingIntent
        )

        // Action 2: Delete (Compact Emoji)
        val deleteIntent = Intent(context, TodoActionReceiver::class.java).apply {
            action = ACTION_DELETE
            putExtra(EXTRA_TASK_ID, task.id)
            data = Uri.parse("todo://action/delete/${task.id}")
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

        // Action 3: Add Task at Top via RemoteInput (Compact Emoji)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("New task at top...")
            .build()

        val addIntent = Intent(context, TodoActionReceiver::class.java).apply {
            action = ACTION_ADD_TOP
            putExtra(EXTRA_TASK_ID, task.id)
            data = Uri.parse("todo://action/addtop/${task.id}")
        }
        val addPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 3, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val addAction = NotificationCompat.Action.Builder(
            R.drawable.ic_input_add,
            "➕ Add",
            addPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        builder.addAction(addAction)

        return builder.build()
    }

    fun buildAllCompletedNotification(context: Context): Notification {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("todo://main/empty")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 3001, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_agenda)
            .setContentTitle("To-Do List")
            .setContentText("All tasks completed! Add a new task below or in the app.")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("New task...")
            .build()

        val addIntent = Intent(context, TodoActionReceiver::class.java).apply {
            action = ACTION_ADD_TOP
            putExtra(EXTRA_TASK_ID, -1)
            data = Uri.parse("todo://action/addtop/empty")
        }
        val addPendingIntent = PendingIntent.getBroadcast(
            context, 3001 * 10 + 3, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val addAction = NotificationCompat.Action.Builder(
            R.drawable.ic_input_add,
            "➕ Add",
            addPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        builder.addAction(addAction)

        return builder.build()
    }

    fun showTodoNotification(context: Context, tasks: List<Task>, isPersistent: Boolean) {
        createNotificationChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()

        val activeTasks = tasks.reversed().filter { !it.isCompleted }
        val baseTime = System.currentTimeMillis()

        if (activeTasks.isEmpty()) {
            val notification = buildAllCompletedNotification(context)
            try {
                notificationManager.notify(3001, notification)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            for (i in activeTasks.indices.reversed()) {
                val task = activeTasks[i]
                val notificationId = TASK_NOTIFICATION_BASE_ID + task.id
                val notification = buildTaskNotification(
                    context,
                    task,
                    whenTimestamp = baseTime - i * 1000L
                )
                try {
                    notificationManager.notify(notificationId, notification)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()
    }
}
