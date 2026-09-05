package com.rishugh.doit.service

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Service
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rishugh.doit.data.Task
import com.rishugh.doit.data.TodoRepository
import com.rishugh.doit.notification.TodoNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TodoForegroundService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val CHANNEL_ID = "todo_foreground_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, TodoForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TodoForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        updateForegroundAndNotifications(TodoRepository.tasks.value)

        serviceScope.launch {
            TodoRepository.tasks.collectLatest { tasks ->
                updateForegroundAndNotifications(tasks)
            }
        }

        return START_STICKY
    }

    private fun updateForegroundAndNotifications(tasks: List<Task>) {
        val activeTasks = tasks.filter { !it.isCompleted }
        val notificationManager = NotificationManagerCompat.from(this)

        // Cancel existing task notifications so Android Notification Shade re-orders them cleanly
        for (task in tasks) {
            try {
                notificationManager.cancel(TodoNotificationHelper.TASK_NOTIFICATION_BASE_ID + task.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (activeTasks.isEmpty()) {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_agenda)
                .setContentTitle("To-Do List")
                .setContentText("All tasks completed! Add a new task below or in the app.")
                .setOngoing(true)
                .setAutoCancel(false)
            try {
                startForeground(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            // Use the first active task as the foreground service notification
            val firstTask = activeTasks[0]
            val firstNotificationId = TodoNotificationHelper.TASK_NOTIFICATION_BASE_ID + firstTask.id
            val firstNotification = TodoNotificationHelper.buildTaskNotification(this, firstTask)

            try {
                startForeground(firstNotificationId, firstNotification)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            // Post remaining active tasks normally
            for (i in 1 until activeTasks.size) {
                val task = activeTasks[i]
                val notificationId = TodoNotificationHelper.TASK_NOTIFICATION_BASE_ID + task.id
                val notification = TodoNotificationHelper.buildTaskNotification(this, task)
                try {
                    notificationManager.notify(notificationId, notification)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Persistent To-Do Service"
            val descriptionText = "Keeps to-do task notifications pinned and non-swipable"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
