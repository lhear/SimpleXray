package com.simplexray.an

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplexray.an.JsonFileAdapter.OnItemActionListener
import com.simplexray.an.MainActivity.Companion.isServiceRunning
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

class ConfigFragment : Fragment(), OnItemActionListener, MenuProvider {
    private var jsonFileAdapter: JsonFileAdapter? = null
    private var jsonFileList: MutableList<File>? = null
    private var prefs: Preferences? = null
    private var configActionListener: OnConfigActionListener? = null
    private var noConfigText: TextView? = null
    private var fragmentExecutorService: ExecutorService? = null
    private var controlMenuItem: MenuItem? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as? OnConfigActionListener)?.let {
            configActionListener = it
            fragmentExecutorService = it.executorService
        } ?: throw RuntimeException("$context must implement OnConfigActionListener")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_config, container, false)
        prefs = Preferences(requireContext())
        val jsonFileRecyclerView = view.findViewById<RecyclerView>(R.id.json_file_list_recyclerview)
        noConfigText = view.findViewById(R.id.no_config_text)
        jsonFileList = jsonFilesInPrivateDir
        jsonFileAdapter = JsonFileAdapter(jsonFileList!!, this, prefs!!)
        jsonFileRecyclerView.layoutManager = LinearLayoutManager(context)
        jsonFileRecyclerView.adapter = jsonFileAdapter
        updateUIBasedOnFileCount()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "ConfigFragment onResume, calling refreshFileList.")
        refreshFileList()
        updateControlMenuItemIcon()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "ConfigFragment onPause")
    }

    override fun onDetach() {
        super.onDetach()
        configActionListener = null
        fragmentExecutorService = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        Log.d(TAG, "ConfigFragment onCreateMenu")
        controlMenuItem = menu.findItem(R.id.menu_control)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_add_config -> {
                configActionListener?.createNewConfigFileAndEdit()
                true
            }

            R.id.menu_control -> {
                configActionListener?.switchVpnService()
                true
            }

            R.id.menu_import_from_clipboard -> {
                configActionListener?.importConfigFromClipboard()
                true
            }

            else -> false
        }
    }

    fun updateControlMenuItemIcon() {
        controlMenuItem?.let { menuItem ->
            prefs?.let { preferences ->
                val enabled = preferences.enable
                if (enabled) {
                    menuItem.setIcon(R.drawable.pause)
                } else {
                    menuItem.setIcon(R.drawable.play)
                }
                Log.d(TAG, "Updated control menu item icon. Enabled: $enabled")
            } ?: Log.w(TAG, "Prefs is null, cannot update icon.")
        } ?: Log.w(TAG, "Control menu item is null, cannot update icon.")
    }

    override fun onEditClick(file: File?) {
        if (configActionListener != null) {
            configActionListener!!.onEditConfigClick(file)
        }
    }

    override fun onDeleteClick(file: File?) {
        if (configActionListener != null) {
            configActionListener!!.onDeleteConfigClick(file)
        }
    }

    fun deleteFileAndUpdateList(fileToDelete: File) {
        val selectedFile = jsonFileAdapter?.selectedItem
        val selectedConfigPath = prefs?.selectedConfigPath
        val isServiceRunning = isServiceRunning(requireContext(), TProxyService::class.java)

        if (isServiceRunning && fileToDelete.absolutePath == selectedConfigPath) {
            Log.w(
                TAG,
                "Attempted to delete the currently active config file while service is running: " + fileToDelete.name
            )
            Toast.makeText(
                requireContext(),
                R.string.delete_config_failed_message,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (fileToDelete.delete()) {
            jsonFileList?.remove(fileToDelete)

            if (selectedFile != null && selectedFile == fileToDelete) {
                jsonFileAdapter?.clearSelection()
                if (fileToDelete.absolutePath == selectedConfigPath) {
                    prefs?.selectedConfigPath = ""
                    Log.d(TAG, "Deleted selected config file, clearing selection in prefs.")
                }
            }

            jsonFileAdapter?.updateData(jsonFileList ?: mutableListOf())
            updateUIBasedOnFileCount()
            requireActivity().invalidateOptionsMenu()
            Log.d(TAG, "Successfully deleted config file: " + fileToDelete.name)
        } else {
            Toast.makeText(requireContext(), R.string.delete_fail, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to delete config file: " + fileToDelete.name)
        }
    }

    override fun onItemSelected(file: File?) {
        prefs?.selectedConfigPath = file?.absolutePath ?: ""
        requireActivity().invalidateOptionsMenu()

        if (isServiceRunning(requireContext(), TProxyService::class.java)) {
            Log.d(TAG, "Config selected while service is running, requesting reload.")
            configActionListener?.reloadConfig()
        }
    }

    private val jsonFilesInPrivateDir: MutableList<File>
        get() {
            val privateDir = requireContext().filesDir
            return privateDir.listFiles()
                ?.filter { it.isFile && it.name.lowercase(Locale.getDefault()).endsWith(".json") }
                ?.sortedBy { it.lastModified() }
                ?.toMutableList()
                ?: mutableListOf()
        }

    fun refreshFileList() {
        fragmentExecutorService?.submit {
            val updatedList = jsonFilesInPrivateDir
            activity?.runOnUiThread {
                Log.d(TAG, "Background file list loading finished, updating UI.")
                jsonFileList = updatedList
                jsonFileAdapter?.updateData(jsonFileList ?: mutableListOf())
                updateUIBasedOnFileCount()
                activity?.invalidateOptionsMenu()
            } ?: run {
                Log.w(TAG, "Fragment detached during refreshFileList background task UI update.")
            }
        } ?: run {
            Log.e(TAG, "ExecutorService is null in refreshFileList. Cannot refresh.")
        }
    }

    private fun updateUIBasedOnFileCount() {
        if (jsonFileList == null || jsonFileList!!.isEmpty()) {
            noConfigText!!.visibility = View.VISIBLE
            if (view != null) {
                requireView().findViewById<View>(R.id.json_file_list_recyclerview).visibility =
                    View.GONE
            }
        } else {
            noConfigText!!.visibility = View.GONE
            if (view != null) {
                requireView().findViewById<View>(R.id.json_file_list_recyclerview).visibility =
                    View.VISIBLE
            }
        }
    }

    companion object {
        private const val TAG = "ConfigFragment"
    }
}