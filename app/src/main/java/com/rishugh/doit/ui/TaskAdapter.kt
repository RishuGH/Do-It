package com.rishugh.doit.ui

import android.R
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.rishugh.doit.data.Task

class TaskAdapter(
    private val onToggleTask: (Task) -> Unit,
    private val onDeleteTask: (Task) -> Unit,
    private val onEditTask: (Task) -> Unit,
    private val onReorderFinished: (List<Task>) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val items = mutableListOf<Task>()
    var isDragging = false
        private set

    fun submitList(newTasks: List<Task>) {
        if (isDragging) return
        val diffCallback = TaskDiffCallback(items, newTasks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(newTasks)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val context = parent.context
        val cardView = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
            radius = 24f
            cardElevation = 4f
            setCardBackgroundColor(Color.parseColor("#2C2C2C"))
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 24, 32, 24)
            gravity = Gravity.CENTER_VERTICAL
        }

        val dragHandle = TextView(context).apply {
            text = "☰"
            textSize = 20f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 24, 0)
        }

        val checkBox = CheckBox(context)

        val titleView = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                setMargins(16, 0, 16, 0)
            }
        }

        val editBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_menu_edit)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val deleteBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_menu_delete)
            setBackgroundColor(Color.TRANSPARENT)
        }

        layout.addView(dragHandle)
        layout.addView(checkBox)
        layout.addView(titleView)
        layout.addView(editBtn)
        layout.addView(deleteBtn)
        cardView.addView(layout)

        return TaskViewHolder(cardView, checkBox, titleView, editBtn, deleteBtn)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = items[position]
        holder.titleView.text = task.title

        if (task.isCompleted) {
            holder.titleView.paintFlags = holder.titleView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.titleView.alpha = 0.5f
        } else {
            holder.titleView.paintFlags = holder.titleView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.titleView.alpha = 1.0f
        }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = task.isCompleted
        holder.checkBox.setOnClickListener {
            onToggleTask(task)
        }

        holder.titleView.setOnClickListener {
            onEditTask(task)
        }

        holder.editBtn.setOnClickListener {
            onEditTask(task)
        }

        holder.deleteBtn.setOnClickListener {
            onDeleteTask(task)
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition in items.indices && toPosition in items.indices) {
            val item = items.removeAt(fromPosition)
            items.add(toPosition, item)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    fun onDragStarted() {
        isDragging = true
    }

    fun onDragEnded() {
        if (isDragging) {
            isDragging = false
            notifyDataSetChanged()
            onReorderFinished(items.toList())
        }
    }

    class TaskViewHolder(
        val cardView: View,
        val checkBox: CheckBox,
        val titleView: TextView,
        val editBtn: ImageButton,
        val deleteBtn: ImageButton
    ) : RecyclerView.ViewHolder(cardView)

    class TaskDiffCallback(
        private val oldList: List<Task>,
        private val newList: List<Task>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].id == newList[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
