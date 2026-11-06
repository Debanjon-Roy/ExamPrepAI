package com.example.examprep.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.examprep.data.model.CostRecord;
import com.example.examprep.data.model.GenerationCache;
import com.example.examprep.data.model.Project;
import com.example.examprep.data.model.ProjectFile;
import com.example.examprep.util.UserPaths;

@Database(
        entities = {Project.class, ProjectFile.class, GenerationCache.class, CostRecord.class},
        version = 1,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final java.util.Map<String, AppDatabase> INSTANCES = new java.util.HashMap<>();

    public abstract ProjectDao projectDao();

    public abstract ProjectFileDao projectFileDao();

    public abstract GenerationCacheDao generationCacheDao();

    public abstract CostRecordDao costRecordDao();

    static String dbNameFor(String uid) {
        return "examprep_db_" + uid;
    }

    /**
     * Returns the database that belongs to the CURRENTLY signed-in Google account.
     * If the account changed since the last call, a different database file is opened.
     */
    public static synchronized AppDatabase getInstance(Context context) {
        return getInstance(context, UserPaths.currentUid());
    }

    public static synchronized AppDatabase getInstance(Context context, String uid) {
        Context app = context.getApplicationContext();

        AppDatabase database = INSTANCES.get(uid);
        if (database == null) {
            // Legacy data has no reliable owner; never assign it on login.

            database = Room.databaseBuilder(app, AppDatabase.class, dbNameFor(uid))
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();
            INSTANCES.put(uid, database);

        }
        return database;
    }
}
