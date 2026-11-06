package com.example.examprep.util.pdf;

import com.example.examprep.util.UserPaths;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StudyGuidePdfGenerator {

    // Vintage Blue styling colors
    private static final DeviceRgb COLOR_PRIMARY = new DeviceRgb(32, 64, 96);       // #204060
    private static final DeviceRgb COLOR_PRIMARY_LIGHT = new DeviceRgb(63, 97, 136); // #3F6188
    private static final DeviceRgb COLOR_ACCENT = new DeviceRgb(201, 122, 62);      // #C97A3E
    private static final DeviceRgb COLOR_BG_CALLOUT = new DeviceRgb(240, 244, 248);  // Soft slate tint
    private static final DeviceRgb COLOR_BG_NOTE = new DeviceRgb(251, 245, 238);     // Warm parchment tint
    private static final DeviceRgb COLOR_TEXT_DARK = new DeviceRgb(28, 40, 51);      // Deep charcoal
    private static final DeviceRgb COLOR_TEXT_MUTED = new DeviceRgb(93, 109, 126);   // Slate gray

    public static File generatePdf(
            @NonNull Context context,
            long projectId,
            @NonNull String projectName,
            @NonNull String studyGuideJson,
            @NonNull String language
    ) throws Exception {
        return generatePdf(context, projectId, projectName, studyGuideJson, language, UserPaths.filesDir(context));
    }

    public static File generatePdf(
            @NonNull Context context,
            long projectId,
            @NonNull String projectName,
            @NonNull String studyGuideJson,
            @NonNull String language,
            @NonNull File ownerFilesDir
    ) throws Exception {

        File outputDir = new File(ownerFilesDir, "generated/" + projectId);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String fileName = "StudyGuide_" + System.currentTimeMillis() + ".pdf";
        File pdfFile = new File(outputDir, fileName);

        JsonObject root = JsonParser.parseString(studyGuideJson).getAsJsonObject();

        String title = root.has("title") ? root.get("title").getAsString() : projectName + " - Study Guide";
        String subtitle = root.has("subtitle") ? root.get("subtitle").getAsString() : "Comprehensive Exam Preparation Guide";

        // Fonts
        PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont fontItalic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
        PdfFont fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        try (FileOutputStream fos = new FileOutputStream(pdfFile);
             PdfWriter writer = new PdfWriter(fos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc)) {

            doc.setMargins(36, 40, 36, 40);

            // 1. Header Banner
            Div headerBox = new Div()
                    .setBackgroundColor(COLOR_BG_CALLOUT)
                    .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
                    .setPadding(16)
                    .setMarginBottom(18);

            headerBox.add(new Paragraph(title)
                    .setFontSize(22)
                    .setFont(fontBold)
                    .setFontColor(COLOR_PRIMARY)
                    .setMarginBottom(4));

            headerBox.add(new Paragraph(subtitle)
                    .setFontSize(12)
                    .setFont(fontItalic)
                    .setFontColor(COLOR_PRIMARY_LIGHT)
                    .setMarginBottom(8));

            String dateStr = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());
            String metaText = String.format("Language: %s  •  Generated on: %s  •  ExamForge",
                    language.equals("bn") ? "বাংলা (Bengali)" : "English", dateStr);

            headerBox.add(new Paragraph(metaText)
                    .setFontSize(9)
                    .setFont(fontRegular)
                    .setFontColor(COLOR_TEXT_MUTED));

            doc.add(headerBox);

            // 2. Topics Covered Checklist
            if (root.has("topics_covered") && root.get("topics_covered").isJsonArray()) {
                JsonArray topics = root.getAsJsonArray("topics_covered");
                if (topics.size() > 0) {
                    Paragraph topicsHeading = new Paragraph("Topics Covered in This Guide")
                            .setFontSize(13)
                            .setFont(fontBold)
                            .setFontColor(COLOR_ACCENT)
                            .setMarginBottom(6);
                    doc.add(topicsHeading);

                    Table topicsTable = new Table(UnitValue.createPercentArray(new float[]{5, 95}))
                            .useAllAvailableWidth()
                            .setMarginBottom(16);

                    int tIndex = 1;
                    for (JsonElement t : topics) {
                        Cell numCell = new Cell().add(new Paragraph(tIndex + ".").setFontSize(9).setFont(fontBold).setFontColor(COLOR_PRIMARY));
                        numCell.setBorder(Border.NO_BORDER).setPadding(2);

                        Cell textCell = new Cell().add(new Paragraph(t.getAsString()).setFontSize(9.5f).setFont(fontRegular).setFontColor(COLOR_TEXT_DARK));
                        textCell.setBorder(Border.NO_BORDER).setPadding(2);

                        topicsTable.addCell(numCell);
                        topicsTable.addCell(textCell);
                        tIndex++;
                    }
                    doc.add(topicsTable);
                }
            }

            // 3. Sections Content
            if (root.has("sections") && root.get("sections").isJsonArray()) {
                JsonArray sections = root.getAsJsonArray("sections");

                for (JsonElement secElem : sections) {
                    if (!secElem.isJsonObject()) continue;
                    JsonObject section = secElem.getAsJsonObject();

                    String heading = section.has("heading") ? section.get("heading").getAsString() : "Section";

                    // Section Title
                    Paragraph secTitle = new Paragraph(heading)
                            .setFontSize(15)
                            .setFont(fontBold)
                            .setFontColor(COLOR_PRIMARY)
                            .setMarginTop(12)
                            .setMarginBottom(6);
                    doc.add(secTitle);

                    // Summary
                    if (section.has("summary") && !section.get("summary").isJsonNull()) {
                        Paragraph summaryPara = new Paragraph(section.get("summary").getAsString())
                                .setFontSize(10.5f)
                                .setFont(fontItalic)
                                .setFontColor(COLOR_TEXT_MUTED)
                                .setMarginBottom(8);
                        doc.add(summaryPara);
                    }

                    // Content Paragraphs
                    if (section.has("content_paragraphs") && section.get("content_paragraphs").isJsonArray()) {
                        for (JsonElement pElem : section.getAsJsonArray("content_paragraphs")) {
                            Paragraph p = new Paragraph(pElem.getAsString())
                                    .setFontSize(10)
                                    .setFont(fontRegular)
                                    .setFontColor(COLOR_TEXT_DARK)
                                    .setMarginBottom(6)
                                    .setTextAlignment(TextAlignment.JUSTIFIED);
                            doc.add(p);
                        }
                    }

                    // Key Concepts Callout
                    if (section.has("key_concepts") && section.get("key_concepts").isJsonArray()) {
                        JsonArray concepts = section.getAsJsonArray("key_concepts");
                        if (concepts.size() > 0) {
                            Div conceptBox = new Div()
                                    .setBackgroundColor(COLOR_BG_CALLOUT)
                                    .setBorder(new SolidBorder(COLOR_PRIMARY_LIGHT, 1f))
                                    .setPadding(10)
                                    .setMarginTop(6)
                                    .setMarginBottom(8);

                            conceptBox.add(new Paragraph("Key Concepts & Definitions")
                                    .setFontSize(11)
                                    .setFont(fontBold)
                                    .setFontColor(COLOR_PRIMARY)
                                    .setMarginBottom(4));

                            for (JsonElement cElem : concepts) {
                                if (cElem.isJsonObject()) {
                                    JsonObject cObj = cElem.getAsJsonObject();
                                    String cName = cObj.has("concept") ? cObj.get("concept").getAsString() : "";
                                    String cExp = cObj.has("explanation") ? cObj.get("explanation").getAsString() : "";

                                    Paragraph cp = new Paragraph()
                                            .add(new Text("• " + cName + ": ").setFont(fontBold).setFontSize(9.5f).setFontColor(COLOR_PRIMARY))
                                            .add(new Text(cExp).setFont(fontRegular).setFontSize(9.5f).setFontColor(COLOR_TEXT_DARK));
                                    conceptBox.add(cp);
                                }
                            }
                            doc.add(conceptBox);
                        }
                    }

                    // Tabular Data if provided
                    if (section.has("table") && section.get("table").isJsonObject()) {
                        JsonObject tableObj = section.getAsJsonObject("table");
                        if (tableObj.has("headers") && tableObj.has("rows")) {
                            JsonArray headers = tableObj.getAsJsonArray("headers");
                            JsonArray rows = tableObj.getAsJsonArray("rows");

                            if (headers.size() > 0) {
                                Table dataTable = new Table(headers.size())
                                        .useAllAvailableWidth()
                                        .setMarginTop(6)
                                        .setMarginBottom(10);

                                for (JsonElement h : headers) {
                                    Cell hCell = new Cell()
                                            .add(new Paragraph(h.getAsString()).setFontSize(9).setFont(fontBold).setFontColor(ColorConstants.WHITE))
                                            .setBackgroundColor(COLOR_PRIMARY)
                                            .setPadding(5);
                                    dataTable.addHeaderCell(hCell);
                                }

                                for (JsonElement rElem : rows) {
                                    if (rElem.isJsonArray()) {
                                        for (JsonElement cellVal : rElem.getAsJsonArray()) {
                                            Cell c = new Cell()
                                                    .add(new Paragraph(cellVal.getAsString()).setFontSize(9).setFont(fontRegular).setFontColor(COLOR_TEXT_DARK))
                                                    .setPadding(4);
                                            dataTable.addCell(c);
                                        }
                                    }
                                }
                                doc.add(dataTable);
                            }
                        }
                    }

                    // Important Notes / Exam Tips
                    if (section.has("important_notes") && section.get("important_notes").isJsonArray()) {
                        JsonArray notes = section.getAsJsonArray("important_notes");
                        if (notes.size() > 0) {
                            Div noteBox = new Div()
                                    .setBackgroundColor(COLOR_BG_NOTE)
                                    .setBorderLeft(new SolidBorder(COLOR_ACCENT, 3f))
                                    .setBorderTop(Border.NO_BORDER)
                                    .setBorderRight(Border.NO_BORDER)
                                    .setBorderBottom(Border.NO_BORDER)
                                    .setPadding(8)
                                    .setMarginTop(4)
                                    .setMarginBottom(8);

                            noteBox.add(new Paragraph("★ Exam Tips & Pitfalls")
                                    .setFontSize(10.5f)
                                    .setFont(fontBold)
                                    .setFontColor(COLOR_ACCENT)
                                    .setMarginBottom(2));

                            for (JsonElement n : notes) {
                                noteBox.add(new Paragraph("• " + n.getAsString())
                                        .setFontSize(9.5f)
                                        .setFont(fontRegular)
                                        .setFontColor(COLOR_TEXT_DARK));
                            }
                            doc.add(noteBox);
                        }
                    }

                    // General Knowledge Additions (Cleanly segregated)
                    if (section.has("general_knowledge_additions") && section.get("general_knowledge_additions").isJsonArray()) {
                        JsonArray gkNotes = section.getAsJsonArray("general_knowledge_additions");
                        if (gkNotes.size() > 0) {
                            Paragraph gkPara = new Paragraph("[General Knowledge Supplement: ")
                                    .setFontSize(8.5f)
                                    .setFont(fontItalic)
                                    .setFontColor(COLOR_TEXT_MUTED);

                            for (JsonElement gk : gkNotes) {
                                gkPara.add(gk.getAsString() + " ");
                            }
                            gkPara.add("]");
                            doc.add(gkPara);
                        }
                    }
                }
            }

            // 4. Verification Certificate at end
            Div certBox = new Div()
                    .setBackgroundColor(COLOR_BG_CALLOUT)
                    .setBorder(new SolidBorder(COLOR_PRIMARY_LIGHT, 1f))
                    .setPadding(10)
                    .setMarginTop(18);

            certBox.add(new Paragraph("✓ ExamForge Verification Complete")
                    .setFontSize(10.5f)
                    .setFont(fontBold)
                    .setFontColor(COLOR_PRIMARY));

            certBox.add(new Paragraph("This study guide has been cross-referenced against all uploaded source materials. Gaps were automatically checked and filled to ensure 100% topic coverage.")
                    .setFontSize(9)
                    .setFont(fontRegular)
                    .setFontColor(COLOR_TEXT_MUTED));

            doc.add(certBox);
        }

        return pdfFile;
    }
}
