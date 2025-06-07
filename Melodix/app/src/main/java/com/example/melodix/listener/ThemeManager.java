package com.example.melodix.listener;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.melodix.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_THEME_MODE = "themeMode";
    public static final int LIGHT_MODE = 0;
    public static final int DARK_MODE = 1;
    @IntDef({LIGHT_MODE, DARK_MODE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ThemeMode {}
    public static void setTheme(Context context, @ThemeMode int themeMode) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot set theme");
            return;
        }
        if (themeMode != LIGHT_MODE && themeMode != DARK_MODE) {
            Log.w(TAG, "Invalid theme mode: " + themeMode + ", defaulting to LIGHT_MODE");
            themeMode = LIGHT_MODE;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int oldTheme = prefs.getInt(KEY_THEME_MODE, LIGHT_MODE);
            if (oldTheme == themeMode) {
                Log.d(TAG, "Theme already set to: " + getThemeName(themeMode));
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_THEME_MODE, themeMode);
            editor.apply();
            applyTheme(themeMode);

            Log.d(TAG, "Theme changed from " + getThemeName(oldTheme) + " to " + getThemeName(themeMode));

        } catch (Exception e) {
            Log.e(TAG, "Error setting theme", e);
        }
    }
    @ThemeMode
    public static int getThemeMode(Context context) {
        if (context == null) {
            Log.w(TAG, "Context is null, returning LIGHT_MODE");
            return LIGHT_MODE;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int savedTheme = prefs.getInt(KEY_THEME_MODE, -1);

            if (savedTheme == -1) {
                Log.d(TAG, "First launch detected, detecting system theme preference");
                int nightModeFlags = context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                boolean systemIsDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

                int initialTheme = systemIsDark ? DARK_MODE : LIGHT_MODE;
                Log.d(TAG, "System is currently " + (systemIsDark ? "dark" : "light") +
                        ", setting initial theme to " + getThemeName(initialTheme));

                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_THEME_MODE, initialTheme);
                editor.apply();

                return initialTheme;
            }
            if (savedTheme == 2) {
                Log.d(TAG, "Migrating from old SYSTEM_DEFAULT_MODE");

                int nightModeFlags = context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                boolean systemIsDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

                int migratedTheme = systemIsDark ? DARK_MODE : LIGHT_MODE;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_THEME_MODE, migratedTheme);
                editor.apply();

                return migratedTheme;
            }
            if (savedTheme == LIGHT_MODE || savedTheme == DARK_MODE) {
                return savedTheme;
            } else {
                Log.w(TAG, "Invalid saved theme: " + savedTheme + ", defaulting to LIGHT_MODE");
                return LIGHT_MODE;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting theme mode", e);
            return LIGHT_MODE;
        }
    }
    public static boolean isDarkMode(Context context) {
        return getThemeMode(context) == DARK_MODE;
    }

    public static String toggleTheme(Context context) {
        int currentTheme = getThemeMode(context);
        int nextTheme = (currentTheme == LIGHT_MODE) ? DARK_MODE : LIGHT_MODE;
        setTheme(context, nextTheme);
        String message = getThemeName(nextTheme) + " mode activated";
        Log.d(TAG, "Theme toggled: " + getThemeName(currentTheme) + " → " + getThemeName(nextTheme));
        return message;
    }
    private static void applyTheme(@ThemeMode int themeMode) {
        try {
            switch (themeMode) {
                case LIGHT_MODE:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case DARK_MODE:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                default:
                    Log.w(TAG, "Unknown theme mode: " + themeMode + ", using light mode");
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
            }
            Log.d(TAG, "✅ Theme applied successfully: " + getThemeName(themeMode));
        } catch (Exception e) {
            Log.e(TAG, "❌ Error applying theme: " + e.getMessage(), e);
            try {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                Log.d(TAG, "Applied fallback theme (light mode)");
            } catch (Exception fallbackError) {
                Log.e(TAG, "❌ Even fallback theme failed: " + fallbackError.getMessage());
            }
        }
    }
    public static String getThemeName(@ThemeMode int themeMode) {
        switch (themeMode) {
            case LIGHT_MODE:
                return "Light";
            case DARK_MODE:
                return "Dark";
            default:
                return "Unknown";
        }
    }
    public static void initializeTheme(Context context) {
        int themeMode = getThemeMode(context);
        applyTheme(themeMode);
        Log.d(TAG, "Theme initialized: " + getThemeName(themeMode));
    }
    public static String toggleThemeWithEmoji(Context context) {
        int currentTheme = getThemeMode(context);
        int nextTheme = (currentTheme == LIGHT_MODE) ? DARK_MODE : LIGHT_MODE;

        setTheme(context, nextTheme);

        String message = (nextTheme == DARK_MODE) ? "🌙 Dark mode" : "☀️ Light mode";
        Log.d(TAG, "Theme toggled with emoji: " + message);

        return message;
    }
}