package com.rishugh.doit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rishugh.doit.data.SyncStatus
import com.rishugh.doit.data.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = viewModel()) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val isPersistent by viewModel.isPersistent.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initializeCloudSync(context)
    }

    var newTaskTitle by remember { mutableStateOf("") }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var showWebClientIdDialog by remember { mutableStateOf(false) }
    var manualWebClientId by remember { mutableStateOf("") }

    if (showWebClientIdDialog) {
        AlertDialog(
            onDismissRequest = { showWebClientIdDialog = false },
            title = { Text("Google Sign-In Setup") },
            text = {
                Column {
                    Text(
                        "Your google-services.json does not contain Google OAuth credentials yet.\n\n" +
                                "Steps to fix:\n" +
                                "1. Open Firebase Console > Authentication > Sign-in method.\n" +
                                "2. Enable 'Google' sign-in provider.\n" +
                                "3. Re-download google-services.json and replace app/google-services.json.\n\n" +
                                "Or paste your Web Client ID directly below:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualWebClientId,
                        onValueChange = { manualWebClientId = it },
                        label = { Text("Web Client ID") },
                        placeholder = { Text("xxxxxx.apps.googleusercontent.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualWebClientId.isNotBlank()) {
                            showWebClientIdDialog = false
                            viewModel.signInWithGoogle(
                                context = context,
                                customWebClientId = manualWebClientId.trim(),
                                onSuccess = { user ->
                                    Toast.makeText(context, "Signed in as $user!", Toast.LENGTH_LONG).show()
                                },
                                onError = { error ->
                                    Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("Try Sign In")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWebClientIdDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    taskToEdit?.let { task ->
        var editedTitle by remember(task) { mutableStateOf(task.title) }
        AlertDialog(
            onDismissRequest = { taskToEdit = null },
            title = { Text("Edit Task") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    singleLine = true,
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedTitle.isNotBlank()) {
                            viewModel.editTask(task.id, editedTitle, context)
                            taskToEdit = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { taskToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val displayTasks = remember(tasks) { tasks.reversed() }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            viewModel.showNotification(context)
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To-Do List in Notification") },
                actions = {
                    when (val status = syncStatus) {
                        is SyncStatus.Connected -> {
                            IconButton(onClick = {
                                Toast.makeText(context, "Cloud Sync Active (User ID: ${status.userId.take(8)}...)", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Cloud Sync Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is SyncStatus.Connecting -> {
                            IconButton(onClick = {
                                Toast.makeText(context, "Connecting to Cloud...", Toast.LENGTH_SHORT).show()
                            }) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        else -> {
                            IconButton(onClick = {
                                Toast.makeText(context, "Cloud Sync: Add google-services.json to app/ folder from Firebase Console", Toast.LENGTH_LONG).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Cloud Sync Disabled",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    IconButton(onClick = {
                        viewModel.showNotification(context)
                        Toast.makeText(context, "Notifications refreshed!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Notifications")
                    }
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.showNotification(context)
                            Toast.makeText(context, "To-Do notification updated!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Show Notification")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("New task...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (newTaskTitle.isNotBlank()) {
                        viewModel.addTask(newTaskTitle, context)
                        newTaskTitle = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 Pin tasks to the notification shade. Use '➕ Add' on any notification to add tasks at the top. Hold and drag any task card to pop it out and reorder.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Keep non-removable (swipe lock)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = isPersistent,
                            onCheckedChange = { checked ->
                                viewModel.setPersistent(checked, context)
                                Toast.makeText(
                                    context,
                                    if (checked) "Notifications are now non-removable (swipe lock enabled)" else "Notifications can now be swiped away",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.showNotification(context)
                                Toast.makeText(context, "Notification refreshed", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Refresh Notification")
                        }
                        OutlinedButton(onClick = {
                            viewModel.hideNotification(context)
                            Toast.makeText(context, "Notification dismissed", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val currentSyncStatus = syncStatus
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (currentSyncStatus is SyncStatus.Connected && !currentSyncStatus.isAnonymous) {
                        val userLabel = currentSyncStatus.email ?: currentSyncStatus.displayName ?: "Google Account"
                        Text(
                            text = "☁️ Synced with Google Account:\n$userLabel",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            viewModel.signOut(context)
                            Toast.makeText(context, "Signed out of Google Account", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Sign Out")
                        }
                    } else {
                        Text(
                            text = "🌐 Multi-Device Cloud Sync:\nSign in with Google so your tasks automatically restore on any phone, tablet, or after reinstalling the app.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.signInWithGoogle(
                                    context = context,
                                    onSuccess = { user ->
                                        Toast.makeText(context, "Signed in as $user! Tasks synced across devices.", Toast.LENGTH_LONG).show()
                                    },
                                    onError = { error ->
                                        if (error.contains("Missing Web Client ID", ignoreCase = true)) {
                                            showWebClientIdDialog = true
                                        } else {
                                            Toast.makeText(context, "Google Sign-In: $error", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                        ) {
                            Text("Sign in with Google")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tasks (${tasks.count { !it.isCompleted }} pending)",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tasks yet. Add one above!")
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        val recyclerView = RecyclerView(ctx).apply {
                            layoutManager = LinearLayoutManager(ctx)
                        }
                        val adapter = TaskAdapter(
                            onToggleTask = { task -> viewModel.toggleTask(task.id, ctx) },
                            onDeleteTask = { task -> viewModel.deleteTask(task.id, ctx) },
                            onEditTask = { task -> taskToEdit = task },
                            onReorderFinished = { reorderedDisplayTasks ->
                                val newTasks = reorderedDisplayTasks.reversed()
                                viewModel.updateTasks(newTasks, ctx)
                            }
                        )
                        recyclerView.adapter = adapter

                        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
                        ) {
                            override fun onMove(
                                rv: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder
                            ): Boolean {
                                val fromPos = viewHolder.bindingAdapterPosition
                                val toPos = target.bindingAdapterPosition
                                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                                    adapter.onItemMove(fromPos, toPos)
                                }
                                return true
                            }

                            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                            override fun onSelectedChanged(
                                viewHolder: RecyclerView.ViewHolder?,
                                actionState: Int
                            ) {
                                super.onSelectedChanged(viewHolder, actionState)
                                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                                    adapter.onDragStarted()
                                    viewHolder?.itemView?.apply {
                                        animate().scaleX(1.05f).scaleY(1.05f).alpha(0.85f).setDuration(150).start()
                                        elevation = 16f
                                    }
                                }
                            }

                            override fun clearView(
                                rv: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder
                            ) {
                                super.clearView(rv, viewHolder)
                                viewHolder.itemView.apply {
                                    animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
                                    elevation = 4f
                                }
                                adapter.onDragEnded()
                            }
                        })

                        itemTouchHelper.attachToRecyclerView(recyclerView)
                        recyclerView
                    },
                    update = { recyclerView ->
                        (recyclerView.adapter as? TaskAdapter)?.submitList(displayTasks)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
