package com.example.examprep.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(
        tableName = "generation_cache",
        foreignKeys = @ForeignKey(
                entity = Project.class,
                parentColumns = "id",
                childColumns = "projectId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = "projectId"),
                @Index(value = {"projectId", "generationType", "inputHash"}, unique = true)
        }
)
public class GenerationCache {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long projectId;

    @NonNull
    private String generationType;

    @NonNull
    private String inputHash;

    @Nullable
    private String resultJson;

    @NonNull
    private Date createdAt;

    private int tokenInputCount;

    private int tokenOutputCount;

    private double costUsd;

    /**
     * Primary constructor with required fields.
     */
    public GenerationCache(long projectId, @NonNull String generationType,
                           @NonNull String inputHash) {
        this.projectId = projectId;
        this.generationType = generationType;
        this.inputHash = inputHash;
        this.resultJson = null;
        this.createdAt = new Date();
        this.tokenInputCount = 0;
        this.tokenOutputCount = 0;
        this.costUsd = 0.0;
    }

    /**
     * Full constructor for all fields.
     */
    @Ignore
    public GenerationCache(long id, long projectId, @NonNull String generationType,
                           @NonNull String inputHash, @Nullable String resultJson,
                           @NonNull Date createdAt, int tokenInputCount,
                           int tokenOutputCount, double costUsd) {
        this.id = id;
        this.projectId = projectId;
        this.generationType = generationType;
        this.inputHash = inputHash;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
        this.tokenInputCount = tokenInputCount;
        this.tokenOutputCount = tokenOutputCount;
        this.costUsd = costUsd;
    }

    // Getters

    public long getId() {
        return id;
    }

    public long getProjectId() {
        return projectId;
    }

    @NonNull
    public String getGenerationType() {
        return generationType;
    }

    @NonNull
    public String getInputHash() {
        return inputHash;
    }

    @Nullable
    public String getResultJson() {
        return resultJson;
    }

    @NonNull
    public Date getCreatedAt() {
        return createdAt;
    }

    public int getTokenInputCount() {
        return tokenInputCount;
    }

    public int getTokenOutputCount() {
        return tokenOutputCount;
    }

    public double getCostUsd() {
        return costUsd;
    }

    // Setters

    public void setId(long id) {
        this.id = id;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public void setGenerationType(@NonNull String generationType) {
        this.generationType = generationType;
    }

    public void setInputHash(@NonNull String inputHash) {
        this.inputHash = inputHash;
    }

    public void setResultJson(@Nullable String resultJson) {
        this.resultJson = resultJson;
    }

    public void setCreatedAt(@NonNull Date createdAt) {
        this.createdAt = createdAt;
    }

    public void setTokenInputCount(int tokenInputCount) {
        this.tokenInputCount = tokenInputCount;
    }

    public void setTokenOutputCount(int tokenOutputCount) {
        this.tokenOutputCount = tokenOutputCount;
    }

    public void setCostUsd(double costUsd) {
        this.costUsd = costUsd;
    }
}
