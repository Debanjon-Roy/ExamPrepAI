package com.example.examprep.util.extractor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TextExtractorResult {

    public enum Method {
        NATIVE_TEXT,
        OCR,
        HYBRID
    }

    private final boolean success;
    @Nullable
    private final String text;
    @NonNull
    private final Method method;
    private final int pageOrItemCount;
    @Nullable
    private final String errorMessage;

    public TextExtractorResult(boolean success, @Nullable String text, @NonNull Method method,
                               int pageOrItemCount, @Nullable String errorMessage) {
        this.success = success;
        this.text = text != null ? text.trim() : null;
        this.method = method;
        this.pageOrItemCount = pageOrItemCount;
        this.errorMessage = errorMessage;
    }

    public static TextExtractorResult success(@NonNull String text, @NonNull Method method, int count) {
        return new TextExtractorResult(true, text, method, count, null);
    }

    public static TextExtractorResult failure(@NonNull String errorMessage) {
        return new TextExtractorResult(false, null, Method.NATIVE_TEXT, 0, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @NonNull
    public Method getMethod() {
        return method;
    }

    public int getPageOrItemCount() {
        return pageOrItemCount;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public int getCharacterCount() {
        return text != null ? text.length() : 0;
    }

    public int getWordCount() {
        if (text == null || text.isEmpty()) return 0;
        String[] words = text.split("\\s+");
        return words.length;
    }
}
