package com.simplexray.an

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplexray.an.LogFragment.LogAdapter.LogViewHolder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LogFragment : Fragment(), MenuProvider {
    private var logFileManager: LogFileManager? = null
    private var logAdapter: LogAdapter? = null
    private lateinit var recyclerViewLog: RecyclerView
    private var layoutManager: LinearLayoutManager? = null
    private var logUpdateReceiver: LogUpdateReceiver? = null
    private var logLoadExecutor: ExecutorService? = null
    private var noLogText: TextView? = null
    private var exportMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLoadExecutor = Executors.newSingleThreadExecutor()
        logUpdateReceiver = LogUpdateReceiver()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_log, container, false)
        logFileManager = LogFileManager(requireContext())
        recyclerViewLog = view.findViewById(R.id.recycler_view_log)
        noLogText = view.findViewById(R.id.no_log_text)
        layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        recyclerViewLog.setLayoutManager(layoutManager)
        logAdapter = LogAdapter(ArrayList(), this)
        recyclerViewLog.setAdapter(logAdapter)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "LogFragment onResume, registering receiver and reloading logs.")
        val filter = IntentFilter(TProxyService.ACTION_LOG_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(
                logUpdateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireActivity().registerReceiver(logUpdateReceiver, filter)
        }
        loadLogsInBackground()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "LogFragment onPause, unregistering receiver.")
        logUpdateReceiver?.let { requireActivity().unregisterReceiver(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "LogFragment view destroyed.")
        logAdapter?.clearLogs()
        logAdapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (logLoadExecutor != null && !logLoadExecutor!!.isShutdown) {
            logLoadExecutor!!.shutdownNow()
            Log.d(TAG, "LogLoadExecutor shut down.")
        }
        Log.d(TAG, "LogFragment destroyed.")
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        Log.d(TAG, "LogFragment onCreateMenu")
        exportMenuItem = menu.findItem(R.id.menu_export)

        if (exportMenuItem != null) {
            exportMenuItem!!.setEnabled((logAdapter?.itemCount ?: 0) > 0)
            Log.d(TAG, "Export menu item enabled: " + exportMenuItem!!.isEnabled)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val id = menuItem.itemId
        if (id == R.id.menu_export) {
            exportLogFile()
            return true
        }
        return false
    }

    private fun exportLogFile() {
        val logFile = logFileManager!!.logFile
        if (!logFile.exists() || logAdapter == null || logAdapter!!.itemCount == 0) {
            Log.w(TAG, "Export log file is null, empty, or no logs in adapter.")
            if (exportMenuItem != null) exportMenuItem!!.setEnabled(false)
            return
        }
        try {
            val fileUri = FileProvider.getUriForFile(
                requireContext(),
                "com.simplexray.an.fileprovider",
                logFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain")
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.export))
            if (shareIntent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(chooserIntent)
                Log.d(TAG, "Export intent resolved and started.")
            } else {
                Log.w(TAG, "No activity found to handle export intent.")
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error getting Uri for file using FileProvider during export.", e)
        }
    }

    fun reloadLogs() {
        Log.d(TAG, "Reloading logs.")
        if (logAdapter != null) {
            logAdapter!!.clearLogs()
        }
        if (logFileManager == null) {
            return
        }
        loadLogsInBackground()
        requireActivity().runOnUiThread {
            updateUIBasedOnLogCount()
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun loadLogsInBackground() {
        if (logAdapter == null) {
            Log.d(TAG, "Skipping background log loading: Adapter is null or logs already loaded.")
            return
        }

        Log.d(TAG, "Starting background initial log loading as list is empty.")
        logLoadExecutor?.takeIf { !it.isShutdown }?.submit {
            val savedLogData = logFileManager!!.readLogs()
            if (activity != null && logAdapter != null) {
                val initialLogs: MutableList<String> = ArrayList()
                if (!savedLogData.isNullOrEmpty()) {
                    initialLogs.addAll(
                        listOf(
                            *savedLogData.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()))
                }

                val uniqueInitialLogs = logAdapter!!.processInitialLogs(initialLogs)

                requireActivity().runOnUiThread {
                    Log.d(TAG, "Background initial log loading finished, updating UI.")
                    if (logAdapter != null) {
                        logAdapter!!.addProcessedLogsToDisplay(uniqueInitialLogs)
                    }
                    updateUIBasedOnLogCount()
                    recyclerViewLog.post {
                        if (logAdapter != null && logAdapter!!.itemCount > 0) {
                            recyclerViewLog.scrollToPosition(logAdapter!!.itemCount - 1)
                            Log.d(TAG, "Scrolled to bottom after initial load.")
                        }
                    }
                    requireActivity().invalidateOptionsMenu()
                }
            } else {
                Log.w(
                    TAG,
                    "Fragment detached or adapter null during background initial log loading UI update."
                )
            }
        }
    }

    private fun updateUIBasedOnLogCount() {
        if ((logAdapter?.itemCount ?: 0) == 0) {
            noLogText?.visibility = View.VISIBLE
            recyclerViewLog.visibility = View.GONE
            exportMenuItem?.isEnabled = false
        } else {
            noLogText?.visibility = View.GONE
            recyclerViewLog.visibility = View.VISIBLE
            val canExport =
                (logAdapter?.itemCount ?: 0) > 0 && logFileManager?.logFile?.exists() == true
            exportMenuItem?.isEnabled = canExport
        }
    }

    private class LogAdapter(
        private val logEntries: MutableList<String>,
        private val logFragment: LogFragment?
    ) : RecyclerView.Adapter<LogViewHolder>() {
        private val logEntrySet: MutableSet<String> = HashSet()
        private val setLock = Any()

        fun processInitialLogs(initialLogs: List<String>?): List<String> {
            val uniqueLogs: MutableList<String> = ArrayList()
            if (initialLogs.isNullOrEmpty()) {
                return uniqueLogs
            }
            synchronized(setLock) {
                for (entry in initialLogs) {
                    if (entry.trim { it <= ' ' }.isNotEmpty()) {
                        if (logEntrySet.add(entry)) {
                            uniqueLogs.add(entry)
                        }
                    }
                }
            }
            Log.d(
                TAG,
                "Processed initial logs: " + uniqueLogs.size + " unique entries added to set."
            )
            return uniqueLogs
        }

        fun filterUniqueLogs(newLogs: List<String?>?): List<String> {
            if (newLogs.isNullOrEmpty()) {
                return emptyList()
            }
            synchronized(setLock) {
                return newLogs
                    .filterNotNull()
                    .filter { it.trim().isNotEmpty() }
                    .filter { logEntrySet.add(it) }
            }
        }

        fun addProcessedLogsToDisplay(processedLogs: List<String>?) {
            if (processedLogs.isNullOrEmpty()) {
                logFragment?.updateUIBasedOnLogCount()
                return
            }
            val startPosition = logEntries.size
            logEntries.addAll(processedLogs)
            notifyItemRangeInserted(startPosition, processedLogs.size)
            logFragment?.updateUIBasedOnLogCount()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.list_item_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val logEntry = logEntries[position]
            val typedValue = TypedValue()
            holder.itemView.context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                typedValue,
                true
            )
            holder.textViewLog.setTextColor(typedValue.data)
            val ssb = SpannableStringBuilder(logEntry)
            val timestampColor =
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
            var endIndex = 0
            while (endIndex < logEntry.length) {
                val c = logEntry[endIndex]
                if (Character.isDigit(c) || c == '/' || c == ' ' || c == ':' || c == '.') {
                    endIndex++
                } else {
                    break
                }
            }
            if (endIndex > 0) {
                val potentialTimestamp = logEntry.substring(0, endIndex)
                if (potentialTimestamp.contains("/") && potentialTimestamp.contains(":")) {
                    ssb.setSpan(
                        ForegroundColorSpan(timestampColor),
                        0,
                        endIndex,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            holder.textViewLog.text = ssb
        }

        override fun getItemCount(): Int {
            return logEntries.size
        }

        fun clearLogs() {
            val oldSize = logEntries.size
            logEntries.clear()
            synchronized(setLock) {
                logEntrySet.clear()
            }
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            logFragment?.updateUIBasedOnLogCount()
            Log.d(TAG, "LogAdapter logs and set cleared.")
        }

        class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var textViewLog: TextView = itemView.findViewById(R.id.text_view_log_entry)

            init {
                textViewLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                textViewLog.typeface = Typeface.MONOSPACE
                textViewLog.setTextIsSelectable(true)
                val typedValue = TypedValue()
                itemView.context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface,
                    typedValue,
                    true
                )
                textViewLog.setTextColor(typedValue.data)
            }
        }
    }

    private inner class LogUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TProxyService.ACTION_LOG_UPDATE == intent.action) {
                val newLogs = intent.getStringArrayListExtra(TProxyService.EXTRA_LOG_DATA)
                if (!newLogs.isNullOrEmpty()) {
                    val wasAtBottom: Boolean
                    val oldItemCount: Int

                    if (layoutManager != null && logAdapter != null && logAdapter!!.itemCount > 0) {
                        val lastCompletelyVisibleItemPosition =
                            layoutManager!!.findLastCompletelyVisibleItemPosition()
                        oldItemCount = logAdapter!!.itemCount
                        wasAtBottom =
                            (oldItemCount == 0) || (lastCompletelyVisibleItemPosition >= oldItemCount - 1)
                        Log.d(
                            TAG,
                            "LogUpdateReceiver: Was at bottom: $wasAtBottom, last visible: $lastCompletelyVisibleItemPosition, item count: $oldItemCount"
                        )
                    } else {
                        wasAtBottom = true
                        Log.d(
                            TAG,
                            "LogUpdateReceiver: List empty or not initialized, treating as at bottom."
                        )
                    }
                    if (logLoadExecutor != null && !logLoadExecutor!!.isShutdown) {
                        logLoadExecutor!!.submit {
                            val uniqueNewLogs =
                                if (logAdapter != null) logAdapter!!.filterUniqueLogs(newLogs) else ArrayList()
                            if (uniqueNewLogs.isNotEmpty() && activity != null) {
                                activity!!.runOnUiThread {
                                    if (logAdapter != null) {
                                        logAdapter!!.addProcessedLogsToDisplay(uniqueNewLogs)
                                    }
                                    updateUIBasedOnLogCount()
                                    if (wasAtBottom) {
                                        recyclerViewLog.post {
                                            if (logAdapter != null && logAdapter!!.itemCount > 0) {
                                                recyclerViewLog.smoothScrollToPosition(logAdapter!!.itemCount - 1)
                                                Log.d(
                                                    TAG,
                                                    "Smooth scrolled to bottom after receiving broadcast because user was at bottom."
                                                )
                                            }
                                        }
                                    } else {
                                        Log.d(
                                            TAG,
                                            "Received log update broadcast, but user was not at bottom. Not auto-scrolling."
                                        )
                                    }
                                    requireActivity().invalidateOptionsMenu()
                                }
                            } else {
                                if (activity != null) {
                                    activity!!.runOnUiThread { this@LogFragment.updateUIBasedOnLogCount() }
                                    activity!!.runOnUiThread { requireActivity().invalidateOptionsMenu() }
                                }
                                Log.d(
                                    TAG,
                                    "Received log update broadcast, but no unique entries or fragment detached."
                                )
                            }
                        }
                    } else {
                        Log.w(
                            TAG,
                            "LogLoadExecutor is null or shut down, cannot process log update."
                        )
                    }
                    Log.d(TAG, "Received log update broadcast with " + newLogs.size + " entries.")
                } else {
                    Log.w(TAG, "Received log update broadcast, but log data list is null or empty.")
                    if (activity != null) {
                        activity!!.runOnUiThread { this@LogFragment.updateUIBasedOnLogCount() }
                        activity!!.runOnUiThread { requireActivity().invalidateOptionsMenu() }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "LogFragment"
    }
}