package com.simplexray.an;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogFragment extends Fragment implements MenuProvider {
    private final static String TAG = "LogFragment";
    LogFileManager logFileManager;
    private LogAdapter logAdapter;
    private RecyclerView recyclerViewLog;
    private LinearLayoutManager layoutManager;
    private LogUpdateReceiver logUpdateReceiver;
    private ExecutorService logLoadExecutor;
    private TextView noLogText;
    private MenuItem exportMenuItem;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logLoadExecutor = Executors.newSingleThreadExecutor();
        logUpdateReceiver = new LogUpdateReceiver();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);
        logFileManager = new LogFileManager(requireContext());
        recyclerViewLog = view.findViewById(R.id.recycler_view_log);
        noLogText = view.findViewById(R.id.no_log_text);
        layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        recyclerViewLog.setLayoutManager(layoutManager);
        logAdapter = new LogAdapter(new ArrayList<>(), this);
        recyclerViewLog.setAdapter(logAdapter);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "LogFragment onResume, registering receiver and reloading logs.");
        IntentFilter filter = new IntentFilter(TProxyService.ACTION_LOG_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireActivity().registerReceiver(logUpdateReceiver, filter);
        }
        loadLogsInBackground();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "LogFragment onPause, unregistering receiver.");
        if (logUpdateReceiver != null) {
            requireActivity().unregisterReceiver(logUpdateReceiver);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "LogFragment view destroyed.");
        if (logAdapter != null) {
            logAdapter.clearLogs();
            logAdapter = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (logLoadExecutor != null && !logLoadExecutor.isShutdown()) {
            logLoadExecutor.shutdownNow();
            Log.d(TAG, "LogLoadExecutor shut down.");
        }
        Log.d(TAG, "LogFragment destroyed.");
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        Log.d(TAG, "LogFragment onCreateMenu");
        MenuItem addConfigItem = menu.findItem(R.id.menu_add_config);
        MenuItem controlMenuItem = menu.findItem(R.id.menu_control);
        MenuItem importConfigItem = menu.findItem(R.id.menu_import_from_clipboard);
        MenuItem backupItem = menu.findItem(R.id.menu_backup);
        MenuItem restoreItem = menu.findItem(R.id.menu_restore);
        exportMenuItem = menu.findItem(R.id.menu_export);

        if (addConfigItem != null) addConfigItem.setVisible(false);
        if (controlMenuItem != null) controlMenuItem.setVisible(false);
        if (importConfigItem != null) importConfigItem.setVisible(false);
        if (backupItem != null) backupItem.setVisible(false);
        if (restoreItem != null) restoreItem.setVisible(false);
        if (exportMenuItem != null) {
            exportMenuItem.setVisible(true);
            exportMenuItem.setEnabled(logAdapter != null && logAdapter.getItemCount() > 0);
            Log.d(TAG, "Export menu item enabled: " + exportMenuItem.isEnabled());
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_export) {
            exportLogFile();
            return true;
        }
        return false;
    }

    public void exportLogFile() {
        File logFile = logFileManager.getLogFile();
        if (logFile == null || !logFile.exists() || logAdapter == null || logAdapter.getItemCount() == 0) {
            Log.w(TAG, "Export log file is null, empty, or no logs in adapter.");
            if (exportMenuItem != null) exportMenuItem.setEnabled(false);
            return;
        }
        try {
            Uri fileUri = FileProvider.getUriForFile(requireContext(), "com.simplexray.an.fileprovider", logFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooserIntent = Intent.createChooser(shareIntent, getString(R.string.export));
            if (shareIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(chooserIntent);
                Log.d(TAG, "Export intent resolved and started.");
            } else {
                Log.w(TAG, "No activity found to handle export intent.");
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error getting Uri for file using FileProvider during export.", e);
        }
    }

    public void reloadLogs() {
        Log.d(TAG, "Reloading logs.");
        if (logAdapter != null) {
            logAdapter.clearLogs();
        }
        if (logFileManager == null) {
            return;
        }
        loadLogsInBackground();
        requireActivity().runOnUiThread(() -> {
            updateUIBasedOnLogCount();
            requireActivity().invalidateOptionsMenu();
        });
    }

    void loadLogsInBackground() {
        if (logAdapter == null) {
            Log.d(TAG, "Skipping background log loading: Adapter is null or logs already loaded.");
            return;
        }

        Log.d(TAG, "Starting background initial log loading as list is empty.");
        if (logLoadExecutor != null && !logLoadExecutor.isShutdown()) {
            logLoadExecutor.submit(() -> {
                String savedLogData = logFileManager.readLogs();
                if (getActivity() != null && logAdapter != null) {
                    List<String> initialLogs = new ArrayList<>();
                    if (savedLogData != null && !savedLogData.isEmpty()) {
                        initialLogs.addAll(Arrays.asList(savedLogData.split("\n")));
                    }

                    final List<String> uniqueInitialLogs = logAdapter.processInitialLogs(initialLogs);

                    getActivity().runOnUiThread(() -> {
                        Log.d(TAG, "Background initial log loading finished, updating UI.");
                        if (logAdapter != null) {
                            logAdapter.addProcessedLogsToDisplay(uniqueInitialLogs);
                        }
                        updateUIBasedOnLogCount();
                        recyclerViewLog.post(() -> {
                            if (logAdapter != null && logAdapter.getItemCount() > 0) {
                                recyclerViewLog.scrollToPosition(logAdapter.getItemCount() - 1);
                                Log.d(TAG, "Scrolled to bottom after initial load.");
                            }
                        });
                        requireActivity().invalidateOptionsMenu();
                    });
                } else {
                    Log.w(TAG, "Fragment detached or adapter null during background initial log loading UI update.");
                }
            });
        } else {
            Log.w(TAG, "LogLoadExecutor is null or shut down, cannot submit initial load task.");
        }
    }

    private void updateUIBasedOnLogCount() {
        if (logAdapter == null || logAdapter.getItemCount() == 0) {
            noLogText.setVisibility(View.VISIBLE);
            if (recyclerViewLog != null) {
                recyclerViewLog.setVisibility(View.GONE);
            }
            if (exportMenuItem != null) exportMenuItem.setEnabled(false);
        } else {
            noLogText.setVisibility(View.GONE);
            if (recyclerViewLog != null) {
                recyclerViewLog.setVisibility(View.VISIBLE);
            }
            if (exportMenuItem != null) {
                File logFile = logFileManager.getLogFile();
                exportMenuItem.setEnabled(logAdapter.getItemCount() > 0 && logFile != null && logFile.exists());
            }
        }
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
        private final Set<String> logEntrySet;
        private final Object setLock = new Object();
        private final List<String> logEntries;
        private final LogFragment logFragment;

        public LogAdapter(List<String> logEntries, LogFragment logFragment) {
            this.logEntries = logEntries;
            this.logFragment = logFragment;
            this.logEntrySet = new HashSet<>();
        }

        public List<String> processInitialLogs(List<String> initialLogs) {
            List<String> uniqueLogs = new ArrayList<>();
            if (initialLogs == null || initialLogs.isEmpty()) {
                return uniqueLogs;
            }
            synchronized (setLock) {
                for (String entry : initialLogs) {
                    if (entry != null && !entry.trim().isEmpty()) {
                        if (logEntrySet.add(entry)) {
                            uniqueLogs.add(entry);
                        }
                    }
                }
            }
            Log.d(TAG, "Processed initial logs: " + uniqueLogs.size() + " unique entries added to set.");
            return uniqueLogs;
        }

        public List<String> filterUniqueLogs(List<String> newLogs) {
            List<String> uniqueLogs = new ArrayList<>();
            if (newLogs == null || newLogs.isEmpty()) {
                return uniqueLogs;
            }
            synchronized (setLock) {
                for (String entry : newLogs) {
                    if (entry != null && !entry.trim().isEmpty()) {
                        if (logEntrySet.add(entry)) {
                            uniqueLogs.add(entry);
                        }
                    }
                }
            }
            Log.d(TAG, "Filtered new logs: " + uniqueLogs.size() + " unique entries.");
            return uniqueLogs;
        }

        public void addProcessedLogsToDisplay(List<String> processedLogs) {
            if (processedLogs == null || processedLogs.isEmpty()) {
                if (logFragment != null) {
                    logFragment.updateUIBasedOnLogCount();
                }
                return;
            }
            int startPosition = logEntries.size();
            logEntries.addAll(processedLogs);
            notifyItemRangeInserted(startPosition, processedLogs.size());
            if (logFragment != null) {
                logFragment.updateUIBasedOnLogCount();
            }
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_log, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            String logEntry = logEntries.get(position);
            TypedValue typedValue = new TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
            holder.textViewLog.setTextColor(typedValue.data);
            SpannableStringBuilder ssb = new SpannableStringBuilder(logEntry);
            int timestampColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_green_dark);
            int endIndex = 0;
            while (endIndex < logEntry.length()) {
                char c = logEntry.charAt(endIndex);
                if (Character.isDigit(c) || c == '/' || c == ' ' || c == ':' || c == '.') {
                    endIndex++;
                } else {
                    break;
                }
            }
            if (endIndex > 0) {
                String potentialTimestamp = logEntry.substring(0, endIndex);
                if (potentialTimestamp.contains("/") && potentialTimestamp.contains(":")) {
                    ssb.setSpan(new ForegroundColorSpan(timestampColor), 0, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            holder.textViewLog.setText(ssb);
        }

        @Override
        public int getItemCount() {
            return logEntries.size();
        }

        public void clearLogs() {
            int oldSize = logEntries.size();
            logEntries.clear();
            synchronized (setLock) {
                logEntrySet.clear();
            }
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize);
            }
            if (logFragment != null) {
                logFragment.updateUIBasedOnLogCount();
            }
            Log.d(TAG, "LogAdapter logs and set cleared.");
        }

        static class LogViewHolder extends RecyclerView.ViewHolder {
            TextView textViewLog;

            LogViewHolder(@NonNull View itemView) {
                super(itemView);
                textViewLog = itemView.findViewById(R.id.text_view_log_entry);
                textViewLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                textViewLog.setTypeface(android.graphics.Typeface.MONOSPACE);
                textViewLog.setTextIsSelectable(true);
                TypedValue typedValue = new TypedValue();
                itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                textViewLog.setTextColor(typedValue.data);
            }
        }
    }

    private class LogUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TProxyService.ACTION_LOG_UPDATE.equals(intent.getAction())) {
                ArrayList<String> newLogs = intent.getStringArrayListExtra(TProxyService.EXTRA_LOG_DATA);
                if (newLogs != null && !newLogs.isEmpty()) {
                    final boolean wasAtBottom;
                    final int oldItemCount;

                    if (layoutManager != null && logAdapter != null && logAdapter.getItemCount() > 0) {
                        int lastCompletelyVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition();
                        oldItemCount = logAdapter.getItemCount();
                        wasAtBottom = (oldItemCount == 0) || (lastCompletelyVisibleItemPosition >= oldItemCount - 1);
                        Log.d(TAG, "LogUpdateReceiver: Was at bottom: " + wasAtBottom + ", last visible: " + lastCompletelyVisibleItemPosition + ", item count: " + oldItemCount);
                    } else {
                        wasAtBottom = true;
                        Log.d(TAG, "LogUpdateReceiver: List empty or not initialized, treating as at bottom.");
                    }
                    if (logLoadExecutor != null && !logLoadExecutor.isShutdown()) {
                        logLoadExecutor.submit(() -> {
                            final List<String> uniqueNewLogs = logAdapter != null ? logAdapter.filterUniqueLogs(newLogs) : new ArrayList<>();
                            if (!uniqueNewLogs.isEmpty() && getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (logAdapter != null) {
                                        logAdapter.addProcessedLogsToDisplay(uniqueNewLogs);
                                    }
                                    updateUIBasedOnLogCount();
                                    if (wasAtBottom) {
                                        recyclerViewLog.post(() -> {
                                            if (logAdapter != null && logAdapter.getItemCount() > 0) {
                                                recyclerViewLog.smoothScrollToPosition(logAdapter.getItemCount() - 1);
                                                Log.d(TAG, "Smooth scrolled to bottom after receiving broadcast because user was at bottom.");
                                            }
                                        });
                                    } else {
                                        Log.d(TAG, "Received log update broadcast, but user was not at bottom. Not auto-scrolling.");
                                    }
                                    requireActivity().invalidateOptionsMenu();
                                });
                            } else {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(LogFragment.this::updateUIBasedOnLogCount);
                                    getActivity().runOnUiThread(() -> requireActivity().invalidateOptionsMenu());
                                }
                                Log.d(TAG, "Received log update broadcast, but no unique entries or fragment detached.");
                            }
                        });
                    } else {
                        Log.w(TAG, "LogLoadExecutor is null or shut down, cannot process log update.");
                    }
                    Log.d(TAG, "Received log update broadcast with " + newLogs.size() + " entries.");
                } else {
                    Log.w(TAG, "Received log update broadcast, but log data list is null or empty.");
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(LogFragment.this::updateUIBasedOnLogCount);
                        getActivity().runOnUiThread(() -> requireActivity().invalidateOptionsMenu());
                    }
                }
            }
        }
    }
}