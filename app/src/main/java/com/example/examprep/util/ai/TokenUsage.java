package com.example.examprep.util.ai;

public class TokenUsage {
    private final long inputTokens;
    private final long outputTokens;
    private final long cacheCreationInputTokens;
    private final long cacheReadInputTokens;
    private final double costUsd;

    // Claude Haiku 5.5 pricing per million tokens
    // Tier <= 100K: Input $0.10, Output $0.50, Cache Write $0.125, Cache Read $0.01
    // Tier > 100K: Input $0.50, Output $2.50, Cache Write $0.625, Cache Read $0.05
    public TokenUsage(long inputTokens, long outputTokens,
                      long cacheCreationInputTokens, long cacheReadInputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        this.cacheReadInputTokens = cacheReadInputTokens;

        long totalInput = inputTokens + cacheCreationInputTokens + cacheReadInputTokens;
        boolean isHighTier = totalInput > 100000;

        double rateInput = isHighTier ? 0.50 : 0.10;
        double rateOutput = isHighTier ? 2.50 : 0.50;
        double rateCacheWrite = isHighTier ? 0.625 : 0.125;
        double rateCacheRead = isHighTier ? 0.05 : 0.01;

        double cost = (inputTokens * rateInput / 1_000_000.0)
                + (outputTokens * rateOutput / 1_000_000.0)
                + (cacheCreationInputTokens * rateCacheWrite / 1_000_000.0)
                + (cacheReadInputTokens * rateCacheRead / 1_000_000.0);

        this.costUsd = cost;
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0);
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getCacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    public long getCacheReadInputTokens() {
        return cacheReadInputTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public String getFormattedSummary() {
        return String.format(
                "Tokens: %d in / %d out (Cache: %d read, %d write) • Cost: $%.5f",
                inputTokens, outputTokens, cacheReadInputTokens, cacheCreationInputTokens, costUsd
        );
    }
}
