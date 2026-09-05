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

    fun addTask(title: String) {
        if (title.isBlank()) return
        val newItem = Task(
            id = (_tasks.value.maxOfOrNull { it.id } ?: 0) + 1,
            title = title.trim(),
            isCompleted = false
        )
        _tasks.update { it + newItem }
    }

    fun addTaskAfter(afterId: Int, title: String) {
        if (title.isBlank()) return
        val newItem = Task(
            id = (_tasks.value.maxOfOrNull { it.id } ?: 0) + 1,
            title = title.trim(),
            isCompleted = false
        )
        _tasks.update { list ->
            val index = list.indexOfFirst { it.id == afterId }
            if (index != -1) {
                val mutable = list.toMutableList()
                mutable.add(index + 1, newItem)
                mutable
            } else {
                list + newItem
            }
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
