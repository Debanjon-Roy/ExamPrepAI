package com.example.examprep.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "projects")
public class Project {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String name;

    @Nullable
    private String description;

    @NonNull
    private Date createdAt;

    @NonNull
    private Date updatedAt;

    @NonNull
    private String language;

    @NonNull
    private String detailLevel;

    /**
     * Primary constructor with name only. Sets all defaults.
     */
    public Project(@NonNull String name) {
        this.name = name;
        this.description = null;
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.language = "en";
        this.detailLevel = "standard";
    }

    /**
     * Full constructor for all fields.
     */
    @Ignore
    public Project(long id, @NonNull String name, @Nullable String description,
                   @NonNull Date createdAt, @NonNull Date updatedAt,
                   @NonNull String language, @NonNull String detailLevel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.language = language;
        this.detailLevel = detailLevel;
    }

    // Getters

    public long getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @NonNull
    public Date getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public Date getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public String getLanguage() {
        return language;
    }

    @NonNull
    public String getDetailLevel() {
        return detailLevel;
    }

    // Setters

    public void setId(long id) {
        this.id = id;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public void setCreatedAt(@NonNull Date createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(@NonNull Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setLanguage(@NonNull String language) {
        this.language = language;
    }

    public void setDetailLevel(@NonNull String detailLevel) {
        this.detailLevel = detailLevel;
    }
}
