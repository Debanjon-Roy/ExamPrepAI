package com.example.examprep.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.examprep.data.model.GenerationCache;

@Dao
public interface GenerationCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(GenerationCache cache);

    @Query("SELECT * FROM generation_cache WHERE projectId = :projectId AND generationType = :type AND inputHash = :hash LIMIT 1")
    GenerationCache getCachedResult(long projectId, String type, String hash);

    @Query("SELECT * FROM generation_cache WHERE projectId = :projectId AND generationType = :type ORDER BY createdAt DESC LIMIT 1")
    GenerationCache getLatestCachedResult(long projectId, String type);

    @Query("DELETE FROM generation_cache WHERE projectId = :projectId AND generationType = :type")
    void deleteCacheByType(long projectId, String type);

    @Query("DELETE FROM generation_cache WHERE projectId = :projectId")
    void deleteCacheForProject(long projectId);
}
