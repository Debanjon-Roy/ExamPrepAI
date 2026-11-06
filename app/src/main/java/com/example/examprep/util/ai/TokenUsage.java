package com.example.examprep.util.ai;

public class TokenUsage {
    private final long inputTokens;
    private final long outputTokens;
    private final long cacheCreationInputTokens;
    private final long cacheReadInputTokens;
    private final double costUsd;

    // OpenAI GPT-4o mini pricing per million tokens:
    // Input: $0.15 per 1M tokens
    // Output: $0.60 per 1M tokens
    // Cached prompt input: $0.075 per 1M tokens
    public TokenUsage(long inputTokens, long outputTokens,
                      long cacheCreationInputTokens, long cacheReadInputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        this.cacheReadInputTokens = cacheReadInputTokens;

        double rateInput = 0.15;
        double rateOutput = 0.60;
        double rateCachedInput = 0.075;

        long uncachedInput = Math.max(0, inputTokens - cacheReadInputTokens);
        double cost = (uncachedInput * rateInput / 1_000_000.0)
                + (outputTokens * rateOutput / 1_000_000.0)
                + (cacheReadInputTokens * rateCachedInput / 1_000_000.0);

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
                "Tokens: %d in / %d out (Cached: %d) • Cost: $%.5f",
                inputTokens, outputTokens, cacheReadInputTokens, costUsd
        );
    }
}
