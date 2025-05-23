package com.simplexray.an;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Objects;
import java.util.Set;

public class PrefsProvider extends ContentProvider {
    private static final String TAG = "PrefsProvider";
    private static final int PREFS = 100;
    private static final int PREFS_WITH_KEY = 101;
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private SharedPreferences prefs;

    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = PrefsContract.AUTHORITY;
        matcher.addURI(authority, PrefsContract.PATH_PREFS, PREFS);
        matcher.addURI(authority, PrefsContract.PATH_PREFS + "/*", PREFS_WITH_KEY);
        return matcher;
    }

    @Override
    public boolean onCreate() {
        prefs = PreferenceManager.getDefaultSharedPreferences(Objects.requireNonNull(getContext()));
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int match = sUriMatcher.match(uri);
        String key = null;
        if (match == PREFS_WITH_KEY) {
            key = uri.getLastPathSegment();
        } else if (match != PREFS) {
            throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{PrefsContract.PrefsEntry.COLUMN_PREF_KEY, PrefsContract.PrefsEntry.COLUMN_PREF_VALUE, PrefsContract.PrefsEntry.COLUMN_PREF_TYPE});
        if (key != null) {
            Object value = null;
            String type = null;
            if (prefs.contains(key)) {
                try {
                    value = prefs.getString(key, null);
                    if (value != null) type = "String";
                } catch (ClassCastException e) {
                    try {
                        value = prefs.getInt(key, 0);
                        type = "Integer";
                    } catch (ClassCastException e2) {
                        try {
                            value = prefs.getBoolean(key, false);
                            type = "Boolean";
                        } catch (ClassCastException e3) {
                            try {
                                value = prefs.getLong(key, 0L);
                                type = "Long";
                            } catch (ClassCastException e4) {
                                try {
                                    value = prefs.getFloat(key, 0f);
                                    type = "Float";
                                } catch (ClassCastException e5) {
                                    try {
                                        value = prefs.getStringSet(key, null);
                                        if (value != null) type = "StringSet";
                                    } catch (ClassCastException e6) {
                                        Log.e(TAG, "Unknown type for key: " + key, e6);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (value != null) {
                cursor.addRow(new Object[]{key, value.toString(), type});
            }
        }
        return cursor;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case PREFS:
                return PrefsContract.PrefsEntry.CONTENT_TYPE;
            case PREFS_WITH_KEY:
                return PrefsContract.PrefsEntry.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported by this provider.");
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported by this provider.");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        int rowsAffected = 0;
        if (match == PREFS_WITH_KEY) {
            String key = uri.getLastPathSegment();
            if (key != null && values != null && values.containsKey(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE)) {
                SharedPreferences.Editor editor = prefs.edit();
                Object value = values.get(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE);
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Set) {
                    try {
                        editor.putStringSet(key, (Set<String>) value);
                    } catch (ClassCastException e) {
                        Log.e(TAG, "Value for key " + key + " is not a Set<String>", e);
                    }
                } else {
                    Log.e(TAG, "Unsupported value type for key: " + key);
                }
                editor.apply();
                rowsAffected = 1;
                Context context = getContext();
                if (context != null) {
                    context.getContentResolver().notifyChange(uri, null);
                }
            }
        } else {
            throw new UnsupportedOperationException("Unknown uri for update: " + uri);
        }
        return rowsAffected;
    }
}