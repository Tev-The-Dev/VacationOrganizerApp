// java
package com.zybooks.d308vacationplanner;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

public class App extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "App";
    private int mStarted = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        mStarted++;
        if (mStarted == 1) {
            Log.i(TAG, "App foregrounded");
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        mStarted--;
        if (mStarted <= 0) {
            mStarted = 0;
            Log.i(TAG, "App backgrounded - clearing authentication");
            AuthState.clear();
        }
    }

    // Unused lifecycle callbacks
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
