package com.simplexray.an;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConfigFragment extends Fragment implements JsonFileAdapter.OnItemActionListener {
    private static final String TAG = "ConfigFragment";
    private JsonFileAdapter jsonFileAdapter;
    private List<File> jsonFileList;
    private Preferences prefs;
    private OnConfigActionListener configActionListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnConfigActionListener) {
            configActionListener = (OnConfigActionListener) context;
        } else {
            throw new RuntimeException(context
                    + " must implement OnConfigActionListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_config, container, false);
        prefs = new Preferences(requireContext());
        RecyclerView jsonFileRecyclerView = view.findViewById(R.id.json_file_list_recyclerview);
        jsonFileList = getJsonFilesInPrivateDir();
        jsonFileAdapter = new JsonFileAdapter(jsonFileList, this, prefs);
        jsonFileRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        jsonFileRecyclerView.setAdapter(jsonFileAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "ConfigFragment onResume, reloading file list.");
        new Thread(() -> {
            List<File> updatedList = getJsonFilesInPrivateDir();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "Background file list loading finished, updating UI.");
                    jsonFileList = updatedList;
                    jsonFileAdapter.updateData(jsonFileList);
                });
            } else {
                Log.w(TAG, "Fragment detached during background file list loading.");
            }
        }).start();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        configActionListener = null;
    }

    @Override
    public void onEditClick(File file) {
        if (configActionListener != null) {
            configActionListener.onEditConfigClick(file);
        }
    }

    @Override
    public void onDeleteClick(File file) {

        if (configActionListener != null) {
            configActionListener.onDeleteConfigClick(file);
        }
    }

    public void deleteFileAndUpdateList(File fileToDelete) {

        File selectedFile = jsonFileAdapter.getSelectedItem();
        if (fileToDelete.delete()) {
            jsonFileList.remove(fileToDelete);

            if (selectedFile != null && selectedFile.equals(fileToDelete)) {
                jsonFileAdapter.clearSelection();
            }
            jsonFileAdapter.updateData(jsonFileList);
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), R.string.delete_fail, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onItemSelected(File file) {
        prefs.setSelectedConfigPath(file.getAbsolutePath());
    }

    private List<File> getJsonFilesInPrivateDir() {
        List<File> jsonFiles = new ArrayList<>();
        File privateDir = requireContext().getFilesDir();
        if (privateDir.exists() && privateDir.isDirectory()) {
            File[] files = privateDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                        jsonFiles.add(file);
                    }
                }
            }
        }
        jsonFiles.sort(Comparator.comparingLong(File::lastModified));
        return jsonFiles;
    }

    public void refreshFileList() {
        jsonFileList = getJsonFilesInPrivateDir();
        jsonFileAdapter.updateData(jsonFileList);
    }

    public interface OnConfigActionListener {
        void onEditConfigClick(File file);

        void onDeleteConfigClick(File file);
    }
}