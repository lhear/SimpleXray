package com.simplexray.an

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager

class PrefsProvider : ContentProvider() {
    private var prefs: SharedPreferences? = null

    override fun onCreate(): Boolean {
        prefs = PreferenceManager.getDefaultSharedPreferences(context!!)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val match = sUriMatcher.match(uri)
        var key: String? = null
        if (match == PREFS_WITH_KEY) {
            key = uri.lastPathSegment
        } else if (match != PREFS) {
            throw UnsupportedOperationException("Unknown uri: $uri")
        }
        val cursor = MatrixCursor(
            arrayOf(
                PrefsContract.PrefsEntry.COLUMN_PREF_KEY,
                PrefsContract.PrefsEntry.COLUMN_PREF_VALUE,
                PrefsContract.PrefsEntry.COLUMN_PREF_TYPE
            )
        )
        if (key != null) {
            var value: Any? = null
            var type: String? = null
            if (prefs!!.contains(key)) {
                val valueAndType: Pair<Any?, String?>? = try {
                    prefs!!.getString(key, null)?.let { v -> Pair<Any?, String?>(v, "String") }
                } catch (e: ClassCastException) {
                    null
                } ?: try {
                    Pair<Any?, String?>(prefs!!.getBoolean(key, false), "Boolean")
                } catch (e: ClassCastException) {
                    null
                } ?: try {
                    Pair<Any?, String?>(prefs!!.getInt(key, 0), "Integer")
                } catch (e: ClassCastException) {
                    null
                } ?: try {
                    Pair<Any?, String?>(prefs!!.getLong(key, 0L), "Long")
                } catch (e: ClassCastException) {
                    null
                } ?: try {
                    Pair<Any?, String?>(prefs!!.getFloat(key, 0f), "Float")
                } catch (e: ClassCastException) {
                    null
                } ?: try {
                    prefs!!.getStringSet(key, null)
                        ?.let { v -> Pair<Any?, String?>(v, "StringSet") }
                } catch (e: ClassCastException) {
                    Log.w(TAG, "Error retrieving key '$key' as StringSet, giving up", e)
                    null
                }

                value = valueAndType?.first
                type = valueAndType?.second
            }
            if (value != null) {
                cursor.addRow(arrayOf<Any?>(key, value.toString(), type))
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        val match = sUriMatcher.match(uri)
        return when (match) {
            PREFS -> PrefsContract.PrefsEntry.CONTENT_TYPE
            PREFS_WITH_KEY -> PrefsContract.PrefsEntry.CONTENT_ITEM_TYPE
            else -> throw UnsupportedOperationException("Unknown uri: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert not supported by this provider.")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Delete not supported by this provider.")
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int {
        val match = sUriMatcher.match(uri)
        var rowsAffected = 0
        if (match == PREFS_WITH_KEY) {
            val key = uri.lastPathSegment
            if (key != null && values != null && values.containsKey(PrefsContract.PrefsEntry.COLUMN_PREF_VALUE)) {
                val editor = prefs!!.edit()
                when (val value = values[PrefsContract.PrefsEntry.COLUMN_PREF_VALUE]) {
                    is String -> {
                        editor.putString(key, value)
                    }

                    is Int -> {
                        editor.putInt(key, value)
                    }

                    is Boolean -> {
                        editor.putBoolean(key, value)
                    }

                    is Long -> {
                        editor.putLong(key, value)
                    }

                    is Float -> {
                        editor.putFloat(key, value)
                    }

                    is Set<*> -> {
                        val stringSet = value.filterIsInstance<String>().toSet()
                        if (stringSet.size == value.size) {
                            editor.putStringSet(key, stringSet)
                        } else {
                            Log.e(
                                TAG,
                                "Value for key $key is a Set but contains non-String or null elements (putStringSet requires Set<String>)."
                            )
                        }
                    }

                    else -> {
                        Log.e(TAG, "Unsupported value type for key: $key")
                    }
                }
                editor.apply()
                rowsAffected = 1
                val context = context
                context?.contentResolver?.notifyChange(uri, null)
            }
        } else {
            throw UnsupportedOperationException("Unknown uri for update: $uri")
        }
        return rowsAffected
    }

    companion object {
        private const val TAG = "PrefsProvider"
        private const val PREFS = 100
        private const val PREFS_WITH_KEY = 101
        private val sUriMatcher = buildUriMatcher()
        private fun buildUriMatcher(): UriMatcher {
            val matcher = UriMatcher(UriMatcher.NO_MATCH)
            val authority = PrefsContract.AUTHORITY
            matcher.addURI(authority, PrefsContract.PATH_PREFS, PREFS)
            matcher.addURI(authority, PrefsContract.PATH_PREFS + "/*", PREFS_WITH_KEY)
            return matcher
        }
    }
}