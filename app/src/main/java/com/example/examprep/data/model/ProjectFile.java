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
        tableName = "project_files",
        foreignKeys = @ForeignKey(
                entity = Project.class,
                parentColumns = "id",
                childColumns = "projectId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index(value = "projectId")}
)
public class ProjectFile {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long projectId;

    @NonNull
    private String fileName;

    @NonNull
    private String fileUri;

    @NonNull
    private String mimeType;

    private long fileSize;

    @Nullable
    private String extractedText;

    @Nullable
    private String ocrText;

    @NonNull
    private String textExtractionStatus;

    @NonNull
    private Date addedAt;

    /**
     * Primary constructor with required fields.
     */
    public ProjectFile(long projectId, @NonNull String fileName, @NonNull String fileUri,
                       @NonNull String mimeType, long fileSize) {
        this.projectId = projectId;
        this.fileName = fileName;
        this.fileUri = fileUri;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.extractedText = null;
        this.ocrText = null;
        this.textExtractionStatus = "pending";
        this.addedAt = new Date();
    }

    /**
     * Full constructor for all fields.
     */
    @Ignore
    public ProjectFile(long id, long projectId, @NonNull String fileName, @NonNull String fileUri,
                       @NonNull String mimeType, long fileSize, @Nullable String extractedText,
                       @Nullable String ocrText, @NonNull String textExtractionStatus,
                       @NonNull Date addedAt) {
        this.id = id;
        this.projectId = projectId;
        this.fileName = fileName;
        this.fileUri = fileUri;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.extractedText = extractedText;
        this.ocrText = ocrText;
        this.textExtractionStatus = textExtractionStatus;
        this.addedAt = addedAt;
    }

    // Getters

    public long getId() {
        return id;
    }

    public long getProjectId() {
        return projectId;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getFileUri() {
        return fileUri;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    @Nullable
    public String getExtractedText() {
        return extractedText;
    }

    @Nullable
    public String getOcrText() {
        return ocrText;
    }

    @NonNull
    public String getTextExtractionStatus() {
        return textExtractionStatus;
    }

    @NonNull
    public Date getAddedAt() {
        return addedAt;
    }

    // Setters

    public void setId(long id) {
        this.id = id;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public void setFileName(@NonNull String fileName) {
        this.fileName = fileName;
    }

    public void setFileUri(@NonNull String fileUri) {
        this.fileUri = fileUri;
    }

    public void setMimeType(@NonNull String mimeType) {
        this.mimeType = mimeType;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setExtractedText(@Nullable String extractedText) {
        this.extractedText = extractedText;
    }

    public void setOcrText(@Nullable String ocrText) {
        this.ocrText = ocrText;
    }

    public void setTextExtractionStatus(@NonNull String textExtractionStatus) {
        this.textExtractionStatus = textExtractionStatus;
    }

    public void setAddedAt(@NonNull Date addedAt) {
        this.addedAt = addedAt;
    }
}
