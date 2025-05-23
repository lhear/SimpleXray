package com.simplexray.an;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements ConfigFragment.OnConfigActionListener {
    private static final String TAG = "MainActivity";
    private static boolean controlMenuClickable = true;
    private Preferences prefs;
    private MenuItem controlMenuItem;
    private BroadcastReceiver startReceiver;
    private BroadcastReceiver stopReceiver;
    private BottomNavigationView bottomNavigationView;
    private int currentMenuItemId = R.id.menu_bottom_config;
    private ConfigFragment configFragment;
    private Fragment logFragment;
    private Fragment settingsFragment;

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Preferences(this);
        prefs.setEnable(isServiceRunning(this, TProxyService.class));

        setupUI();
        extractAssetsIfNeeded();
        initializeFragments(savedInstanceState);
        registerReceivers();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentMenuItemId", currentMenuItemId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNavigationView.setSelectedItemId(currentMenuItemId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        controlMenuItem = menu.findItem(R.id.menu_control);
        updateUI();
        Fragment currentFragment = null;
        if (currentMenuItemId == R.id.menu_bottom_config) {
            currentFragment = configFragment;
        } else if (currentMenuItemId == R.id.menu_bottom_log) {
            currentFragment = logFragment;
        } else if (currentMenuItemId == R.id.menu_bottom_settings) {
            currentFragment = settingsFragment;
        }
        boolean showConfigMenu = currentFragment instanceof ConfigFragment;
        MenuItem addConfigItem = menu.findItem(R.id.menu_add_config);
        MenuItem importConfigItem = menu.findItem(R.id.menu_import_from_clipboard);
        if (controlMenuItem != null) {
            controlMenuItem.setVisible(showConfigMenu);
        }
        if (addConfigItem != null) {
            addConfigItem.setVisible(showConfigMenu);
        }
        if (importConfigItem != null) {
            importConfigItem.setVisible(showConfigMenu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add_config) {
            createNewConfigFileAndEdit();
            return true;
        } else if (id == R.id.menu_control) {
            switchVpnService();
            return true;
        } else if (id == R.id.menu_import_from_clipboard) {
            importConfigFromClipboard();
            return true;
        }
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            boolean handledByFragment = currentFragment.onOptionsItemSelected(item);
            if (handledByFragment) {
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            controlMenuClickable = false;
            boolean isEnable = prefs.getEnable();
            startService(new Intent(this, TProxyService.class).setAction(isEnable ? TProxyService.ACTION_DISCONNECT : TProxyService.ACTION_CONNECT));
        }
    }

    private void updateUI() {
        boolean enabled = prefs.getEnable();
        if (controlMenuItem != null) {
            if (enabled) {
                controlMenuItem.setIcon(R.drawable.pause);
            } else {
                controlMenuItem.setIcon(R.drawable.play);
            }
        }
    }

    private void setupUI() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        setStatusBarFontColorByTheme(isDark);
        setContentView(R.layout.main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout mainLinearLayout = findViewById(R.id.main_linear_layout);
        ViewCompat.setOnApplyWindowInsetsListener(mainLinearLayout, (v, insets) -> {
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, 0);
            return insets;
        });

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int newItemId = item.getItemId();
            Log.d(TAG, "select item id: " + newItemId);
            if (newItemId == currentMenuItemId) {
                Log.d(TAG, "Item already selected: " + getResources().getResourceEntryName(newItemId));
                return true;
            }
            Fragment targetFragment = null;
            String title = getString(R.string.app_name);
            if (newItemId == R.id.menu_bottom_config) {
                targetFragment = configFragment;
                title = getString(R.string.configuration);
            } else if (newItemId == R.id.menu_bottom_settings) {
                targetFragment = settingsFragment;
                title = getString(R.string.settings);
            } else if (newItemId == R.id.menu_bottom_log) {
                targetFragment = logFragment;
                title = getString(R.string.log);
            }
            if (targetFragment != null) {
                int slideInAnim, slideOutAnim;
                if (getMenuItemPosition(newItemId) > getMenuItemPosition(currentMenuItemId)) {
                    slideInAnim = R.anim.slide_in_right;
                    slideOutAnim = R.anim.slide_out_left;
                } else {
                    slideInAnim = R.anim.slide_in_left;
                    slideOutAnim = R.anim.slide_out_right;
                }
                loadFragment(targetFragment, slideInAnim, slideOutAnim);
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(title);
                }
                currentMenuItemId = newItemId;
                Log.d(TAG, "Fragment loaded. New currentMenuItemId: " + getResources().getResourceEntryName(currentMenuItemId));
                return true;
            }
            Log.w(TAG, "No fragment found for item id: " + getResources().getResourceEntryName(newItemId));
            return false;
        });
    }

    private void initializeFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            Log.d(TAG, "Initial launch, adding and showing ConfigFragment");
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            configFragment = new ConfigFragment();
            logFragment = new LogFragment();
            settingsFragment = new SettingsFragment();
            transaction.add(R.id.fragment_container, configFragment, "config_fragment");
            transaction.add(R.id.fragment_container, logFragment, "log_fragment");
            transaction.add(R.id.fragment_container, settingsFragment, "settings_fragment");
            transaction.hide(logFragment);
            transaction.hide(settingsFragment);
            transaction.commit();
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(getString(R.string.configuration));
            }
            currentMenuItemId = R.id.menu_bottom_config;
            bottomNavigationView.setSelectedItemId(R.id.menu_bottom_config);
        } else {
            Log.d(TAG, "Restoring state");
            currentMenuItemId = savedInstanceState.getInt("currentMenuItemId", R.id.menu_bottom_config);
            FragmentManager fragmentManager = getSupportFragmentManager();
            configFragment = (ConfigFragment) fragmentManager.findFragmentByTag("config_fragment");
            logFragment = fragmentManager.findFragmentByTag("log_fragment");
            settingsFragment = fragmentManager.findFragmentByTag("settings_fragment");
            String title = getString(R.string.app_name);
            if (currentMenuItemId == R.id.menu_bottom_log) {
                title = getString(R.string.log);
            } else if (currentMenuItemId == R.id.menu_bottom_settings) {
                title = getString(R.string.settings);
            } else if (currentMenuItemId == R.id.menu_bottom_config) {
                title = getString(R.string.configuration);
            }
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
            }
            Log.d(TAG, "State restored. CurrentMenuItemId: " + getResources().getResourceEntryName(currentMenuItemId));
            bottomNavigationView.setSelectedItemId(currentMenuItemId);
        }
    }

    private void registerReceivers() {
        startReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Service started");
                new Handler(getMainLooper()).postDelayed(() -> {
                    prefs.setEnable(true);
                    updateUI();
                    controlMenuClickable = true;
                }, 100);
            }
        };
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Service stopped");
                new Handler(getMainLooper()).postDelayed(() -> {
                    prefs.setEnable(false);
                    updateUI();
                    controlMenuClickable = true;
                }, 300);
            }
        };
        IntentFilter startSuccessFilter = new IntentFilter(TProxyService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(startReceiver, startSuccessFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(startReceiver, startSuccessFilter);
        }
        IntentFilter stopSuccessFilter = new IntentFilter(TProxyService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, stopSuccessFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, stopSuccessFilter);
        }
    }

    private void extractAssetsIfNeeded() {
        Context context = getApplicationContext();
        String[] files = {"geoip.dat", "geosite.dat"};
        for (String file : files) {
            File dir = context.getExternalFilesDir(null);
            File targetFile = new File(dir, file);
            if (!targetFile.exists()) {
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    in = context.getAssets().open(file);
                    dir.mkdirs();
                    out = new FileOutputStream(targetFile);
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
    }

    private void switchVpnService() {
        if (!controlMenuClickable) return;
        Preferences prefs = new Preferences(this);
        String selectedConfigPath = prefs.getSelectedConfigPath();
        if (selectedConfigPath == null || selectedConfigPath.isEmpty() || !(new File(selectedConfigPath).exists())) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.not_select_config)
                    .setPositiveButton(R.string.confirm, null).show();
            Log.w(TAG, "Attempted to start VPN service without a selected config file.");
            return;
        }
        Intent intent = VpnService.prepare(MainActivity.this);
        if (intent != null) startActivityForResult(intent, 0);
        else onActivityResult(0, RESULT_OK, null);
    }

    private void setStatusBarFontColorByTheme(boolean isDark) {
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(startReceiver);
        unregisterReceiver(stopReceiver);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        setStatusBarFontColorByTheme(isDark);
    }

    @Override
    public void onEditConfigClick(File file) {
        Log.d(TAG, "ConfigFragment request: Edit file: " + file.getName());
        Intent intent = new Intent(this, ConfigEditActivity.class);
        intent.putExtra("filePath", file.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onDeleteConfigClick(File file) {
        Log.d(TAG, "ConfigFragment request: Delete file: " + file.getName());
        new MaterialAlertDialogBuilder(this).setTitle(R.string.delete_config).setMessage(file.getName())
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (configFragment != null) {
                        configFragment.deleteFileAndUpdateList(file);
                    } else {
                        Log.e(TAG, "Cannot delete file: configFragment reference is null.");
                    }
                }).setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                }).show();
    }

    private void createNewConfigFileAndEdit() {
        String filename = System.currentTimeMillis() + ".json";
        File newFile = new File(getFilesDir(), filename);
        InputStream assetInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            Preferences prefs = new Preferences(this);
            String fileContent = "";
            if (prefs.getUseTemplate()) {
                assetInputStream = getAssets().open("template");
                int size = assetInputStream.available();
                byte[] buffer = new byte[size];
                assetInputStream.read(buffer);
                fileContent = new String(buffer, StandardCharsets.UTF_8);
            } else {
                fileContent = "{}";
            }
            fileOutputStream = new FileOutputStream(newFile);
            fileOutputStream.write(fileContent.getBytes());
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof ConfigFragment) {
                ConfigFragment configFragment = (ConfigFragment) currentFragment;
                configFragment.refreshFileList();
            }
            Intent intent = new Intent(this, ConfigEditActivity.class);
            intent.putExtra("filePath", newFile.getAbsolutePath());
            startActivity(intent);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        } finally {
            if (assetInputStream != null) {
                try {
                    assetInputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void loadFragment(Fragment targetFragment, int enterAnim, int exitAnim) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(enterAnim, exitAnim, R.anim.slide_in_left, R.anim.slide_out_right);
        if (configFragment != null && configFragment.isAdded()) {
            transaction.hide(configFragment);
        }
        if (logFragment != null && logFragment.isAdded()) {
            transaction.hide(logFragment);
        }
        if (settingsFragment != null && settingsFragment.isAdded()) {
            transaction.hide(settingsFragment);
        }
        if (targetFragment != null) {
            transaction.show(targetFragment);
            Log.d(TAG, "Showing fragment: " + targetFragment.getClass().getSimpleName());
        }
        transaction.commit();
        new Handler(getMainLooper()).post(() -> {
            Log.d(TAG, "Posting invalidateOptionsMenu");
            invalidateOptionsMenu();
        });
    }

    private int getMenuItemPosition(int itemId) {
        if (itemId == R.id.menu_bottom_config) {
            return 0;
        } else if (itemId == R.id.menu_bottom_log) {
            return 1;
        } else if (itemId == R.id.menu_bottom_settings) {
            return 2;
        }
        return -1;
    }

    private void importConfigFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.clipboard_no_text)
                    .setPositiveButton(R.string.confirm, null).show();
            Log.w(TAG, "Clipboard is empty or null.");
            return;
        }
        ClipData.Item item = Objects.requireNonNull(clipboard.getPrimaryClip()).getItemAt(0);
        if (item == null || item.getText() == null) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.clipboard_no_text)
                    .setPositiveButton(R.string.confirm, null).show();
            Log.w(TAG, "Clipboard item is null or does not contain text.");
            return;
        }
        String clipboardContent = item.getText().toString();
        try {
            JSONObject jsonObject = new JSONObject(clipboardContent);
            if (!jsonObject.has("inbounds") || !jsonObject.has("outbounds")) {
                new MaterialAlertDialogBuilder(this).setMessage(R.string.invalid_config)
                        .setPositiveButton(R.string.confirm, null).show();
                Log.w(TAG, "JSON missing 'inbounds' or 'outbounds' keys.");
                return;
            }
            clipboardContent = jsonObject.toString(2);
            clipboardContent = clipboardContent.replaceAll("\\\\/", "/");
        } catch (JSONException e) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.invalid_config)
                    .setPositiveButton(R.string.confirm, null).show();
            Log.e(TAG, "Invalid JSON format in clipboard.", e);
            return;
        }
        String filename = "imported_" + System.currentTimeMillis() + ".json";
        File newFile = new File(getFilesDir(), filename);
        try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
            fileOutputStream.write(clipboardContent.getBytes(StandardCharsets.UTF_8));
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof ConfigFragment) {
                ConfigFragment configFragment = (ConfigFragment) currentFragment;
                configFragment.refreshFileList();
            }
            Intent intent = new Intent(this, ConfigEditActivity.class);
            intent.putExtra("filePath", newFile.getAbsolutePath());
            startActivity(intent);
            Log.d(TAG, "Successfully imported config from clipboard to: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.import_failed)
                    .setPositiveButton(R.string.confirm, null).show();
            Log.e(TAG, "Error saving imported config file.", e);
        }
    }
}