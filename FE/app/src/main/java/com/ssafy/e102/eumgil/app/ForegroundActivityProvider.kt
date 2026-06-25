package com.ssafy.e102.eumgil.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object ForegroundActivityProvider : Application.ActivityLifecycleCallbacks {
    private var activityReference: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = activityReference?.get()

    override fun onActivityResumed(activity: Activity) {
        activityReference = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (activityReference?.get() == activity) {
            activityReference = null
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
