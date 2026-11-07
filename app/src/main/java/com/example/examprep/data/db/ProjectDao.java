package com.example.examprep.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.examprep.data.model.Project;

import java.util.Date;
import java.util.List;

@Dao
public interface ProjectDao {

    @Insert
    long insert(Project project);

    @Update
    void update(Project project);

    @Delete
    void delete(Project project);

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    LiveData<List<Project>> getAllProjects();

    @Query("SELECT * FROM projects WHERE id = :id")
    LiveData<Project> getProjectById(long id);

    @Query("SELECT * FROM projects WHERE id = :id")
    LiveData<Project> getProjectLive(long id);

    @Query("SELECT * FROM projects WHERE id = :id")
    Project getProjectByIdSync(long id);

    @Query("SELECT * FROM projects")
    List<Project> getAllProjectsSync();

    @Query("SELECT COUNT(*) FROM projects")
    int getProjectCount();

    @Query("UPDATE projects SET updatedAt = :date WHERE id = :id")
    void updateTimestamp(long id, Date date);

    @Query("UPDATE projects SET name = :name, updatedAt = :date WHERE id = :id")
    int rename(long id, String name, Date date);
}
