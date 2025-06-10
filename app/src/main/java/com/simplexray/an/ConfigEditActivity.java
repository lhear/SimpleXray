package com.simplexray.an;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public class ConfigEditActivity extends AppCompatActivity {
    private static final String TAG = "ConfigEditActivity";
    private EditText editTextConfig;
    private EditText editTextFilename;
    private File configFile;
    private String originalFilePath;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        setStatusBarFontColorByTheme(isDark);
        setContentView(R.layout.activity_config);

        editTextConfig = findViewById(R.id.edit_text_config);
        editTextFilename = findViewById(R.id.edit_text_filename);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        LinearLayout mainConfigLayout = findViewById(R.id.main_config_layout);
        ViewCompat.setOnApplyWindowInsetsListener(mainConfigLayout, (v, insets) -> {
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            ScrollView scrollView = findViewById(R.id.scrollViewConfig);
            if (scrollView != null) {
                v.setPadding(scrollView.getPaddingLeft(), systemBarsInsets.top, scrollView.getPaddingRight(), scrollView.getPaddingBottom());
            }
            v.setPadding(v.getPaddingLeft(), systemBarsInsets.top, v.getPaddingRight(), imeInsets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        originalFilePath = getIntent().getStringExtra("filePath");
        if (originalFilePath != null) {
            configFile = new File(originalFilePath);
            readConfigFile();
        } else {
            Log.e(TAG, "No file path provided in Intent extras.");
            editTextConfig.setEnabled(false);
            editTextFilename.setEnabled(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.config_edit_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_save) {
            saveConfigFile();
            return true;
        } else if (id == R.id.action_share) {
            shareConfigFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void readConfigFile() {
        if (configFile == null || !configFile.exists()) {
            Log.e(TAG, "config not found.");
            editTextConfig.setEnabled(false);
            editTextFilename.setEnabled(false);
            return;
        }
        editTextFilename.setText(getFileNameWithoutExtension());

        StringBuilder stringBuilder = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(configFile);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader br = new BufferedReader(isr)) {
            String line = br.readLine();
            if (line != null) {
                stringBuilder.append(line);
                while ((line = br.readLine()) != null) {
                    stringBuilder.append('\n').append(line);
                }
            }
            editTextConfig.setText(stringBuilder.toString());
        } catch (IOException e) {
            Log.e(TAG, "Error reading config file", e);
            editTextConfig.setEnabled(false);
            editTextFilename.setEnabled(false);
        }
    }

    private void saveConfigFile() {
        if (configFile == null || originalFilePath == null) {
            Log.e(TAG, "Config file path not set.");
            return;
        }

        String configContent = editTextConfig.getText().toString();
        String newFilename = editTextFilename.getText().toString().trim();

        if (newFilename.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.filename_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        if (!isValidFilename(newFilename)) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.filename_invalid)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        if (!newFilename.endsWith(".json")) {
            newFilename += ".json";
        }

        File originalFile = new File(originalFilePath);
        File parentDir = originalFile.getParentFile();
        if (parentDir == null) {
            Log.e(TAG, "Could not determine parent directory.");
            return;
        }
        File newFile = new File(parentDir, newFilename);

        if (newFile.exists() && !newFile.equals(configFile)) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.filename_already_exists)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        String formattedContent;
        try {
            JSONObject jsonObject = new JSONObject(configContent);
            if (jsonObject.has("log")) {
                Object logObject = jsonObject.get("log");
                if (logObject instanceof JSONObject) {
                    JSONObject logJson = (JSONObject) logObject;
                    if (logJson.has("access")) {
                        logJson.remove("access");
                        Log.d(TAG, "Removed log.access");
                    }
                    if (logJson.has("error")) {
                        logJson.remove("error");
                        Log.d(TAG, "Removed log.error");
                    }
                }
            }
            formattedContent = jsonObject.toString(2);
            formattedContent = formattedContent.replaceAll("\\\\/", "/");
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON format", e);
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.invalid_json_format)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        editTextConfig.setText(formattedContent);

        if (!newFile.equals(configFile)) {
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(formattedContent.getBytes());
                if (configFile.exists()) {
                    boolean deleted = configFile.delete();
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete old config file: " + configFile.getAbsolutePath());
                    }
                }
                configFile = newFile;
                originalFilePath = newFile.getAbsolutePath();
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.config_save_success)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } catch (IOException e) {
                Log.e(TAG, "Error writing new config file", e);
            }
        } else {
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(formattedContent.getBytes());
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.config_save_success)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } catch (IOException e) {
                Log.e(TAG, "Error writing config file", e);
            }
        }
        editTextFilename.setText(getFileNameWithoutExtension());
    }

    private void shareConfigFile() {
        if (configFile == null || !configFile.exists()) {
            Log.e(TAG, "Config file not found.");
            return;
        }

        String configContent = editTextConfig.getText().toString();

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, configContent);

        startActivity(Intent.createChooser(shareIntent, null));
    }

    private @NonNull String getFileNameWithoutExtension() {
        String fileName = configFile.getName();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }
        return fileName;
    }

    private boolean isValidFilename(String filename) {
        String invalidChars = "[\\\\/:*?\"<>|]";
        return !Pattern.compile(invalidChars).matcher(filename).find();
    }

    private void setStatusBarFontColorByTheme(boolean isDark) {
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        setStatusBarFontColorByTheme(isDark);
    }
}
