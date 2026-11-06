package com.example.examprep.util.extractor;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class XlsxExtractor {

    private static final Pattern SHEET_PATTERN = Pattern.compile("^xl/worksheets/sheet(\\d+)\\.xml$");
    private static final int MAX_ROWS_PER_SHEET = 2000;

    public static TextExtractorResult extract(File xlsxFile) {
        if (xlsxFile == null || !xlsxFile.exists()) {
            return TextExtractorResult.failure("XLSX file does not exist");
        }

        try (ZipFile zip = new ZipFile(xlsxFile)) {
            // 1. Extract shared strings table
            List<String> sharedStrings = new ArrayList<>();
            ZipEntry sharedStringsEntry = zip.getEntry("xl/sharedStrings.xml");
            if (sharedStringsEntry != null) {
                try (InputStream is = zip.getInputStream(sharedStringsEntry)) {
                    sharedStrings = parseSharedStrings(is);
                }
            }

            // 2. Extract sheets in order
            Map<Integer, String> sheetsData = new HashMap<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Matcher matcher = SHEET_PATTERN.matcher(entry.getName());
                if (matcher.matches()) {
                    int sheetNum = Integer.parseInt(matcher.group(1));
                    try (InputStream is = zip.getInputStream(entry)) {
                        String sheetContent = parseSheet(is, sharedStrings);
                        sheetsData.put(sheetNum, sheetContent);
                    }
                }
            }

            if (sheetsData.isEmpty()) {
                return TextExtractorResult.failure("No worksheets found in XLSX file");
            }

            List<Integer> sortedSheets = new ArrayList<>(sheetsData.keySet());
            Collections.sort(sortedSheets);

            StringBuilder fullText = new StringBuilder();
            for (Integer sheetNum : sortedSheets) {
                fullText.append("=== Sheet ").append(sheetNum).append(" ===\n");
                String content = sheetsData.get(sheetNum);
                if (content != null && !content.isEmpty()) {
                    fullText.append(content).append("\n");
                }
                fullText.append("\n");
            }

            String result = fullText.toString().trim();
            if (result.isEmpty()) {
                return TextExtractorResult.failure("XLSX contains no extractable data");
            }

            return TextExtractorResult.success(result, TextExtractorResult.Method.NATIVE_TEXT, sortedSheets.size());
        } catch (Exception e) {
            return TextExtractorResult.failure("Failed to parse XLSX: " + e.getMessage());
        }
    }

    private static List<String> parseSharedStrings(InputStream is) {
        List<String> strings = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(is, "UTF-8");

            int eventType = parser.getEventType();
            StringBuilder currentString = new StringBuilder();
            boolean inText = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("t".equals(parser.getName())) {
                        inText = true;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inText) {
                        currentString.append(parser.getText());
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("t".equals(parser.getName())) {
                        inText = false;
                    } else if ("si".equals(parser.getName())) {
                        strings.add(currentString.toString().trim());
                        currentString.setLength(0);
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception ignored) {
        }
        return strings;
    }

    private static String parseSheet(InputStream is, List<String> sharedStrings) {
        StringBuilder sheetText = new StringBuilder();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(is, "UTF-8");

            int eventType = parser.getEventType();
            int rowCount = 0;
            StringBuilder currentRow = new StringBuilder();
            String cellType = null;
            boolean inValue = false;
            StringBuilder cellValue = new StringBuilder();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("row".equals(name)) {
                        currentRow.setLength(0);
                    } else if ("c".equals(name)) {
                        cellType = parser.getAttributeValue(null, "t");
                    } else if ("v".equals(name)) {
                        inValue = true;
                        cellValue.setLength(0);
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inValue) {
                        cellValue.append(parser.getText());
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("v".equals(name)) {
                        inValue = false;
                        String val = cellValue.toString().trim();
                        if ("s".equals(cellType)) {
                            try {
                                int idx = Integer.parseInt(val);
                                if (idx >= 0 && idx < sharedStrings.size()) {
                                    val = sharedStrings.get(idx);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        if (currentRow.length() > 0) {
                            currentRow.append("\t");
                        }
                        currentRow.append(val);
                    } else if ("row".equals(name)) {
                        rowCount++;
                        if (rowCount <= MAX_ROWS_PER_SHEET) {
                            sheetText.append(currentRow).append("\n");
                        }
                    }
                }
                eventType = parser.next();
            }

            if (rowCount > MAX_ROWS_PER_SHEET) {
                sheetText.append("... [Sheet truncated after ").append(MAX_ROWS_PER_SHEET).append(" rows]\n");
            }
        } catch (Exception ignored) {
        }
        return sheetText.toString().trim();
    }
}
