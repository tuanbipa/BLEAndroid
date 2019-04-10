package com.tubipa.bleandroid;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SaveStore {

    public static void saveInt(String key, int value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        sharedPreferences.edit().putInt(key, value).apply();
    }

    public static int getInt(String key, int defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        return sharedPreferences.getInt(key, defaultValue);
    }

    public static void saveBoolean(String key, boolean value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    public static void saveString(String key, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        sharedPreferences.edit().putString(key, value).apply();
    }

    public static String getString(String key, String defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApplication.getInstance());
        return sharedPreferences.getString(key, defaultValue);
    }

}
