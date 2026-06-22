package com.zayan.locationtracker.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.zayan.locationtracker.utils.Constants;

public class AppSettings {

    private static final String TAG = Constants.LOG_TAG + "/AppSettings";

    private static volatile AppSettings instance;

    private final SharedPreferences prefs;

    private AppSettings(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AppSettings getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppSettings.class) {
                if (instance == null) {
                    instance = new AppSettings(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public long getIntervalMs() {
        long stored = prefs.getLong(Constants.PREF_KEY_INTERVAL_MS, Constants.DEFAULT_INTERVAL_MS);
        for (long option : Constants.INTERVAL_OPTIONS_MS) {
            if (stored == option) return stored;
        }
        // stored value isn't a known option — reset to default
        Log.w(TAG, "getIntervalMs: " + stored + "ms is not a valid option, resetting.");
        setIntervalMs(Constants.DEFAULT_INTERVAL_MS);
        return Constants.DEFAULT_INTERVAL_MS;
    }

    public void setIntervalMs(long intervalMs) {
        prefs.edit().putLong(Constants.PREF_KEY_INTERVAL_MS, intervalMs).apply();
        Log.d(TAG, "setIntervalMs: " + intervalMs + "ms");
    }

    public int getIntervalIndex() {
        long current = getIntervalMs();
        for (int i = 0; i < Constants.INTERVAL_OPTIONS_MS.length; i++) {
            if (Constants.INTERVAL_OPTIONS_MS[i] == current) return i;
        }
        return Constants.DEFAULT_INTERVAL_INDEX;
    }

    public void setWasTracking(boolean wasTracking) {
        prefs.edit().putBoolean(Constants.PREF_KEY_WAS_TRACKING, wasTracking).commit();
        Log.d(TAG, "setWasTracking: " + wasTracking);
    }

    public boolean wasTracking() {
        return prefs.getBoolean(Constants.PREF_KEY_WAS_TRACKING, false);
    }

    public void resetToDefaults() {
        prefs.edit().clear().commit();
        Log.d(TAG, "resetToDefaults: all settings cleared.");
    }

    @NonNull
    public String getIntervalLabel() {
        int index = getIntervalIndex();
        if (index >= 0 && index < Constants.INTERVAL_LABELS.length) {
            return Constants.INTERVAL_LABELS[index];
        }
        return Constants.INTERVAL_LABELS[Constants.DEFAULT_INTERVAL_INDEX];
    }
}
