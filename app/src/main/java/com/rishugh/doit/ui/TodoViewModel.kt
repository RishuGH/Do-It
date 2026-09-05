package com.rishugh.doit.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.rishugh.doit.data.Task
import com.rishugh.doit.data.TodoRepository
import com.rishugh.doit.notification.TodoNotificationHelper
import kotlinx.coroutines.flow.StateFlow

class TodoViewModel : ViewModel() {
    val tasks: StateFlow<List<Task>> = TodoRepository.tasks

    fun addTask(title: String) {
        TodoRepository.addTask(title)
    }

    fun toggleTask(id: Int) {
        TodoRepository.toggleTask(id)
    }

    fun deleteTask(id: Int) {
        TodoRepository.deleteTask(id)
    }

    fun showNotification(context: Context) {
        TodoNotificationHelper.showTodoNotification(context, tasks.value)
    }

    fun hideNotification(context: Context) {
        TodoNotificationHelper.cancelNotification(context)
    }
}
