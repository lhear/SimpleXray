package com.simplexray.an.xray

import android.content.Context
import java.io.File

object AssetsInstaller {
    fun ensureAssets(context: Context) {
        copyIfMissing(context, "geoip.dat")
        copyIfMissing(context, "geosite.dat")
        // Optional pattern assets
        copyIfMissing(context, "cdn_patterns.json")
        copyIfMissing(context, "asn_v4.csv")
    }

    private fun copyIfMissing(context: Context, name: String) {
        val outFile = File(context.filesDir, name)
        if (outFile.exists() && outFile.length() > 0) return
        try {
            context.assets.open(name).use { ins ->
                outFile.outputStream().use { outs -> ins.copyTo(outs) }
            }
        } catch (_: Throwable) {
            // ignore missing optional assets
        }
    }
}

