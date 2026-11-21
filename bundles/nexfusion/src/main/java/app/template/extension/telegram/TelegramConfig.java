package app.template.extension.telegram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Unified configuration and theme helper for Telegram Morphe Settings.
 * Ensures bidirectional persistence across in-app Telegram settings and NexFusion Manager.
 */
public final class TelegramConfig {

    public static final String PREF_BUNDLE_NAME = "nexfusion_patch_prefs_io.github.nexfusion.patches";
    public static final String PREF_LOCAL_NAME = "morphe_telegram_prefs";

    // Patch preference keys matching NexFusion bundle registration
    public static final String KEY_ANTI_DELETE = "io.github.nexfusion.patches/Anti-delete messages";
    public static final String KEY_ANTI_DISAPPEARING = "io.github.nexfusion.patches/Anti-disappearing media";
    public static final String KEY_DOWNLOAD_BOOST = "io.github.nexfusion.patches/Download speed boost";
    public static final String KEY_BYPASS_CHANNEL_RESTRICTIONS = "io.github.nexfusion.patches/Bypass channel restrictions";
    public static final String KEY_BYPASS_CONTENT_RESTRICTIONS = "io.github.nexfusion.patches/Bypass content restrictions";
    public static final String KEY_REMOVE_ADS = "io.github.nexfusion.patches/Remove ads";
    public static final String KEY_HIDE_TYPING = "io.github.nexfusion.patches/Hide typing indicator";
    public static final String KEY_HIDE_STORIES_READ = "io.github.nexfusion.patches/Hide Stories read status";
    public static final String KEY_ANTI_SCREENSHOT = "io.github.nexfusion.patches/Anti-screenshot notification";
    public static final String KEY_VOICE_TO_MUSIC = "io.github.nexfusion.patches/Voice to music";
    public static final String KEY_DISABLE_DOUBLE_TAP = "io.github.nexfusion.patches/Disable double-tap reaction";
    public static final String KEY_REVEAL_SPOILERS = "io.github.nexfusion.patches/Reveal spoilers";
    public static final String KEY_DISABLE_VIBRATION = "io.github.nexfusion.patches/Disable vibration";
    public static final String KEY_SHOW_SECONDS = "io.github.nexfusion.patches/Show message seconds";
    public static final String KEY_EXACT_NUMBERS = "io.github.nexfusion.patches/Exact numbers";
    public static final String KEY_UNLOCK_PREMIUM = "io.github.nexfusion.patches/Unlock Premium";
    public static final String KEY_DISABLE_AUTO_UPDATE = "io.github.nexfusion.patches/Disable auto-update";
    public static final String KEY_DISABLE_ANALYTICS = "io.github.nexfusion.patches/Disable analytics";
    public static final String KEY_BYPASS_INTEGRITY = "io.github.nexfusion.patches/Bypass integrity check";

    private TelegramConfig() {}

    public static SharedPreferences getBundlePrefs(Context context) {
        return context.getSharedPreferences(PREF_BUNDLE_NAME, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getLocalPrefs(Context context) {
        return context.getSharedPreferences(PREF_LOCAL_NAME, Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        if (context == null) return defaultValue;
        try {
            SharedPreferences bundlePrefs = getBundlePrefs(context);
            if (bundlePrefs.contains(key)) {
                return bundlePrefs.getBoolean(key, defaultValue);
            }
            SharedPreferences localPrefs = getLocalPrefs(context);
            if (localPrefs.contains(key)) {
                return localPrefs.getBoolean(key, defaultValue);
            }
        } catch (Throwable ignored) {}
        return defaultValue;
    }

    public static void setBoolean(Context context, String key, boolean value) {
        if (context == null) return;
        try {
            getBundlePrefs(context).edit().putBoolean(key, value).apply();
            getLocalPrefs(context).edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {}
    }

    public static void resetDefaults(Context context) {
        if (context == null) return;
        try {
            getBundlePrefs(context).edit().clear().apply();
            getLocalPrefs(context).edit().clear().apply();
        } catch (Throwable ignored) {}
    }

    public static void restartTelegram(Context context) {
        if (context == null) return;
        try {
            String packageName = context.getPackageName();
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                Intent mainIntent = Intent.makeRestartActivityTask(Objects.requireNonNull(intent.getComponent()));
                mainIntent.setPackage(packageName);
                context.startActivity(mainIntent);
                Runtime.getRuntime().exit(0);
            }
        } catch (Throwable ignored) {
            try {
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Throwable ignored2) {}
        }
    }

    // Dynamic Theme Color Resolution using Telegram Theme keys
    public static int getThemeColor(String keyName, int fallback) {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            Field field = themeClass.getField(keyName);
            int key = field.getInt(null);
            Method getColor = themeClass.getMethod("getColor", int.class);
            Object result = getColor.invoke(null, key);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    public static boolean isDarkMode(Context context) {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");
            Method isDark = themeClass.getMethod("isCurrentThemeDark");
            Object result = isDark.invoke(null);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {}
        if (context != null) {
            int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    public static boolean isAmoledDark(Context context) {
        if (!isDarkMode(context)) return false;
        int bg = getThemeColor("key_windowBackgroundWhite", Color.WHITE);
        return (bg & 0x00FFFFFF) == 0;
    }
}
