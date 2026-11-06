package com.example.examprep;

import android.app.Application;
import com.example.examprep.data.db.AppDatabase;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public class ExamPrepApp extends Application {
    
    private static ExamPrepApp instance;
    private AppDatabase database;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Apply saved theme preference
        android.content.SharedPreferences themePrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDark = themePrefs.getBoolean("pref_dark_theme", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isDark ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                       : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );

        PDFBoxResourceLoader.init(getApplicationContext());
        database = AppDatabase.getInstance(this);
    }
    
    public static ExamPrepApp getInstance() {
        return instance;
    }
    
    public AppDatabase getDatabase() {
        return database;
    }
}
