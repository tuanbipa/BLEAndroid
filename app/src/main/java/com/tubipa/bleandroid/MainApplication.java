package com.tubipa.bleandroid;

import android.app.Application;

public class MainApplication extends Application {
    private static MainApplication sInstance;
    public static MainApplication getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }
}
