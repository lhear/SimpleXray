package com.simplexray.an

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.simplexray.an.data.source.KeystoreManager
import com.simplexray.an.prefs.Preferences

class SimpleXray : Application() {

    companion object {
        lateinit var instance: SimpleXray
            private set
    }

    val prefs by lazy { Preferences(instance) }
    val keystoreManager by lazy { KeystoreManager(instance, prefs) }

    private var activityCount = 0

    override fun onCreate() {
        super.onCreate()
        instance = this

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                activityCount++
                if (activityCount == 1) {
                    onAppForegrounded()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                activityCount--
                if (activityCount == 0) {
                    onAppBackgrounded()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun onAppForegrounded() {
        if (!prefs.profileProtectionEnabled) return
        keystoreManager.loadCachedKeyAsync()
        Log.d("Lifecycle", "App entered foreground, loaded key")
    }

    private fun onAppBackgrounded() {
        keystoreManager.clearCachedKey()
        Log.d("Lifecycle", "App entered background, cleared key from memory")
    }
}