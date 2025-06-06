package com.simplexray.an;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class MainActivity extends AppCompatActivity implements OnConfigActionListener {
    private static final String TAG = "MainActivity";
    private static boolean controlMenuClickable = true;
    private Preferences prefs;
    private BroadcastReceiver startReceiver;
    private BroadcastReceiver stopReceiver;
    private BottomNavigationView bottomNavigationView;
    private int currentMenuItemId = R.id.menu_bottom_config;
    private ConfigFragment configFragment;
    private Fragment logFragment;
    private SettingsFragment settingsFragment;
    private ActivityResultLauncher<String> createFileLauncher;
    private byte[] compressedBackupData;
    private ActivityResultLauncher<String[]> openFileLauncher;
    private ExecutorService executorService;
    private ActivityResultLauncher<Intent> vpnPrepareLauncher;

    private MenuProvider currentMenuProvider;

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
        executorService = Executors.newSingleThreadExecutor();
        initializeFragments(savedInstanceState);
        registerReceivers();
        setupFileLaunchers();
        vpnPrepareLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                controlMenuClickable = true;
                boolean isEnable = prefs.getEnable();
                startService(new Intent(this, TProxyService.class).setAction(isEnable ? TProxyService.ACTION_DISCONNECT : TProxyService.ACTION_CONNECT));
            } else {
                controlMenuClickable = true;
            }
        });
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
        return true;
    }

    private void setupFileLaunchers() {
        createFileLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            if (uri != null) {
                if (compressedBackupData != null) {
                    byte[] dataToWrite = compressedBackupData;
                    compressedBackupData = null;
                    executorService.submit(() -> {
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                os.write(dataToWrite);
                                Log.d(TAG, "Backup successful to: " + uri);
                                runOnUiThread(() -> Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show());
                            } else {
                                Log.e(TAG, "Failed to open output stream for backup URI: " + uri);
                                runOnUiThread(() -> Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show());
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing backup data to URI: " + uri, e);
                            runOnUiThread(() -> Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show());
                        }
                    });
                } else {
                    Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Compressed backup data is null in launcher callback.");
                }
            } else {
                Log.w(TAG, "Backup file creation cancelled or failed (URI is null).");
                compressedBackupData = null;
            }
        });
        openFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                startRestoreTask(uri);
            } else {
                Log.w(TAG, "Restore file selection cancelled or failed (URI is null).");
            }
        });
    }

    public void performBackup() {
        try {
            Gson gson = new Gson();
            Map<String, Object> preferencesMap = new HashMap<>();
            preferencesMap.put(Preferences.SOCKS_ADDR, prefs.getSocksAddress());
            preferencesMap.put(Preferences.SOCKS_PORT, prefs.getSocksPort());
            preferencesMap.put(Preferences.DNS_IPV4, prefs.getDnsIpv4());
            preferencesMap.put(Preferences.DNS_IPV6, prefs.getDnsIpv6());
            preferencesMap.put(Preferences.IPV6, prefs.getIpv6());
            preferencesMap.put(Preferences.APPS, new ArrayList<>(prefs.getApps()));
            preferencesMap.put(Preferences.BYPASS_LAN, prefs.getBypassLan());
            preferencesMap.put(Preferences.USE_TEMPLATE, prefs.getUseTemplate());
            preferencesMap.put(Preferences.HTTP_PORT, prefs.getHttpPort());
            preferencesMap.put(Preferences.HTTP_PROXY_ENABLED, prefs.getHttpProxyEnabled());
            Map<String, String> configFilesMap = new HashMap<>();
            File filesDir = getFilesDir();
            File[] files = filesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".json")) {
                        try {
                            String content = readFileContent(file);
                            configFilesMap.put(file.getName(), content);
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading config file: " + file.getName(), e);
                        }
                    }
                }
            }
            Map<String, Object> backupData = new HashMap<>();
            backupData.put("preferences", preferencesMap);
            backupData.put("configFiles", configFilesMap);
            String jsonString = gson.toJson(backupData);
            byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
            Deflater deflater = new Deflater();
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            compressedBackupData = outputStream.toByteArray();
            deflater.end();
            String filename = "simplexray_backup_" + System.currentTimeMillis() + ".dat";
            createFileLauncher.launch(filename);
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error during backup process", e);
        }
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }
        return content.toString();
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
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (savedInstanceState == null) {
            Log.d(TAG, "Initial launch, adding and showing ConfigFragment");
            configFragment = new ConfigFragment();
            logFragment = new LogFragment();
            settingsFragment = new SettingsFragment();
            transaction.add(R.id.fragment_container, configFragment, "config_fragment");
            transaction.add(R.id.fragment_container, logFragment, "log_fragment");
            transaction.add(R.id.fragment_container, settingsFragment, "settings_fragment");
            transaction.hide(logFragment);
            transaction.hide(settingsFragment);
            currentMenuItemId = R.id.menu_bottom_config;
        } else {
            Log.d(TAG, "Restoring state");
            configFragment = (ConfigFragment) fragmentManager.findFragmentByTag("config_fragment");
            logFragment = fragmentManager.findFragmentByTag("log_fragment");
            settingsFragment = (SettingsFragment) fragmentManager.findFragmentByTag("settings_fragment");
            currentMenuItemId = savedInstanceState.getInt("currentMenuItemId", R.id.menu_bottom_config);
            if (configFragment != null && configFragment.isAdded()) {
                transaction.hide(configFragment);
            }
            if (logFragment != null && logFragment.isAdded()) {
                transaction.hide(logFragment);
            }
            if (settingsFragment != null && settingsFragment.isAdded()) {
                transaction.hide(settingsFragment);
            }
            Log.d(TAG, "State restored. Intended currentMenuItemId: " + getResources().getResourceEntryName(currentMenuItemId));
        }
        Fragment activeFragmentToShow = null;
        String title = getString(R.string.app_name);
        int initialSelectedItemId = R.id.menu_bottom_config;
        if (currentMenuItemId == R.id.menu_bottom_log && logFragment != null) {
            title = getString(R.string.log);
            activeFragmentToShow = logFragment;
            initialSelectedItemId = R.id.menu_bottom_log;
        } else if (currentMenuItemId == R.id.menu_bottom_settings && settingsFragment != null) {
            title = getString(R.string.settings);
            activeFragmentToShow = settingsFragment;
            initialSelectedItemId = R.id.menu_bottom_settings;
        } else if (configFragment != null) {
            title = getString(R.string.configuration);
            activeFragmentToShow = configFragment;
            initialSelectedItemId = R.id.menu_bottom_config;
            if (currentMenuItemId != R.id.menu_bottom_config) {
                Log.w(TAG, "Intended active fragment was null or invalid ID (" + getResources().getResourceEntryName(currentMenuItemId) + "), defaulting to config fragment.");
            }
        } else {
            Log.e(TAG, "Config fragment is null during initialization. Cannot show any fragment.");
        }
        if (activeFragmentToShow != null) {
            transaction.show(activeFragmentToShow);
            Log.d(TAG, "Showing fragment: " + activeFragmentToShow.getClass().getSimpleName());
        }
        transaction.commitNow();
        currentMenuItemId = initialSelectedItemId;
        if (currentMenuProvider != null) {
            ((MenuHost) this).removeMenuProvider(currentMenuProvider);
            currentMenuProvider = null;
            Log.d(TAG, "Removed previous MenuProvider.");
        }
        if (activeFragmentToShow instanceof MenuProvider) {
            currentMenuProvider = (MenuProvider) activeFragmentToShow;
            Log.d(TAG, "Attaching MenuProvider for fragment: " + activeFragmentToShow.getClass().getSimpleName() + " to Activity lifecycle.");
            ((MenuHost) this).addMenuProvider(currentMenuProvider, this, Lifecycle.State.RESUMED);
        } else {
            Log.d(TAG, "Active fragment (" + (activeFragmentToShow != null ? activeFragmentToShow.getClass().getSimpleName() : "null") + ") does not provide a menu.");
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
        new Handler(getMainLooper()).post(() -> {
            bottomNavigationView.setSelectedItemId(currentMenuItemId);
            Log.d(TAG, "Set bottom navigation selected item to: " + getResources().getResourceEntryName(currentMenuItemId));
        });
        new Handler(getMainLooper()).post(this::invalidateOptionsMenu);
    }

    private void registerReceivers() {
        startReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Service started");
                if (logFragment instanceof LogFragment) {
                    ((LogFragment) logFragment).clearAndReloadLogs();
                }
                new Handler(getMainLooper()).postDelayed(() -> {
                    prefs.setEnable(true);
                    if (configFragment != null && configFragment.isVisible()) {
                        configFragment.updateControlMenuItemIcon();
                    }
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
                    if (configFragment != null && configFragment.isVisible()) {
                        configFragment.updateControlMenuItemIcon();
                    }
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
        File dir = context.getFilesDir();
        dir.mkdirs();
        for (String file : files) {
            File targetFile = new File(dir, file);
            boolean needsExtraction = false;
            if (targetFile.exists()) {
                try {
                    String existingFileHash = calculateSha256(Files.newInputStream(targetFile.toPath()));
                    String assetHash = calculateSha256(context.getAssets().open(file));
                    if (!existingFileHash.equals(assetHash)) {
                        needsExtraction = true;
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    needsExtraction = true;
                    Log.d(TAG, e.toString());
                }
            } else {
                needsExtraction = true;
            }
            if (needsExtraction) {
                try (InputStream in = context.getAssets().open(file); FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to extract asset: " + file, e);
                }
            }
        }
    }

    private String calculateSha256(InputStream is) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1024];
        int read;
        try {
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            is.close();
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte hashByte : hashBytes) {
            sb.append(String.format("%02x", hashByte));
        }
        return sb.toString();
    }

    @Override
    public void switchVpnService() {
        if (!controlMenuClickable) return;
        Preferences prefs = new Preferences(this);
        String selectedConfigPath = prefs.getSelectedConfigPath();
        if (!prefs.getEnable() && (selectedConfigPath == null || selectedConfigPath.isEmpty() || !(new File(selectedConfigPath).exists()))) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.not_select_config).setPositiveButton(R.string.confirm, null).show();
            Log.w(TAG, "Attempted to start VPN service without a selected config file.");
            return;
        }
        Intent intent = VpnService.prepare(MainActivity.this);
        controlMenuClickable = false;
        if (intent != null) {
            vpnPrepareLauncher.launch(intent);
        } else {
            boolean isEnable = prefs.getEnable();
            startService(new Intent(this, TProxyService.class).setAction(isEnable ? TProxyService.ACTION_DISCONNECT : TProxyService.ACTION_CONNECT));
        }
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
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
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
        new MaterialAlertDialogBuilder(this).setTitle(R.string.delete_config).setMessage(file.getName()).setPositiveButton(R.string.confirm, (dialog, which) -> {
            if (configFragment != null) {
                configFragment.deleteFileAndUpdateList(file);
            } else {
                Log.e(TAG, "Cannot delete file: configFragment reference is null.");
            }
        }).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
    }

    @Override
    public void createNewConfigFileAndEdit() {
        String filename = System.currentTimeMillis() + ".json";
        File newFile = new File(getFilesDir(), filename);
        InputStream assetInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            Preferences prefs = new Preferences(this);
            String fileContent;
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
            if (configFragment != null) {
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

    @Override
    public void importConfigFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.clipboard_no_text).setPositiveButton(R.string.confirm, null).show();
            Log.w(TAG, "Clipboard is empty or null.");
            return;
        }
        ClipData.Item item = Objects.requireNonNull(clipboard.getPrimaryClip()).getItemAt(0);
        if (item == null || item.getText() == null) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.clipboard_no_text).setPositiveButton(R.string.confirm, null).show();
            Log.w(TAG, "Clipboard item is null or does not contain text.");
            return;
        }
        String clipboardContent = item.getText().toString();
        try {
            JSONObject jsonObject = new JSONObject(clipboardContent);
            if (!jsonObject.has("inbounds") || !jsonObject.has("outbounds")) {
                new MaterialAlertDialogBuilder(this).setMessage(R.string.invalid_config).setPositiveButton(R.string.confirm, null).show();
                Log.w(TAG, "JSON missing 'inbounds' or 'outbounds' keys.");
                return;
            }
            if (jsonObject.has("log")) {
                Object logObject = jsonObject.get("log");
                if (logObject instanceof JSONObject) {
                    JSONObject logJson = (JSONObject) logObject;
                    if (logJson.has("access")) {
                        logJson.remove("access");
                        Log.d(TAG, "Removed log.access from imported config.");
                    }
                    if (logJson.has("error")) {
                        logJson.remove("error");
                        Log.d(TAG, "Removed log.error from imported config.");
                    }
                }
            }
            clipboardContent = jsonObject.toString(2);
            clipboardContent = clipboardContent.replaceAll("\\\\/", "/");
        } catch (JSONException e) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.invalid_config).setPositiveButton(R.string.confirm, null).show();
            Log.e(TAG, "Invalid JSON format in clipboard.", e);
            return;
        }
        String filename = "imported_" + System.currentTimeMillis() + ".json";
        File newFile = new File(getFilesDir(), filename);
        try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
            fileOutputStream.write(clipboardContent.getBytes(StandardCharsets.UTF_8));
            if (configFragment != null) {
                configFragment.refreshFileList();
            }
            Intent intent = new Intent(this, ConfigEditActivity.class);
            intent.putExtra("filePath", newFile.getAbsolutePath());
            startActivity(intent);
            Log.d(TAG, "Successfully imported config from clipboard to: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.import_failed).setPositiveButton(R.string.confirm, null).show();
            Log.e(TAG, "Error saving imported config file.", e);
        }
    }

    @Override
    public void performRestore() {
        openFileLauncher.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private void startRestoreTask(Uri uri) {
        executorService.submit(() -> {
            try {
                byte[] compressedData;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) {
                        throw new IOException("Failed to open input stream for URI: " + uri);
                    }
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    compressedData = buffer.toByteArray();
                }

                Inflater inflater = new Inflater();
                inflater.setInput(compressedData);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedData.length);
                byte[] buffer = new byte[1024];
                while (!inflater.finished()) {
                    try {
                        int count = inflater.inflate(buffer);
                        if (count == 0 && inflater.needsInput()) {
                            Log.e(TAG, "Incomplete compressed data during inflation.");
                            throw new IOException("Incomplete compressed data.");
                        }
                        if (count > 0) {
                            outputStream.write(buffer, 0, count);
                        }
                    } catch (DataFormatException e) {
                        Log.e(TAG, "Data format error during inflation", e);
                        throw new IOException("Error decompressing data: Invalid format.", e);
                    } catch (Exception e) {
                        Log.e(TAG, "Error during inflation", e);
                        throw new IOException("Error decompressing data.", e);
                    }
                }
                outputStream.close();
                byte[] decompressedData = outputStream.toByteArray();
                inflater.end();

                String jsonString = new String(decompressedData, StandardCharsets.UTF_8);
                Gson gson = new Gson();
                Type backupDataType = new TypeToken<Map<String, Object>>() {
                }.getType();
                Map<String, Object> backupData = gson.fromJson(jsonString, backupDataType);

                if (backupData == null || !backupData.containsKey("preferences") || !backupData.containsKey("configFiles")) {
                    throw new IllegalArgumentException("Invalid backup file format.");
                }

                Map<String, Object> preferencesMap = null;
                Object preferencesObj = backupData.get("preferences");
                if (preferencesObj instanceof Map) {
                    preferencesMap = (Map<String, Object>) preferencesObj;
                }

                Map<String, String> configFilesMap = null;
                Object configFilesObj = backupData.get("configFiles");
                if (configFilesObj instanceof Map) {
                    configFilesMap = (Map<String, String>) configFilesObj;
                }

                if (preferencesMap != null) {
                    Object value;
                    value = preferencesMap.get(Preferences.SOCKS_PORT);
                    if (value instanceof Number) {
                        prefs.setSocksPort(((Number) value).intValue());
                    } else if (value instanceof String) {
                        try {
                            prefs.setSocksPort(Integer.parseInt((String) value));
                        } catch (NumberFormatException ignore) {
                            Log.w(TAG, "Failed to parse SOCKS_PORT as integer: " + value);
                        }
                    }

                    value = preferencesMap.get(Preferences.HTTP_PORT);
                    if (value instanceof Number) {
                        prefs.setHttpPort(((Number) value).intValue());
                    } else if (value instanceof String) {
                        try {
                            prefs.setHttpPort(Integer.parseInt((String) value));
                        } catch (NumberFormatException ignore) {
                            Log.w(TAG, "Failed to parse HTTP_PORT as integer: " + value);
                        }
                    }

                    value = preferencesMap.get(Preferences.DNS_IPV4);
                    if (value instanceof String) {
                        prefs.setDnsIpv4((String) value);
                    }

                    value = preferencesMap.get(Preferences.DNS_IPV6);
                    if (value instanceof String) {
                        prefs.setDnsIpv6((String) value);
                    }

                    value = preferencesMap.get(Preferences.IPV6);
                    if (value instanceof Boolean) {
                        prefs.setIpv6((Boolean) value);
                    }

                    value = preferencesMap.get(Preferences.BYPASS_LAN);
                    if (value instanceof Boolean) {
                        prefs.setBypassLan((Boolean) value);
                    }

                    value = preferencesMap.get(Preferences.USE_TEMPLATE);
                    if (value instanceof Boolean) {
                        prefs.setUseTemplate((Boolean) value);
                    }

                    value = preferencesMap.get(Preferences.HTTP_PROXY_ENABLED);
                    if (value instanceof Boolean) {
                        prefs.setHttpProxyEnabled((Boolean) value);
                    }

                    value = preferencesMap.get(Preferences.APPS);
                    if (value instanceof List) {
                        List<?> appsList = (List<?>) value;
                        Set<String> appsSet = new HashSet<>();
                        for (Object item : appsList) {
                            if (item instanceof String) {
                                appsSet.add((String) item);
                            } else if (item != null) {
                                Log.w(TAG, "Skipping non-String item in APPS list: " + item.getClass().getName());
                            }
                        }
                        prefs.setApps(appsSet);
                    } else if (value != null) {
                        Log.w(TAG, "APPS preference is not a List: " + value.getClass().getName());
                    }
                } else {
                    Log.w(TAG, "Preferences map is null or not a Map.");
                }

                if (configFilesMap != null) {
                    File filesDir = getFilesDir();
                    for (Map.Entry<String, String> entry : configFilesMap.entrySet()) {
                        String filename = entry.getKey();
                        String content = entry.getValue();
                        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                            Log.e(TAG, "Skipping restore of invalid filename: " + filename);
                            continue;
                        }
                        File configFile = new File(filesDir, filename);
                        try (FileOutputStream fos = new FileOutputStream(configFile)) {
                            fos.write(content.getBytes(StandardCharsets.UTF_8));
                            Log.d(TAG, "Successfully restored config file: " + filename);
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing config file: " + filename, e);
                        }
                    }
                } else {
                    Log.w(TAG, "Config files map is null or not a Map.");
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Restore successful.");
                    if (configFragment != null && configFragment.isAdded()) {
                        configFragment.refreshFileList();
                    }
                    if (settingsFragment != null && settingsFragment.isAdded()) {
                        settingsFragment.refreshPreferences();
                    }
                    invalidateOptionsMenu();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error during restore process", e);
                runOnUiThread(() -> Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadFragment(Fragment targetFragment, int enterAnim, int exitAnim) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(enterAnim, exitAnim, R.anim.slide_in_left, R.anim.slide_out_right);

        MenuHost menuHost = this;

        if (currentMenuProvider != null) {
            menuHost.removeMenuProvider(currentMenuProvider);
            currentMenuProvider = null;
            Log.d(TAG, "Removed previous MenuProvider before loading new fragment.");
        }

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
            if (!targetFragment.isAdded()) {
                transaction.add(R.id.fragment_container, targetFragment, targetFragment.getClass().getSimpleName());
                Log.d(TAG, "Adding target fragment as it was not added: " + targetFragment.getClass().getSimpleName());
            }
            transaction.show(targetFragment);
            Log.d(TAG, "Showing fragment: " + targetFragment.getClass().getSimpleName());

            if (targetFragment instanceof MenuProvider) {
                currentMenuProvider = (MenuProvider) targetFragment;
                menuHost.addMenuProvider(currentMenuProvider, this, Lifecycle.State.RESUMED);
                Log.d(TAG, "Attaching MenuProvider for fragment: " + targetFragment.getClass().getSimpleName() + " to Activity lifecycle.");
            } else {
                Log.d(TAG, "Target fragment (" + targetFragment.getClass().getSimpleName() + ") does not provide a menu.");
            }

            if (targetFragment instanceof LogFragment) {
                ((LogFragment) targetFragment).loadLogsInBackground();
            }
        }

        transaction.commit();

        new Handler(getMainLooper()).post(() -> {
            Log.d(TAG, "Posting invalidateOptionsMenu after fragment transaction");
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

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }
}