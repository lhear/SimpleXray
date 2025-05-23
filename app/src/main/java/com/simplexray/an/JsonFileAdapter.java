package com.simplexray.an;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class JsonFileAdapter extends RecyclerView.Adapter<JsonFileAdapter.ViewHolder> {
    private static final String TAG = "JsonFileAdapter";
    private final OnItemActionListener listener;
    private final Preferences preferences;
    private List<File> fileList;
    private int selectedItemPosition = RecyclerView.NO_POSITION;

    public JsonFileAdapter(List<File> fileList, OnItemActionListener listener, Preferences preferences) {
        this.fileList = fileList;
        this.listener = listener;
        this.preferences = preferences;
        loadSelectedItem();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        File file = fileList.get(position);
        String fileName = file.getName();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }
        holder.fileNameTextView.setText(fileName);
        if (selectedItemPosition == position) {
            TypedValue typedValue = new TypedValue();
            Context context = holder.itemView.getContext();
            context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
            holder.selectionIndicator.setBackgroundColor(typedValue.data);
        } else {
            holder.selectionIndicator.setBackgroundResource(android.R.color.transparent);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemSelected(file);
                preferences.setSelectedConfigPath(file.getAbsolutePath());
            }
            notifyItemChanged(selectedItemPosition);
            selectedItemPosition = holder.getAdapterPosition();
            notifyItemChanged(selectedItemPosition);
            Log.d(TAG, "Item clicked: " + file.getName() + ", position: " + selectedItemPosition);
        });
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(file);
            }
            Log.d(TAG, "Edit clicked for: " + file.getName());
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(file);
            }
            Log.d(TAG, "Delete clicked for: " + file.getName());
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<File> newFileList) {
        this.fileList = newFileList;
        selectedItemPosition = RecyclerView.NO_POSITION;
        loadSelectedItem();
        notifyDataSetChanged();
    }

    public File getSelectedItem() {
        if (selectedItemPosition != RecyclerView.NO_POSITION && selectedItemPosition < fileList.size()) {
            return fileList.get(selectedItemPosition);
        }
        return null;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearSelection() {
        preferences.setSelectedConfigPath(null);
        selectedItemPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    private void loadSelectedItem() {
        String savedPath = preferences.getSelectedConfigPath();
        if (savedPath != null) {
            for (int i = 0; i < fileList.size(); i++) {
                if (fileList.get(i).getAbsolutePath().equals(savedPath)) {
                    selectedItemPosition = i;
                    Log.d(TAG, "Loaded selected item: " + fileList.get(i).getName() + ", position: " + selectedItemPosition);
                    return;
                }
            }
        }
        if (!fileList.isEmpty()) {
            selectedItemPosition = 0;
            preferences.setSelectedConfigPath(fileList.get(0).getAbsolutePath());
            Log.d(TAG, "Default selected item: " + fileList.get(0).getName() + ", position: " + selectedItemPosition);
        } else {
            selectedItemPosition = RecyclerView.NO_POSITION;
            preferences.setSelectedConfigPath(null);
        }
    }

    public interface OnItemActionListener {
        void onEditClick(File file);

        void onDeleteClick(File file);

        void onItemSelected(File file);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        ImageButton editButton;
        ImageButton deleteButton;
        View selectionIndicator;
        View itemView;

        public ViewHolder(View view) {
            super(view);
            itemView = view;
            fileNameTextView = view.findViewById(R.id.file_name_text_view);
            editButton = view.findViewById(R.id.edit_button);
            deleteButton = view.findViewById(R.id.delete_button);
            selectionIndicator = view.findViewById(R.id.selection_indicator);
        }
    }
}