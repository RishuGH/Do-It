package com.rishugh.doit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.rishugh.doit.data.TodoRepository
import com.rishugh.doit.notification.TodoNotificationHelper
import com.rishugh.doit.service.TodoForegroundService

class TodoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TodoNotificationHelper.EXTRA_TASK_ID, -1)
        val isPersistent = TodoRepository.isPersistent.value
        val notificationManager = NotificationManagerCompat.from(context)

        when (intent.action) {
            TodoNotificationHelper.ACTION_COMPLETE -> {
                if (taskId != -1) {
                    notificationManager.cancel(TodoNotificationHelper.TASK_NOTIFICATION_BASE_ID + taskId)
                    TodoRepository.toggleTask(taskId)
                    if (isPersistent) {
                        TodoForegroundService.start(context)
                    } else {
                        TodoNotificationHelper.showTodoNotification(context, TodoRepository.tasks.value, false)
                    }
                }
            }
            TodoNotificationHelper.ACTION_DELETE -> {
                if (taskId != -1) {
                    notificationManager.cancel(TodoNotificationHelper.TASK_NOTIFICATION_BASE_ID + taskId)
                    TodoRepository.deleteTask(taskId)
                    if (isPersistent) {
                        TodoForegroundService.start(context)
                    } else {
                        TodoNotificationHelper.showTodoNotification(context, TodoRepository.tasks.value, false)
                    }
                }
            }
            TodoNotificationHelper.ACTION_ADD_TOP -> {
                val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInputResults?.getCharSequence(TodoNotificationHelper.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank()) {
                    TodoNotificationHelper.cancelNotification(context)
                    TodoRepository.addTaskAtTop(replyText)
                    if (isPersistent) {
                        TodoForegroundService.start(context)
                    } else {
                        TodoNotificationHelper.showTodoNotification(context, TodoRepository.tasks.value, false)
                    }
                }
            }
        }
    }
}
