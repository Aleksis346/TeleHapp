package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;

public class TeleHappConfig {
    private static final String PREFS_NAME = "telehapp_config";
    private static final String KEY_CUSTOM_FEATURES_ENABLED = "custom_features_enabled";
    private static final String KEY_SELECTED_APP_ICON = "selected_app_icon";
    private static final String KEY_TITLE_STYLE_INDEX = "title_style_index";
    private static final String KEY_USE_PREMIUM_DESIGN = "use_premium_design";

    public static final String ICON_DEFAULT = "default";
    public static final String ICON_MEME_1 = "meme_1";
    public static final String ICON_MEME_2 = "meme_2";
    public static final String ICON_MEME_3 = "meme_3";
    public static final String ICON_MEME_4 = "meme_4";

    public static final String[] TITLE_STYLES = new String[]{
            "TeleHapp",
            "★ TeleHapp ★",
            "𝓣𝓮𝓵𝓮𝓗𝓪𝓹𝓹",
            "[ TeleHapp ]",
            "⚡ TeleHapp ⚡"
    };

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

    public static String getSelectedAppIcon() {
        try {
            return getPrefs().getString(KEY_SELECTED_APP_ICON, ICON_DEFAULT);
        } catch (Throwable t) {
            return ICON_DEFAULT;
        }
    }

    public static void setSelectedAppIcon(String iconId) {
        try {
            getPrefs().edit().putString(KEY_SELECTED_APP_ICON, iconId == null ? ICON_DEFAULT : iconId).apply();
        } catch (Throwable ignore) {
        }
    }

    public static int getTitleStyleIndex() {
        try {
            int index = getPrefs().getInt(KEY_TITLE_STYLE_INDEX, 0);
            if (index < 0 || index >= TITLE_STYLES.length) {
                index = 0;
            }
            return index;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setTitleStyleIndex(int index) {
        try {
            if (index < 0 || index >= TITLE_STYLES.length) {
                index = 0;
            }
            getPrefs().edit().putInt(KEY_TITLE_STYLE_INDEX, index).apply();
        } catch (Throwable ignore) {
        }
    }

    public static boolean isUsePremiumDesign() {
        try {
            if (!getPrefs().contains(KEY_USE_PREMIUM_DESIGN)) {
                return isCurrentUserPremium();
            }
            return getPrefs().getBoolean(KEY_USE_PREMIUM_DESIGN, isCurrentUserPremium());
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setUsePremiumDesign(boolean enabled) {
        try {
            getPrefs().edit().putBoolean(KEY_USE_PREMIUM_DESIGN, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    public static boolean isCurrentUserPremium() {
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated() && UserConfig.getInstance(a).isPremium()) {
                    return true;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    public static String getDialogsTitle() {
        if (!isCustomFeaturesEnabled()) {
            return "TeleHapp";
        }
        return TITLE_STYLES[getTitleStyleIndex()];
    }
}
