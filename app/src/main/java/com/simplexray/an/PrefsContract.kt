package com.simplexray.an

import android.net.Uri
import android.provider.BaseColumns

object PrefsContract {
    const val AUTHORITY: String = "com.simplexray.an.prefsprovider"
    val BASE_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
    const val PATH_PREFS: String = "prefs"

    object PrefsEntry : BaseColumns {
        @JvmField
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_PREFS).build()
        const val CONTENT_TYPE: String =
            "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_PREFS"
        const val CONTENT_ITEM_TYPE: String =
            "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_PREFS"
        const val COLUMN_PREF_KEY: String = "pref_key"
        const val COLUMN_PREF_VALUE: String = "pref_value"
        const val COLUMN_PREF_TYPE: String = "pref_type"
    }
}
