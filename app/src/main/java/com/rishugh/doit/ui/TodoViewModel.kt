package com.rishugh.doit.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.rishugh.doit.data.FirebaseSyncRepository
import com.rishugh.doit.data.SyncStatus
import com.rishugh.doit.data.Task
import com.rishugh.doit.data.TodoRepository
import com.rishugh.doit.notification.TodoNotificationHelper
import com.rishugh.doit.service.TodoForegroundService
import kotlinx.coroutines.flow.StateFlow

class TodoViewModel : ViewModel() {
    val tasks: StateFlow<List<Task>> = TodoRepository.tasks
    val isPersistent: StateFlow<Boolean> = TodoRepository.isPersistent
    val syncStatus: StateFlow<SyncStatus> = FirebaseSyncRepository.syncStatus

    fun initializeCloudSync(context: Context) {
        FirebaseSyncRepository.initialize(context) { remoteTasks ->
            TodoRepository.updateTasksFromRemote(remoteTasks)
            showNotification(context)
        }
    }

    fun signInWithGoogle(
        context: Context,
        customWebClientId: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        FirebaseSyncRepository.signInWithGoogle(context, customWebClientId, onSuccess, onError)
    }

    fun signOut(context: Context) {
        FirebaseSyncRepository.signOut(context)
    }

    fun addTask(title: String, context: Context) {
        TodoRepository.addTask(title)
        showNotification(context)
    }

    fun toggleTask(id: Int, context: Context) {
        TodoRepository.toggleTask(id)
        showNotification(context)
    }

    fun deleteTask(id: Int, context: Context) {
        TodoRepository.deleteTask(id)
        showNotification(context)
    }

    fun editTask(id: Int, newTitle: String, context: Context) {
        TodoRepository.editTask(id, newTitle)
        showNotification(context)
    }

    fun updateTasks(newTasks: List<Task>, context: Context) {
        TodoRepository.updateTasks(newTasks)
        showNotification(context)
    }

    fun reorderTasks(fromIndex: Int, toIndex: Int, context: Context) {
        TodoRepository.reorderTasks(fromIndex, toIndex)
        showNotification(context)
    }

    fun setPersistent(persistent: Boolean, context: Context) {
        TodoRepository.setPersistent(persistent)
        showNotification(context)
    }

    fun showNotification(context: Context) {
        if (isPersistent.value) {
            TodoForegroundService.start(context)
        } else {
            TodoForegroundService.stop(context)
            TodoNotificationHelper.showTodoNotification(context, tasks.value, false)
        }
    }

    fun hideNotification(context: Context) {
        TodoForegroundService.stop(context)
        TodoNotificationHelper.cancelNotification(context)
    }
}
