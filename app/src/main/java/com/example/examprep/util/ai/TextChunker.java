package com.example.examprep.util.ai;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    // 1 token approx 4 characters for English / code, ~2.5 for Bengali/Unicode
    // 70,000 tokens ~ 280,000 chars. Setting 250,000 ensures we never exceed 100K input tokens.
    private static final int MAX_CHUNK_CHARS = 250_000;

    public static List<String> chunkText(@NonNull String fullText) {
        List<String> chunks = new ArrayList<>();
        if (fullText.isEmpty()) {
            return chunks;
        }

        if (fullText.length() <= MAX_CHUNK_CHARS) {
            chunks.add(fullText);
            return chunks;
        }

        // Need to chunk large text gracefully
        String[] splitPoints = new String[]{
                "\n--- Page ",
                "\n--- Slide ",
                "\n=== Sheet ",
                "\n\n",
                "\n"
        };

        int currentStart = 0;
        int totalLength = fullText.length();

        while (currentStart < totalLength) {
            int targetEnd = Math.min(currentStart + MAX_CHUNK_CHARS, totalLength);

            if (targetEnd >= totalLength) {
                chunks.add(fullText.substring(currentStart).trim());
                break;
            }

            // Find best splitting boundary before targetEnd
            int bestSplit = -1;
            int lookbackLimit = Math.max(currentStart, targetEnd - 15_000);

            for (String delimiter : splitPoints) {
                int pos = fullText.lastIndexOf(delimiter, targetEnd);
                if (pos > lookbackLimit) {
                    bestSplit = pos;
                    break;
                }
            }

            if (bestSplit <= currentStart) {
                bestSplit = targetEnd; // Fall back to hard boundary if no natural break
            }

            String chunk = fullText.substring(currentStart, bestSplit).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            currentStart = bestSplit;
        }

        return chunks;
    }

    public static long estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (long) Math.ceil(text.length() / 3.8);
    }
}
