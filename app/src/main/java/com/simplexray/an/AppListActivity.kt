package com.simplexray.an

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppListActivity : AppCompatActivity(), SearchView.OnQueryTextListener {
    private var prefs: Preferences? = null
    private var isChanged = false
    private var adapter: AppRecyclerAdapter? = null
    private var packageList: MutableList<Package>? = null
    private var progressBar: ProgressBar? = null
    private var executorService: ExecutorService? = null
    private var recyclerView: RecyclerView? = null
    private var toolbar: Toolbar? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_list_activity)
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        progressBar = findViewById(R.id.progress_bar)
        recyclerView = findViewById(R.id.app_list_recycler_view)
        executorService = Executors.newSingleThreadExecutor()

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView!!) { v: View, insets: WindowInsetsCompat ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBarInsets.bottom)
            return@setOnApplyWindowInsetsListener insets
        }

        prefs = Preferences(this)
        adapter = AppRecyclerAdapter(this, ArrayList())
        packageList = ArrayList()
        recyclerView?.let { rv ->
            rv.setLayoutManager(LinearLayoutManager(this))
            rv.setAdapter(adapter)
        }
        loadAppList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_list_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView?
        searchView?.setOnQueryTextListener(this)
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW or MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        val searchText = newText.lowercase(Locale.getDefault())
        val filteredList = packageList?.filter {
            it.label.lowercase(Locale.getDefault()).contains(searchText)
        }?.toMutableList() ?: ArrayList()

        adapter?.updateList(filteredList)
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        setStatusBarFontColorByTheme(isDark)
    }

    override fun onResume() {
        super.onResume()
        if (adapter?.itemCount == 0) {
            loadAppList()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        if (executorService?.isShutdown == false) {
            executorService?.shutdownNow()
        }
        if (isChanged) {
            val apps: MutableSet<String> = HashSet()
            adapter?.currentList?.let { list ->
                for (pkg in list) {
                    if (pkg.selected) apps.add(pkg.packageName)
                }
            }
            prefs?.apps = apps
        }
        super.onDestroy()
    }

    private fun loadAppList() {
        if (executorService?.isShutdown == false) {
            progressBar?.visibility = View.VISIBLE
            executorService?.execute(LoadAppsRunnable(this))
        }
    }

    private fun setStatusBarFontColorByTheme(isDark: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }

    private class LoadAppsRunnable(context: AppListActivity) : Runnable {
        private val activityWeakReference = WeakReference(context)

        override fun run() {
            val activity = activityWeakReference.get() ?: return

            if (activity.isFinishing) {
                return
            }

            val apps = activity.prefs?.apps ?: emptySet()
            val pm = activity.packageManager
            val loadedPackages: MutableList<Package> = ArrayList()
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            for (info in installedPackages) {
                if (Thread.currentThread().isInterrupted) {
                    return
                }
                if (info.packageName == activity.packageName) continue

                val hasInternetPermission = info.requestedPermissions?.any {
                    it == Manifest.permission.INTERNET
                } == true

                if (!hasInternetPermission) continue

                val selected = apps.contains(info.packageName)
                val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
                val icon = info.applicationInfo?.loadIcon(pm)
                    ?: activity.packageManager.defaultActivityIcon

                val pkg = Package(selected, label, icon, info.packageName)
                loadedPackages.add(pkg)
            }

            loadedPackages.sortWith(compareByDescending<Package> { it.selected }.thenBy { it.label })

            activity.runOnUiThread {
                if (activity.isFinishing) {
                    return@runOnUiThread
                }
                activity.packageList?.clear()
                activity.packageList?.addAll(loadedPackages)

                activity.adapter?.updateList(loadedPackages)
                activity.progressBar?.visibility = View.GONE
            }
        }
    }

    private data class Package(
        var selected: Boolean,
        var label: String,
        var icon: Drawable,
        var packageName: String
    )

    @SuppressWarnings("NotifyDataSetChanged")
    private class AppRecyclerAdapter(activity: AppListActivity, var currentList: List<Package>) :
        RecyclerView.Adapter<AppRecyclerAdapter.ViewHolder>() {
        private val activityWeakReference = WeakReference(activity)

        fun updateList(newList: List<Package>) {
            this.currentList = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.appitem, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pkg = currentList[position]
            holder.icon.setImageDrawable(pkg.icon)
            holder.name.text = pkg.label
            holder.checked.isChecked = pkg.selected

            holder.itemView.setOnClickListener {
                val isChecked = !holder.checked.isChecked
                holder.checked.isChecked = isChecked
                pkg.selected = isChecked

                val activity = activityWeakReference.get()
                if (activity != null) {
                    activity.isChanged = true
                }
            }
        }

        override fun getItemCount(): Int {
            return currentList.size
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var icon: ImageView = itemView.findViewById(R.id.icon)
            var name: TextView = itemView.findViewById(R.id.name)
            var checked: CheckBox = itemView.findViewById(R.id.checked)
        }
    }
}
