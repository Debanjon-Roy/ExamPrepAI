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

    @Query("SELECT * FROM generation_cache WHERE projectId = :projectId AND generationType = 'exam_questions' ORDER BY createdAt DESC, id DESC")
    java.util.List<GenerationCache> getQuestionSets(long projectId);

    @Query("SELECT * FROM generation_cache WHERE projectId = :projectId AND id = :id AND generationType = 'exam_questions' LIMIT 1")
    GenerationCache getQuestionSet(long projectId, long id);

    @Query("DELETE FROM generation_cache WHERE projectId = :projectId AND id = :id AND generationType = 'exam_questions'")
    int deleteQuestionSet(long projectId, long id);

    @Query("DELETE FROM generation_cache WHERE projectId = :projectId AND generationType = :type")
    void deleteCacheByType(long projectId, String type);

    @Query("DELETE FROM generation_cache WHERE projectId = :projectId")
    void deleteCacheForProject(long projectId);

    @Query("SELECT COUNT(*) FROM generation_cache")
    int getTotalWorksGenerated();

    @Query("SELECT COUNT(*) FROM generation_cache")
    androidx.lifecycle.LiveData<Integer> getTotalWorksGeneratedLive();
}
