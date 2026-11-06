package com.example.examprep.util.ai;

import androidx.annotation.NonNull;

public class AiResponse {
    @NonNull
    private final String content;
    @NonNull
    private final TokenUsage tokenUsage;
    private final boolean servedFromLocalCache;

    public AiResponse(@NonNull String content, @NonNull TokenUsage tokenUsage, boolean servedFromLocalCache) {
        this.content = content;
        this.tokenUsage = tokenUsage;
        this.servedFromLocalCache = servedFromLocalCache;
    }

    @NonNull
    public String getContent() {
        return content;
    }

    @NonNull
    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public boolean isServedFromLocalCache() {
        return servedFromLocalCache;
    }
}
