package com.example.melodix;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_DARK_MODE = "isDarkMode";

    public static final int LIGHT_MODE = 0;
    public static final int DARK_MODE = 1;

    public static void setTheme(Context context, int themeMode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_DARK_MODE, themeMode);
        editor.apply();

        AppCompatDelegate.setDefaultNightMode(themeMode == DARK_MODE
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static int getThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_DARK_MODE, DARK_MODE); // Default to dark mode
    }

    public static boolean isDarkMode(Context context) {
        return getThemeMode(context) == DARK_MODE;
    }

    public static void toggleTheme(Context context) {
        int currentTheme = getThemeMode(context);
        setTheme(context, currentTheme == DARK_MODE ? LIGHT_MODE : DARK_MODE);
    }
}
