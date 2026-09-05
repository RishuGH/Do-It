package com.rishugh.doit.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TodoRepository {
    private val _tasks = MutableStateFlow(
        listOf(
            Task(1, "Buy groceries"),
            Task(2, "Finish Android project"),
            Task(3, "Call mom")
        )
    )
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _isPersistent = MutableStateFlow(true)
    val isPersistent: StateFlow<Boolean> = _isPersistent.asStateFlow()

    fun setPersistent(persistent: Boolean) {
        _isPersistent.update { persistent }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        val newItem = Task(
            id = (_tasks.value.maxOfOrNull { it.id } ?: 0) + 1,
            title = title.trim(),
            isCompleted = false
        )
        _tasks.update { listOf(newItem) + it }
    }

    fun addTaskAtTop(title: String) {
        addTask(title)
    }

    fun reorderTasks(fromIndex: Int, toIndex: Int) {
        _tasks.update { list ->
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update list
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable
        }
    }

    fun toggleTask(id: Int) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it }
        }
    }

    fun deleteTask(id: Int) {
        _tasks.update { list -> list.filter { it.id != id } }
    }
}
