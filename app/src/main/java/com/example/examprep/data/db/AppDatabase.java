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

    private static AppDatabase INSTANCE;
    private static String instanceUid;

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
        Context app = context.getApplicationContext();
        String uid = UserPaths.currentUid();

        if (INSTANCE == null || !uid.equals(instanceUid)) {
            boolean adoptedLegacy = LegacyMigration.claim(app, uid);

            INSTANCE = Room.databaseBuilder(app, AppDatabase.class, dbNameFor(uid))
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build();
            instanceUid = uid;

            if (adoptedLegacy) {
                LegacyMigration.fixFilePaths(app, INSTANCE);
            }
        }
        return INSTANCE;
    }
}
