package com.simplexray.an;

import android.net.Uri;
import android.provider.BaseColumns;

public final class PrefsContract {
    public static final String AUTHORITY = "com.simplexray.an.prefsprovider";
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String PATH_PREFS = "prefs";

    private PrefsContract() {
    }

    public static final class PrefsEntry implements BaseColumns {
        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_PREFS).build();
        public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + PATH_PREFS;
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd." + AUTHORITY + "." + PATH_PREFS;
        public static final String COLUMN_PREF_KEY = "pref_key";
        public static final String COLUMN_PREF_VALUE = "pref_value";
        public static final String COLUMN_PREF_TYPE = "pref_type"; // To store type like String, int, boolean
    }
}
