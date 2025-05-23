package com.simplexray.an;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

public class Preferences {
    public static final String PREFS_NAME = "SocksPrefs";
    public static final String SOCKS_ADDR = "SocksAddr";
    public static final String SOCKS_PORT = "SocksPort";
    public static final String SOCKS_USER = "SocksUser";
    public static final String SOCKS_PASS = "SocksPass";
    public static final String DNS_IPV4 = "DnsIpv4";
    public static final String DNS_IPV6 = "DnsIpv6";
    public static final String IPV4 = "Ipv4";
    public static final String IPV6 = "Ipv6";
    public static final String GLOBAL = "Global";
    public static final String UDP_IN_TCP = "UdpInTcp";
    public static final String APPS = "Apps";
    public static final String ENABLE = "Enable";
    public static final String SELECTED_CONFIG_PATH = "SelectedConfigPath";
    public static final String BYPASS_LAN = "BypassLan";
    public static final String USE_TEMPLATE = "UseTemplate";
    public static final String HTTP_PORT = "HttpPort";
    public static final String HTTP_PROXY_ENABLED = "HttpProxyEnabled";
    private static final String TAG = "Preferences";
    private final ContentResolver contentResolver;
    private final Gson gson;

    public Preferences(Context context) {
        Context context1 = context.getApplicationContext();
        this.contentResolver = context1.getContentResolver();
        this.gson = new Gson();
    }

