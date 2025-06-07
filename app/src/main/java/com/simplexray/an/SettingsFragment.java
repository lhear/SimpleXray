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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragmentCompat implements MenuProvider {
    private static final String IPV4_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);
    private static final String IPV6_REGEX = "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$";
    private static final Pattern IPV6_PATTERN = Pattern.compile(IPV6_REGEX);
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

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        prefs = new Preferences(requireContext());
        Preference appsPreference = findPreference("apps");
        if (appsPreference != null) {
            appsPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getActivity(), AppListActivity.class));
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
        EditTextPreference httpPortPreference = findPreference("HttpPort");
        EditTextPreference socksPortPreference = findPreference("SocksPort");
        EditTextPreference dnsIpv4Preference = findPreference("DnsIpv4");
        EditTextPreference dnsIpv6Preference = findPreference("DnsIpv6");
        CheckBoxPreference ipv6Preference = findPreference("Ipv6");
        CheckBoxPreference httpProxyEnabledPreference = findPreference("HttpProxyEnabled");
        if (httpPortPreference != null) {
            httpPortPreference.setText(String.valueOf(prefs.getHttpPort()));
            httpPortPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String stringValue = (String) newValue;
                try {
                    int port = Integer.parseInt(stringValue);
                    if (port > 1024 && port <= 65535) {
                        prefs.setHttpPort(port);
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
        if (httpProxyEnabledPreference != null) {
            if (httpPortPreference != null) {
                httpPortPreference.setEnabled(httpProxyEnabledPreference.isChecked());
            }
            httpProxyEnabledPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isChecked = (Boolean) newValue;
                if (httpPortPreference != null) {
                    httpPortPreference.setEnabled(isChecked);
                }
                prefs.setHttpProxyEnabled(isChecked);
                return true;
            });
        }
        CheckBoxPreference bypassLanPreference = findPreference("BypassLan");
        if (bypassLanPreference != null) {
            bypassLanPreference.setChecked(prefs.getBypassLan());
        }
        CheckBoxPreference useTemplatePreference = findPreference("UseTemplate");
        if (useTemplatePreference != null) {
            useTemplatePreference.setChecked(prefs.getUseTemplate());
        }
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
        EditTextPreference httpPortPreference = findPreference("HttpPort");
        if (httpPortPreference != null) {
            httpPortPreference.setText(String.valueOf(prefs.getHttpPort()));
            CheckBoxPreference httpProxyEnabledPreference = findPreference("HttpProxyEnabled");
            if (httpProxyEnabledPreference != null) {
                httpPortPreference.setEnabled(httpProxyEnabledPreference.isChecked());
            }
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
}