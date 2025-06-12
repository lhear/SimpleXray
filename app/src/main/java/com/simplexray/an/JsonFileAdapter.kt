package com.simplexray.an

import android.annotation.SuppressLint
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class JsonFileAdapter(
    private var fileList: List<File>,
    private val listener: OnItemActionListener?,
    private val preferences: Preferences
) : RecyclerView.Adapter<JsonFileAdapter.ViewHolder>() {
    private var selectedItemPosition = RecyclerView.NO_POSITION

    init {
        loadSelectedItem()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val file = fileList[position]
        var fileName = file.name
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length - ".json".length)
        }
        holder.fileNameTextView.text = fileName
        if (selectedItemPosition == position) {
            val typedValue = TypedValue()
            val context = holder.itemView.context
            context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            holder.selectionIndicator.setBackgroundColor(typedValue.data)
        } else {
            holder.selectionIndicator.setBackgroundResource(android.R.color.transparent)
        }
        holder.itemView.setOnClickListener {
            listener?.onItemSelected(file)
            preferences.selectedConfigPath = file.absolutePath
            notifyItemChanged(selectedItemPosition)
            selectedItemPosition = holder.adapterPosition
            notifyItemChanged(selectedItemPosition)
            Log.d(TAG, "Item clicked: ${file.name}, position: $selectedItemPosition")
        }
        holder.editButton.setOnClickListener {
            listener?.onEditClick(file)
            Log.d(TAG, "Edit clicked for: ${file.name}")
        }
        holder.deleteButton.setOnClickListener {
            listener?.onDeleteClick(file)
            Log.d(TAG, "Delete clicked for: ${file.name}")
        }
    }

    override fun getItemCount(): Int {
        return fileList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newFileList: List<File>) {
        this.fileList = newFileList
        selectedItemPosition = RecyclerView.NO_POSITION
        loadSelectedItem()
        notifyDataSetChanged()
    }

    val selectedItem: File?
        get() {
            if (selectedItemPosition != RecyclerView.NO_POSITION && selectedItemPosition < fileList.size) {
                return fileList[selectedItemPosition]
            }
            return null
        }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        preferences.selectedConfigPath = null
        selectedItemPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    private fun loadSelectedItem() {
        val savedPath = preferences.selectedConfigPath
        savedPath?.let { path ->
            val index = fileList.indexOfFirst { it.absolutePath == path }
            if (index != -1) {
                selectedItemPosition = index
                Log.d(
                    TAG,
                    "Loaded selected item: ${fileList[selectedItemPosition].name}, position: $selectedItemPosition"
                )
                return
            }
        }
        if (fileList.isNotEmpty()) {
            selectedItemPosition = 0
            preferences.selectedConfigPath = fileList[0].absolutePath
            Log.d(
                TAG,
                "Default selected item: ${fileList[0].name}, position: $selectedItemPosition"
            )
        } else {
            selectedItemPosition = RecyclerView.NO_POSITION
            preferences.selectedConfigPath = null
        }
    }

    interface OnItemActionListener {
        fun onEditClick(file: File?)

        fun onDeleteClick(file: File?)

        fun onItemSelected(file: File?)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var fileNameTextView: TextView = view.findViewById(R.id.file_name_text_view)
        var editButton: ImageButton = view.findViewById(R.id.edit_button)
        var deleteButton: ImageButton = view.findViewById(R.id.delete_button)
        var selectionIndicator: View = view.findViewById(R.id.selection_indicator)
    }

    companion object {
        private const val TAG = "JsonFileAdapter"
    }
}