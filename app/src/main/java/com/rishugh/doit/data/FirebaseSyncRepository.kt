package com.rishugh.doit.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SyncStatus {
    object Idle : SyncStatus
    object Connecting : SyncStatus
    data class Connected(
        val userId: String,
        val isAnonymous: Boolean,
        val displayName: String? = null,
        val email: String? = null
    ) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

object FirebaseSyncRepository {
    private const val TAG = "FirebaseSyncRepository"
    private const val TASKS_COLLECTION = "tasks"

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null
    private var isSyncingFromRemote = false
    private var remoteTasksCallback: ((List<Task>) -> Unit)? = null

    fun initialize(context: Context, onRemoteTasksUpdated: (List<Task>) -> Unit) {
        remoteTasksCallback = onRemoteTasksUpdated
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                Log.w(TAG, "Firebase is not initialized. Make sure google-services.json is added.")
                _syncStatus.update { SyncStatus.Error("Firebase not initialized (missing google-services.json)") }
                return
            }

            _syncStatus.update { SyncStatus.Connecting }
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser != null) {
                onUserAuthenticated(currentUser, onRemoteTasksUpdated)
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        result.user?.let { user ->
                            onUserAuthenticated(user, onRemoteTasksUpdated)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed anonymous auth", e)
                        _syncStatus.update { SyncStatus.Error("Auth failed: ${e.localizedMessage}") }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
            _syncStatus.update { SyncStatus.Error("Firebase setup issue: ${e.localizedMessage}") }
        }
    }

    private fun onUserAuthenticated(
        user: FirebaseUser,
        onRemoteTasksUpdated: (List<Task>) -> Unit
    ) {
        _syncStatus.update {
            SyncStatus.Connected(
                userId = user.uid,
                isAnonymous = user.isAnonymous,
                displayName = user.displayName,
                email = user.email
            )
        }
        listenToRemoteTasks(user.uid, onRemoteTasksUpdated)
    }

    fun signInWithGoogle(
        context: Context,
        customWebClientId: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val clientId = if (!customWebClientId.isNullOrBlank()) {
            customWebClientId
        } else {
            try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else ""
            } catch (_: Exception) {
                ""
            }
        }

        if (clientId.isBlank()) {
            onError("Missing Web Client ID. Please enable Google Sign-In in Firebase Console and re-download google-services.json")
            return
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)

                    val auth = FirebaseAuth.getInstance()
                    val currentUser = auth.currentUser

                    if (currentUser != null && currentUser.isAnonymous) {
                        currentUser.linkWithCredential(authCredential)
                            .addOnSuccessListener { authResult ->
                                authResult.user?.let { user ->
                                    remoteTasksCallback?.let { onUserAuthenticated(user, it) }
                                    onSuccess(user.email ?: user.displayName ?: "Google Account")
                                }
                            }
                            .addOnFailureListener {
                                auth.signInWithCredential(authCredential)
                                    .addOnSuccessListener { authResult ->
                                        authResult.user?.let { user ->
                                            remoteTasksCallback?.let { onUserAuthenticated(user, it) }
                                            onSuccess(user.email ?: user.displayName ?: "Google Account")
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        onError(e.localizedMessage ?: "Google Sign-In failed")
                                    }
                            }
                    } else {
                        auth.signInWithCredential(authCredential)
                            .addOnSuccessListener { authResult ->
                                authResult.user?.let { user ->
                                    remoteTasksCallback?.let { onUserAuthenticated(user, it) }
                                    onSuccess(user.email ?: user.displayName ?: "Google Account")
                                }
                            }
                            .addOnFailureListener { e ->
                                onError(e.localizedMessage ?: "Google Sign-In failed")
                            }
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                // User cancelled sign-in flow intentionally
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In exception", e)
                onError(e.localizedMessage ?: "Sign-In error occurred")
            }
        }
    }

    fun signOut(context: Context) {
        try {
            firestoreListener?.remove()
            FirebaseAuth.getInstance().signOut()
            _syncStatus.update { SyncStatus.Idle }
            remoteTasksCallback?.let { initialize(context, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out", e)
        }
    }

    private fun listenToRemoteTasks(userId: String, onRemoteTasksUpdated: (List<Task>) -> Unit) {
        try {
            val db = FirebaseFirestore.getInstance()
            firestoreListener?.remove()

            firestoreListener = db.collection("users")
                .document(userId)
                .collection(TASKS_COLLECTION)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Firestore listen error: ${error.message}", error)
                        _syncStatus.update {
                            SyncStatus.Error("Firestore Error: ${error.localizedMessage}\nCheck Firestore Security Rules in Firebase Console!")
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val remoteTasks = snapshot.documents.mapNotNull { doc ->
                            try {
                                val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                                val title = doc.getString("title") ?: ""
                                val isCompleted = doc.getBoolean("isCompleted") ?: false
                                val orderIndex = doc.getLong("orderIndex")?.toInt() ?: 0
                                Task(id = id, title = title, isCompleted = isCompleted, orderIndex = orderIndex)
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedBy { it.orderIndex }

                        isSyncingFromRemote = true
                        onRemoteTasksUpdated(remoteTasks)
                        isSyncingFromRemote = false

                        val auth = FirebaseAuth.getInstance()
                        val user = auth.currentUser
                        if (user != null) {
                            _syncStatus.update {
                                SyncStatus.Connected(
                                    userId = user.uid,
                                    isAnonymous = user.isAnonymous,
                                    displayName = user.displayName,
                                    email = user.email
                                )
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Firestore listener", e)
            _syncStatus.update { SyncStatus.Error("Firestore Setup Error: ${e.localizedMessage}") }
        }
    }

    fun uploadTasks(tasks: List<Task>) {
        if (isSyncingFromRemote) return
        val currentStatus = _syncStatus.value
        if (currentStatus !is SyncStatus.Connected) return

        try {
            val db = FirebaseFirestore.getInstance()
            val tasksRef = db.collection("users")
                .document(currentStatus.userId)
                .collection(TASKS_COLLECTION)

            tasks.forEachIndexed { index, task ->
                val taskData = hashMapOf(
                    "id" to task.id,
                    "title" to task.title,
                    "isCompleted" to task.isCompleted,
                    "orderIndex" to index
                )
                tasksRef.document(task.id.toString())
                    .set(taskData, SetOptions.merge())
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Task upload failed", e)
                        _syncStatus.update {
                            SyncStatus.Error("Task upload failed: ${e.localizedMessage}. Check Firestore Security Rules!")
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed uploading tasks to Firestore", e)
        }
    }

    fun deleteTask(taskId: Int) {
        val currentStatus = _syncStatus.value
        if (currentStatus !is SyncStatus.Connected) return

        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("users")
                .document(currentStatus.userId)
                .collection(TASKS_COLLECTION)
                .document(taskId.toString())
                .delete()
                .addOnFailureListener { e ->
                    Log.e(TAG, "Task delete failed", e)
                    _syncStatus.update {
                        SyncStatus.Error("Task delete failed: ${e.localizedMessage}. Check Firestore Security Rules!")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting task from Firestore", e)
        }
    }
}
