package com.example.examprep.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cost_records",
        indices = {@Index(value = "date", unique = true)}
)
public class CostRecord {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String date;

    private long inputTokens;

    private long outputTokens;

    private long cacheWriteTokens;

    private long cacheReadTokens;

    private double costUsd;

    private int requestCount;

    /**
     * Primary constructor with date only. Sets defaults for token counts.
     */
    public CostRecord(@NonNull String date) {
        this.date = date;
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.cacheWriteTokens = 0;
        this.cacheReadTokens = 0;
        this.costUsd = 0.0;
        this.requestCount = 0;
    }

    /**
     * Full constructor for all fields.
     */
    @Ignore
    public CostRecord(long id, @NonNull String date, long inputTokens, long outputTokens,
                      long cacheWriteTokens, long cacheReadTokens, double costUsd,
                      int requestCount) {
        this.id = id;
        this.date = date;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.costUsd = costUsd;
        this.requestCount = requestCount;
    }

    // Getters

    public long getId() {
        return id;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getCacheWriteTokens() {
        return cacheWriteTokens;
    }

    public long getCacheReadTokens() {
        return cacheReadTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public int getRequestCount() {
        return requestCount;
    }

    // Setters

    public void setId(long id) {
        this.id = id;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    public void setInputTokens(long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public void setOutputTokens(long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public void setCacheWriteTokens(long cacheWriteTokens) {
        this.cacheWriteTokens = cacheWriteTokens;
    }

    public void setCacheReadTokens(long cacheReadTokens) {
        this.cacheReadTokens = cacheReadTokens;
    }

    public void setCostUsd(double costUsd) {
        this.costUsd = costUsd;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }
}
