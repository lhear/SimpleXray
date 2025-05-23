package com.simplexray.an;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppListActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {
    private Preferences prefs;
    private boolean isChanged = false;
    private AppRecyclerAdapter adapter;
    private List<Package> packageList;
    private ProgressBar progressBar;
    private ExecutorService executorService;
    private RecyclerView recyclerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_list_activity);
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        setStatusBarFontColorByTheme(isDark);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        RelativeLayout rootLayout = findViewById(R.id.app_list_root_layout);
        progressBar = findViewById(R.id.progress_bar);
        recyclerView = findViewById(R.id.app_list_recycler_view);
        executorService = Executors.newSingleThreadExecutor();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int navigationBarsHeight = systemBarsInsets.bottom == imeInsets.bottom ? 0 : systemBarsInsets.bottom;
            v.setPadding(v.getPaddingLeft(), imeInsets.top + systemBarsInsets.top, v.getPaddingRight(), imeInsets.bottom);
            recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(), navigationBarsHeight);
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = new Preferences(this);
        adapter = new AppRecyclerAdapter(this, new ArrayList<>());
        packageList = new ArrayList<>();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        loadAppList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.app_list_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        Objects.requireNonNull(searchView).setOnQueryTextListener(this);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String searchText = newText.toLowerCase();
        List<Package> filteredList = new ArrayList<>();
        for (Package pkg : packageList) {
            if (pkg.label.toLowerCase().contains(searchText)) {
                filteredList.add(pkg);
            }
        }
        adapter.updateList(filteredList);
        return true;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        setStatusBarFontColorByTheme(isDark);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (packageList == null || packageList.isEmpty()) {
            loadAppList();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (isChanged) {
            Set<String> apps = new HashSet<>();

            for (Package pkg : adapter.getCurrentList()) {
                if (pkg.selected)
                    apps.add(pkg.info.packageName);
            }
            prefs.setApps(apps);
        }
        super.onDestroy();
    }

    private void loadAppList() {
        if (executorService != null && !executorService.isShutdown()) {
            progressBar.setVisibility(View.VISIBLE);
            executorService.execute(new LoadAppsRunnable(this));
        }
    }

    private void setStatusBarFontColorByTheme(boolean isDark) {
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
    }

    static class ViewHolder {
        ImageView icon;
        TextView name;
        CheckBox checked;
    }

    private static class LoadAppsRunnable implements Runnable {
        private final WeakReference<AppListActivity> activityWeakReference;

        LoadAppsRunnable(AppListActivity context) {
            activityWeakReference = new WeakReference<>(context);
        }

        @Override
        public void run() {
            AppListActivity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            Set<String> apps = activity.prefs.getApps();
            PackageManager pm = activity.getPackageManager();
            List<Package> loadedPackages = new ArrayList<>();
            List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            for (PackageInfo info : installedPackages) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (info.packageName.equals(activity.getPackageName()))
                    continue;
                if (info.requestedPermissions == null)
                    continue;
                if (!Arrays.asList(info.requestedPermissions).contains(Manifest.permission.INTERNET))
                    continue;
                boolean selected = apps.contains(info.packageName);
                String label = Objects.requireNonNull(info.applicationInfo).loadLabel(pm).toString();
                Drawable icon = info.applicationInfo.loadIcon(pm);
                Package pkg = new Package(info, selected, label, icon);
                loadedPackages.add(pkg);
            }

            loadedPackages.sort((a, b) -> {
                if (a.selected != b.selected)
                    return a.selected ? -1 : 1;
                return a.label.compareTo(b.label);
            });

            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }

                activity.packageList.clear();
                activity.packageList.addAll(loadedPackages);

                activity.adapter.updateList(loadedPackages);

                activity.progressBar.setVisibility(View.GONE);
            });
        }
    }

    private static class Package {
        public PackageInfo info;
        public boolean selected;
        public String label;
        public Drawable icon;

        public Package(PackageInfo info, boolean selected, String label, Drawable icon) {
            this.info = info;
            this.selected = selected;
            this.label = label;
            this.icon = icon;
        }
    }

    private static class AppRecyclerAdapter extends RecyclerView.Adapter<AppRecyclerAdapter.ViewHolder> {
        private List<Package> packages;
        private final WeakReference<AppListActivity> activityWeakReference;

        public AppRecyclerAdapter(AppListActivity activity, List<Package> packages) {
            this.activityWeakReference = new WeakReference<>(activity);
            this.packages = packages;
        }

        public void updateList(List<Package> newList) {
            this.packages = newList;
            notifyDataSetChanged();
        }

        public List<Package> getCurrentList() {
            return packages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.appitem, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Package pkg = packages.get(position);
            holder.icon.setImageDrawable(pkg.icon);
            holder.name.setText(pkg.label);
            holder.checked.setChecked(pkg.selected);

            holder.itemView.setOnClickListener(v -> {
                boolean isChecked = !holder.checked.isChecked();
                holder.checked.setChecked(isChecked);
                pkg.selected = isChecked;

                AppListActivity activity = activityWeakReference.get();
                if (activity != null) {
                    activity.isChanged = true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return packages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            CheckBox checked;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                name = itemView.findViewById(R.id.name);
                checked = itemView.findViewById(R.id.checked);
            }
        }
    }
}
