package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

public class TeleHappConfig {
    private static final String PREFS_NAME = "telehapp_config";
    private static final String KEY_CUSTOM_FEATURES_ENABLED = "custom_features_enabled";

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isCustomFeaturesEnabled() {
        try {
            return getPrefs().getBoolean(KEY_CUSTOM_FEATURES_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setCustomFeaturesEnabled(boolean enabled) {
        try {
            getPrefs().edit().putBoolean(KEY_CUSTOM_FEATURES_ENABLED, enabled).apply();
        } catch (Throwable ignore) {
        }
    }
}
