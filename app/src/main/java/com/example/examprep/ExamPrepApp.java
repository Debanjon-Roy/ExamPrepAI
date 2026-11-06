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
