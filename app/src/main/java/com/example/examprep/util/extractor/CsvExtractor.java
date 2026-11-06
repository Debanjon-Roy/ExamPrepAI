package com.example.examprep.util.extractor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CsvExtractor {

    private static final int MAX_ROWS_TO_EXTRACT = 5000;

    public static TextExtractorResult extract(InputStream inputStream) {
        if (inputStream == null) {
            return TextExtractorResult.failure("Input stream is null");
        }

        StringBuilder sb = new StringBuilder();
        int rowCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rowCount++;
                if (rowCount <= MAX_ROWS_TO_EXTRACT) {
                    sb.append(line).append("\n");
                }
            }

            if (rowCount > MAX_ROWS_TO_EXTRACT) {
                sb.append("\n... [Truncated after ").append(MAX_ROWS_TO_EXTRACT).append(" of ").append(rowCount).append(" rows to prevent memory exhaustion]\n");
            }

            String content = sb.toString().trim();
            if (content.isEmpty()) {
                return TextExtractorResult.failure("CSV file is empty");
            }

            return TextExtractorResult.success(content, TextExtractorResult.Method.NATIVE_TEXT, rowCount);
        } catch (Exception e) {
            return TextExtractorResult.failure("Error reading CSV: " + e.getMessage());
        }
    }
}
