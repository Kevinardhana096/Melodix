package com.example.melodix;

import android.app.Application;
import android.content.Context;

/**
 * Application class to provide global context for the app
 */
public class MelodixApplication extends Application {
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    /**
     * Get the application context
     * @return Application context
     */
    public static Context getAppContext() {
        return appContext;
    }
}
