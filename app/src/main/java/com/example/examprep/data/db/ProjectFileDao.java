package com.example.examprep.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.examprep.data.model.ProjectFile;

import java.util.List;

@Dao
public interface ProjectFileDao {

    @Insert
    long insert(ProjectFile projectFile);

    @Insert
    List<Long> insertAll(List<ProjectFile> projectFiles);

    @Update
    void update(ProjectFile projectFile);

    @Delete
    void delete(ProjectFile projectFile);

    @Query("DELETE FROM project_files WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM project_files WHERE id = :id LIMIT 1")
    ProjectFile getFileById(long id);

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY addedAt DESC")
    LiveData<List<ProjectFile>> getFilesForProject(long projectId);

    @Query("SELECT * FROM project_files WHERE projectId = :projectId")
    List<ProjectFile> getFilesForProjectSync(long projectId);

    @Query("SELECT * FROM project_files WHERE projectId = :projectId AND textExtractionStatus = 'pending'")
    List<ProjectFile> getPendingFiles(long projectId);

    @Query("UPDATE project_files SET extractedText = :text, textExtractionStatus = :status WHERE id = :id")
    void updateExtractedText(long id, String text, String status);

    @Query("UPDATE project_files SET ocrText = :text, textExtractionStatus = :status WHERE id = :id")
    void updateOcrText(long id, String text, String status);
}
