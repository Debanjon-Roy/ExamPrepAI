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

public class PptxExtractor {

    private static final Pattern SLIDE_PATTERN = Pattern.compile("^ppt/slides/slide(\\d+)\\.xml$");
    private static final Pattern NOTE_PATTERN = Pattern.compile("^ppt/notesSlides/notesSlide(\\d+)\\.xml$");

    public static TextExtractorResult extract(File pptxFile) {
        if (pptxFile == null || !pptxFile.exists()) {
            return TextExtractorResult.failure("PPTX file does not exist");
        }

        try (ZipFile zip = new ZipFile(pptxFile)) {
            Map<Integer, String> slidesText = new HashMap<>();
            Map<Integer, String> notesText = new HashMap<>();

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                Matcher slideMatcher = SLIDE_PATTERN.matcher(name);
                if (slideMatcher.matches()) {
                    int slideNum = Integer.parseInt(slideMatcher.group(1));
                    try (InputStream is = zip.getInputStream(entry)) {
                        String text = extractTextFromXml(is);
                        slidesText.put(slideNum, text);
                    }
                    continue;
                }

                Matcher noteMatcher = NOTE_PATTERN.matcher(name);
                if (noteMatcher.matches()) {
                    int noteNum = Integer.parseInt(noteMatcher.group(1));
                    try (InputStream is = zip.getInputStream(entry)) {
                        String text = extractTextFromXml(is);
                        notesText.put(noteNum, text);
                    }
                }
            }

            if (slidesText.isEmpty()) {
                return TextExtractorResult.failure("No slides found in PPTX file");
            }

            List<Integer> sortedIndices = new ArrayList<>(slidesText.keySet());
            Collections.sort(sortedIndices);

            StringBuilder fullText = new StringBuilder();
            int slideCount = sortedIndices.size();

            for (Integer index : sortedIndices) {
                fullText.append("--- Slide ").append(index).append(" ---\n");
                String text = slidesText.get(index);
                if (text != null && !text.isEmpty()) {
                    fullText.append(text).append("\n");
                }
                String note = notesText.get(index);
                if (note != null && !note.isEmpty()) {
                    fullText.append("[Speaker Notes: ").append(note).append("]\n");
                }
                fullText.append("\n");
            }

            String content = fullText.toString().trim();
            if (content.isEmpty()) {
                return TextExtractorResult.failure("PPTX contains no extractable text");
            }

            return TextExtractorResult.success(content, TextExtractorResult.Method.NATIVE_TEXT, slideCount);
        } catch (Exception e) {
            return TextExtractorResult.failure("Failed to parse PPTX: " + e.getMessage());
        }
    }

    private static String extractTextFromXml(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(is, "UTF-8");

            int eventType = parser.getEventType();
            boolean inTextTag = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("a:t".equals(tagName) || "t".equals(tagName)) {
                        inTextTag = true;
                    } else if ("a:p".equals(tagName) || "p".equals(tagName)) {
                        // Paragraph break
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                            sb.append("\n");
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inTextTag) {
                        sb.append(parser.getText()).append(" ");
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tagName = parser.getName();
                    if ("a:t".equals(tagName) || "t".equals(tagName)) {
                        inTextTag = false;
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            // Ignore partial parse errors, return what we have
        }
        return sb.toString().trim();
    }
}
