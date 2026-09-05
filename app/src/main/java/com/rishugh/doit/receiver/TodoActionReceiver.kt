package com.rishugh.doit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.rishugh.doit.data.TodoRepository
import com.rishugh.doit.notification.TodoNotificationHelper

class TodoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TodoNotificationHelper.EXTRA_TASK_ID, -1)
        when (intent.action) {
            TodoNotificationHelper.ACTION_COMPLETE -> {
                if (taskId != -1) {
                    TodoRepository.toggleTask(taskId)
                    TodoNotificationHelper.showTodoNotification(context, TodoRepository.tasks.value)
                }
            }
            TodoNotificationHelper.ACTION_DELETE -> {
                if (taskId != -1) {
                    TodoRepository.deleteTask(taskId)
                    TodoNotificationHelper.showTodoNotification(context, TodoRepository.tasks.value)
                }
            }
            TodoNotificationHelper.ACTION_ADD_BELOW -> {
                val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInputResults?.getCharSequence(TodoNotificationHelper.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank() && taskId != -1) {
                    TodoRepository.addTaskAfter(taskId, replyText)
                    TodoNotificationHelper.showTodoNotification(context, TodoRepository.tasks.value)
                }
            }
        }
    }
}
