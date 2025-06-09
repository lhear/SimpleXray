package com.simplexray.an;

import static com.simplexray.an.TProxyService.getNativeLibraryDir;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragmentCompat implements MenuProvider {
    private static final String IPV4_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);
    private static final String IPV6_REGEX = "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$";
    private static final Pattern IPV6_PATTERN = Pattern.compile(IPV6_REGEX);
    private Preferences prefs;
    private OnConfigActionListener configActionListener;
    private ActivityResultLauncher<String[]> geoipFilePickerLauncher;
    private ActivityResultLauncher<String[]> geositeFilePickerLauncher;

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

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        prefs = new Preferences(requireContext());

        geoipFilePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                importRuleFile(uri, "geoip.dat");
            } else {
                Log.d("SettingsFragment", "Geoip file picking cancelled or failed (URI is null).");
            }
        });

        geositeFilePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                importRuleFile(uri, "geosite.dat");
            } else {
                Log.d("SettingsFragment", "Geosite file picking cancelled or failed (URI is null).");
            }
        });

        Preference appsPreference = findPreference("apps");
        if (appsPreference != null) {
            appsPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getActivity(), AppListActivity.class));
                return true;
            });
        }
        Preference geoip = findPreference("geoip");
        if (geoip != null) {
            geoip.setOnPreferenceClickListener(preference -> {
                geoipFilePickerLauncher.launch(new String[]{"*/*"});
                return true;
            });
        }
        Preference geosite = findPreference("geosite");
        if (geosite != null) {
            geosite.setOnPreferenceClickListener(preference -> {
                geositeFilePickerLauncher.launch(new String[]{"*/*"});
                return true;
            });
        }
        Preference clearFiles = findPreference("clear_files");
        if (clearFiles != null) {
            clearFiles.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.rule_file_restore_default_title)
                        .setMessage(R.string.rule_file_restore_default_message)
                        .setPositiveButton(R.string.confirm, (dialog, which) -> {
                            restoreDefaultRuleFile("geoip.dat");
                            restoreDefaultRuleFile("geosite.dat");
                            Toast.makeText(requireContext(), R.string.rule_file_restore_default_success, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
                return true;
            });
        }
        Preference source = findPreference("source");
        if (source != null) {
            source.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.source_url)));
                startActivity(browserIntent);
                return true;
            });
        }
        Preference version = findPreference("version");
        if (version != null) {
            PackageManager packageManager = requireContext().getPackageManager();
            String packageName = requireContext().getPackageName();
            PackageInfo packageInfo;
            try {
                packageInfo = packageManager.getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            String versionName = packageInfo.versionName;
            version.setSummary(versionName);
        }
        Preference kernel = findPreference("kernel");
        if (kernel != null) {
            String libraryDir = getNativeLibraryDir(requireContext());
            String xrayPath = libraryDir + "/libxray.so";
            try {
                Process process = Runtime.getRuntime().exec(xrayPath + " -version");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String firstLine = reader.readLine();
                process.destroy();
                kernel.setSummary(firstLine);
            } catch (IOException e) {
                Log.e("SettingsFragment", "Failed to get xray version", e);
                throw new RuntimeException(e);
            }
        }
        EditTextPreference socksPortPreference = findPreference("SocksPort");
        EditTextPreference dnsIpv4Preference = findPreference("DnsIpv4");
        EditTextPreference dnsIpv6Preference = findPreference("DnsIpv6");
        CheckBoxPreference ipv6Preference = findPreference("Ipv6");
        if (socksPortPreference != null) {
            socksPortPreference.setText(String.valueOf(prefs.getSocksPort()));
            socksPortPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String stringValue = (String) newValue;
                try {
                    int port = Integer.parseInt(stringValue);
                    if (port > 1024 && port <= 65535) {
                        prefs.setSocksPort(port);
                        return true;
                    } else {
                        new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_port_range).setPositiveButton(R.string.confirm, null).show();
                        return false;
                    }
                } catch (NumberFormatException e) {
                    new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_port).setPositiveButton(R.string.confirm, null).show();
                    return false;
                }
            });
        }
        if (dnsIpv4Preference != null) {
            dnsIpv4Preference.setText(prefs.getDnsIpv4());
            dnsIpv4Preference.setOnPreferenceChangeListener((preference, newValue) -> {
                String stringValue = (String) newValue;
                Matcher matcher = IPV4_PATTERN.matcher(stringValue);
                if (matcher.matches()) {
                    prefs.setDnsIpv4(stringValue);
                    return true;
                } else {
                    new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_ipv4).setPositiveButton(R.string.confirm, null).show();
                    return false;
                }
            });
        }
        if (dnsIpv6Preference != null) {
            dnsIpv6Preference.setText(prefs.getDnsIpv6());
        }
        if (ipv6Preference != null) {
            if (dnsIpv6Preference != null) {
                dnsIpv6Preference.setEnabled(ipv6Preference.isChecked());
            }
            ipv6Preference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isChecked = (Boolean) newValue;
                if (dnsIpv6Preference != null) {
                    dnsIpv6Preference.setEnabled(isChecked);
                }
                prefs.setIpv6(isChecked);
                return true;
            });
        }
        if (dnsIpv6Preference != null) {
            dnsIpv6Preference.setOnPreferenceChangeListener((preference, newValue) -> {
                String stringValue = (String) newValue;
                Matcher matcher = IPV6_PATTERN.matcher(stringValue);
                if (matcher.matches()) {
                    prefs.setDnsIpv6(stringValue);
                    return true;
                } else {
                    new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_ipv6).setPositiveButton(R.string.confirm, null).show();
                    return false;
                }
            });
        }

        refreshPreferences();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    public void refreshPreferences() {
        if (prefs == null) {
            prefs = new Preferences(requireContext());
        }
        EditTextPreference socksPortPreference = findPreference("SocksPort");
        if (socksPortPreference != null) {
            socksPortPreference.setText(String.valueOf(prefs.getSocksPort()));
        }
        EditTextPreference dnsIpv4Preference = findPreference("DnsIpv4");
        if (dnsIpv4Preference != null) {
            dnsIpv4Preference.setText(prefs.getDnsIpv4());
        }
        EditTextPreference dnsIpv6Preference = findPreference("DnsIpv6");
        if (dnsIpv6Preference != null) {
            dnsIpv6Preference.setText(prefs.getDnsIpv6());
            CheckBoxPreference ipv6Preference = findPreference("Ipv6");
            if (ipv6Preference != null) {
                dnsIpv6Preference.setEnabled(ipv6Preference.isChecked());
            }
        }
        CheckBoxPreference ipv6Preference = findPreference("Ipv6");
        if (ipv6Preference != null) {
            ipv6Preference.setChecked(prefs.getIpv6());
        }
        CheckBoxPreference bypassLanPreference = findPreference("BypassLan");
        if (bypassLanPreference != null) {
            bypassLanPreference.setChecked(prefs.getBypassLan());
        }
        CheckBoxPreference useTemplatePreference = findPreference("UseTemplate");
        if (useTemplatePreference != null) {
            useTemplatePreference.setChecked(prefs.getUseTemplate());
        }
        CheckBoxPreference httpProxyEnabledPreference = findPreference("HttpProxyEnabled");
        if (httpProxyEnabledPreference != null) {
            httpProxyEnabledPreference.setChecked(prefs.getHttpProxyEnabled());
        }

        Preference geoip = findPreference("geoip");
        boolean isGeoipCustom = false;
        if (geoip != null) {
            if (prefs.getCustomGeoipImported()) {
                File geoipFile = new File(requireContext().getFilesDir(), "geoip.dat");
                if (geoipFile.exists()) {
                    long lastModified = geoipFile.lastModified();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
                    geoip.setSummary(getString(R.string.rule_file_imported_prefix) + " " + sdf.format(new Date(lastModified)));
                    isGeoipCustom = true;
                } else {
                    geoip.setSummary(R.string.rule_file_missing_error);
                    prefs.setCustomGeoipImported(false);
                    Log.e("SettingsFragment", "Custom geoip file expected but not found.");
                }
            } else {
                geoip.setSummary(R.string.rule_file_default);
            }
        }

        Preference geosite = findPreference("geosite");
        boolean isGeositeCustom = false;
        if (geosite != null) {
            if (prefs.getCustomGeositeImported()) {
                File geositeFile = new File(requireContext().getFilesDir(), "geosite.dat");
                if (geositeFile.exists()) {
                    long lastModified = geositeFile.lastModified();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
                    geosite.setSummary(getString(R.string.rule_file_imported_prefix) + " " + sdf.format(new Date(lastModified)));
                    isGeositeCustom = true;
                } else {
                    geosite.setSummary(R.string.rule_file_missing_error);
                    prefs.setCustomGeositeImported(false);
                    Log.e("SettingsFragment", "Custom geosite file expected but not found.");
                }
            } else {
                geosite.setSummary(R.string.rule_file_default);
            }
        }

        Preference clearFiles = findPreference("clear_files");
        if (clearFiles != null) {
            clearFiles.setEnabled(isGeoipCustom || isGeositeCustom);
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        Log.d("SettingsFragment", "onCreateMenu");
        MenuItem addConfigItem = menu.findItem(R.id.menu_add_config);
        MenuItem controlMenuItem = menu.findItem(R.id.menu_control);
        MenuItem importConfigItem = menu.findItem(R.id.menu_import_from_clipboard);
        MenuItem backupItem = menu.findItem(R.id.menu_backup);
        MenuItem restoreItem = menu.findItem(R.id.menu_restore);
        MenuItem exportItem = menu.findItem(R.id.menu_export);

        if (addConfigItem != null) addConfigItem.setVisible(false);
        if (controlMenuItem != null) controlMenuItem.setVisible(false);
        if (importConfigItem != null) importConfigItem.setVisible(false);
        if (backupItem != null) backupItem.setVisible(true);
        if (restoreItem != null) restoreItem.setVisible(true);
        if (exportItem != null) exportItem.setVisible(false);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_backup) {
            if (configActionListener != null) {
                configActionListener.performBackup();
                return true;
            }
        } else if (id == R.id.menu_restore) {
            if (configActionListener != null) {
                configActionListener.performRestore();
                return true;
            }
        }
        return false;
    }

    private void importRuleFile(Uri uri, String filename) {
        if (configActionListener instanceof MainActivity) {
            MainActivity activity = (MainActivity) configActionListener;
            activity.getExecutorService().submit(() -> {
                File targetFile = new File(requireContext().getFilesDir(), filename);
                try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(targetFile)) {

                    if (inputStream == null) {
                        throw new IOException("Failed to open input stream for URI: " + uri);
                    }

                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }

                    requireActivity().runOnUiThread(() -> {
                        if ("geoip.dat".equals(filename)) {
                            prefs.setCustomGeoipImported(true);
                        } else if ("geosite.dat".equals(filename)) {
                            prefs.setCustomGeositeImported(true);
                        }
                        refreshPreferences();
                        Toast.makeText(requireContext(), filename + " 导入成功", Toast.LENGTH_SHORT).show();
                    });

                    Log.d("SettingsFragment", "Successfully imported " + filename + " from URI: " + uri);

                } catch (IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.rule_file_import_failed_prefix) + " " + filename + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if ("geoip.dat".equals(filename)) {
                            prefs.setCustomGeoipImported(false);
                        } else if ("geosite.dat".equals(filename)) {
                            prefs.setCustomGeositeImported(false);
                        }
                        refreshPreferences();
                    });
                    Log.e("SettingsFragment", "Error importing rule file: " + filename, e);
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.rule_file_import_failed_prefix) + " " + filename + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if ("geoip.dat".equals(filename)) {
                            prefs.setCustomGeoipImported(false);
                        } else if ("geosite.dat".equals(filename)) {
                            prefs.setCustomGeositeImported(false);
                        }
                        refreshPreferences();
                    });
                    Log.e("SettingsFragment", "Unexpected error during rule file import: " + filename, e);
                }
            });
        } else {
            Toast.makeText(requireContext(), R.string.rule_file_import_unavailable, Toast.LENGTH_SHORT).show();
            Log.e("SettingsFragment", "MainActivity does not implement OnConfigActionListener or provide ExecutorService.");
        }
    }

    private void restoreDefaultRuleFile(String filename) {
        File targetFile = new File(requireContext().getFilesDir(), filename);
        boolean deleted = targetFile.delete();

        if ("geoip.dat".equals(filename)) {
            prefs.setCustomGeoipImported(false);
        } else if ("geosite.dat".equals(filename)) {
            prefs.setCustomGeositeImported(false);
        }

        if (configActionListener != null) {
            configActionListener.triggerAssetExtraction();
        }

        refreshPreferences();
        Log.d("SettingsFragment", "Restored default for " + filename + ". File deleted: " + deleted);
    }
}