    private String getValueFromProvider(String key) {
        Uri uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build();
        try (Cursor cursor = contentResolver.query(uri, new String[]{PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, PrefsContract.PrefsEntry.COLUMN_PREF_TYPE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int valueColumnIndex = cursor.getColumnIndex(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE);
                if (valueColumnIndex != -1) {
                    return cursor.getString(valueColumnIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading preference for key: " + key, e);
        }
        return null;
    }

    private String getTypeFromProvider(String key) {
        Uri uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build();
        try (Cursor cursor = contentResolver.query(uri, new String[]{PrefsContract.PrefsEntry.COLUMN_PREF_TYPE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int typeColumnIndex = cursor.getColumnIndex(PrefsContract.PrefsEntry.COLUMN_PREF_TYPE);
                if (typeColumnIndex != -1) {
                    return cursor.getString(typeColumnIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading preference type for key: " + key, e);
        }
        return null;
    }

    private void setValueInProvider(String key, Object value) {
        Uri uri = PrefsContract.PrefsEntry.CONTENT_URI.buildUpon().appendPath(key).build();
        ContentValues values = new ContentValues();
        if (value instanceof String) {
            values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, (String) value);
        } else if (value instanceof Integer) {
            values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, (Integer) value);
        } else if (value instanceof Boolean) {
            values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, (Boolean) value);
        } else if (value instanceof Long) {
            values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, (Long) value);
        } else if (value instanceof Float) {
            values.put(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, (Float) value);
        } else if (value instanceof Set) {
            Log.e(TAG, "Setting StringSet via Content Provider requires serialization logic.");
            return;
        } else {
            Log.e(TAG, "Unsupported type for key: " + key + " with value: " + value);
            return;
        }
        try {
            int rows = contentResolver.update(uri, values, null, null);
            if (rows == 0) {
                Log.w(TAG, "Update failed or key not found for: " + key);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting preference for key: " + key, e);
        }
    }

    public String getSocksAddress() {
        String value = getValueFromProvider(SOCKS_ADDR);
        return value != null ? value : "127.0.0.1";
    }

    public int getSocksPort() {
        String value = getValueFromProvider(SOCKS_PORT);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse SocksPort as Integer: " + value, e);
            }
        }
        return 10808;
    }

    public void setSocksPort(int port) {
        setValueInProvider(SOCKS_PORT, port);
    }

    public String getSocksUsername() {
        String value = getValueFromProvider(SOCKS_USER);
        return value != null ? value : "";
    }

    public String getSocksPassword() {
        String value = getValueFromProvider(SOCKS_PASS);
        return value != null ? value : "";
    }

    public String getDnsIpv4() {
        String value = getValueFromProvider(DNS_IPV4);
        return value != null ? value : "8.8.8.8";
    }

    public void setDnsIpv4(String addr) {
        setValueInProvider(DNS_IPV4, addr);
    }

    public String getDnsIpv6() {
        String value = getValueFromProvider(DNS_IPV6);
        return value != null ? value : "2001:4860:4860::8888";
    }

    public void setDnsIpv6(String addr) {
        setValueInProvider(DNS_IPV6, addr);
    }

    public boolean getUdpInTcp() {
        String value = getValueFromProvider(UDP_IN_TCP);
        String type = getTypeFromProvider(UDP_IN_TCP);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    public boolean getIpv4() {
        String value = getValueFromProvider(IPV4);
        String type = getTypeFromProvider(IPV4);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return true;
    }

    public void setIpv4(boolean enable) {
        setValueInProvider(IPV4, enable);
    }

    public boolean getIpv6() {
        String value = getValueFromProvider(IPV6);
        String type = getTypeFromProvider(IPV6);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    public void setIpv6(boolean enable) {
        setValueInProvider(IPV6, enable);
    }

    public boolean getGlobal() {
        String value = getValueFromProvider(GLOBAL);
        String type = getTypeFromProvider(GLOBAL);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    public void setGlobal(boolean enable) {
        setValueInProvider(GLOBAL, enable);
    }

    public Set<String> getApps() {
        String jsonSet = getValueFromProvider(APPS);
        if (jsonSet != null) {
            try {
                Type type = new TypeToken<Set<String>>() {
                }.getType();
                return gson.fromJson(jsonSet, type);
            } catch (Exception e) {
                Log.e(TAG, "Error deserializing APPS StringSet", e);
                return new HashSet<>();
            }
        }
        return new HashSet<>();
    }

    public void setApps(Set<String> apps) {
        String jsonSet = gson.toJson(apps);
        setValueInProvider(APPS, jsonSet);
    }

    public boolean getEnable() {
        String value = getValueFromProvider(ENABLE);
        String type = getTypeFromProvider(ENABLE);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    public void setEnable(boolean enable) {
        setValueInProvider(ENABLE, enable);
    }

    public int getTunnelMtu() {
        return 8500;
    }

    public String getTunnelIpv4Address() {
        return "198.18.0.1";
    }

    public int getTunnelIpv4Prefix() {
        return 32;
    }

    public String getTunnelIpv6Address() {
        return "fc00::1";
    }

    public int getTunnelIpv6Prefix() {
        return 128;
    }

    public int getTaskStackSize() {
        return 81920;
    }

    public String getSelectedConfigPath() {
        return getValueFromProvider(SELECTED_CONFIG_PATH);
    }

    public void setSelectedConfigPath(String path) {
        setValueInProvider(SELECTED_CONFIG_PATH, path);
    }

    public boolean getBypassLan() {
        String value = getValueFromProvider(BYPASS_LAN);
        String type = getTypeFromProvider(BYPASS_LAN);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return true;
    }

    public boolean getUseTemplate() {
        String value = getValueFromProvider(USE_TEMPLATE);
        String type = getTypeFromProvider(USE_TEMPLATE);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return true;
    }

    public int getHttpPort() {
        String value = getValueFromProvider(HTTP_PORT);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse HttpPort as Integer: " + value, e);
            }
        }
        return 10809;
    }

    public void setHttpPort(int port) {
        setValueInProvider(HTTP_PORT, port);
    }

    public boolean getHttpProxyEnabled() {
        String value = getValueFromProvider(HTTP_PROXY_ENABLED);
        String type = getTypeFromProvider(HTTP_PROXY_ENABLED);
        if (value != null && "Boolean".equals(type)) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    public void setHttpProxyEnabled(boolean enable) {
        setValueInProvider(HTTP_PROXY_ENABLED, enable);
    }
}