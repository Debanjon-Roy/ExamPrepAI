package com.example.examprep.util.pptx;

import android.content.Context;
import androidx.annotation.NonNull;

import com.example.examprep.data.model.slide.PresentationDeck;
import com.example.examprep.data.model.slide.SlideOutline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PptxGenerator {

    // 16:9 Widescreen dimensions in EMUs (914400 EMUs = 1 inch)
    // 13.333 inches x 7.5 inches
    public static final long SLIDE_WIDTH = 12192000L;
    public static final long SLIDE_HEIGHT = 6858000L;

    // Vintage Blue Theme Hex Colors (without '#')
    private static final String COLOR_NAVY_DARK = "1A2C3D";
    private static final String COLOR_PRIMARY_BLUE = "204060";
    private static final String COLOR_AMBER = "C97A3E";
    private static final String COLOR_AMBER_LIGHT = "F5E8DD";
    private static final String COLOR_BG_LIGHT = "F5F7FA";
    private static final String COLOR_CARD_BG = "EBF2F7";
    private static final String COLOR_WHITE = "FFFFFF";
    private static final String COLOR_TEXT_DARK = "1D2A3A";
    private static final String COLOR_TEXT_MUTED = "5A6C7C";
    private static final String COLOR_BORDER = "204060";

    public static File generatePresentation(
            @NonNull Context context,
            long projectId,
            @NonNull String projectName,
            @NonNull PresentationDeck deck
    ) throws IOException {
        File outputDir = new File(context.getFilesDir(), "generated/" + projectId);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File pptxFile = new File(outputDir, "Presentation_" + timestamp + ".pptx");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(pptxFile))) {
            List<SlideOutline> slides = deck.getSlides();
            if (slides.isEmpty()) {
                SlideOutline fallback = new SlideOutline();
                fallback.setSlideNumber(1);
                fallback.setLayout("title");
                fallback.setHeader(projectName);
                fallback.setSubheader("ExamPrep AI Study Deck");
                slides.add(fallback);
            }

            int count = slides.size();

            // 1. [Content_Types].xml
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(count));

            // 2. _rels/.rels
            writeZipEntry(zos, "_rels/.rels", buildRootRelsXml());

            // 3. docProps/core.xml & app.xml
            String isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
            writeZipEntry(zos, "docProps/core.xml", buildCorePropsXml(deck.getPresentationTitle(), isoDate));
            writeZipEntry(zos, "docProps/app.xml", buildAppPropsXml(count));

            // 4. ppt/presentation.xml & ppt/_rels/presentation.xml.rels
            writeZipEntry(zos, "ppt/presentation.xml", buildPresentationXml(count));
            writeZipEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRelsXml(count));

            // 5. Theme
            writeZipEntry(zos, "ppt/theme/theme1.xml", buildThemeXml());

            // 6. Slide Master & Layouts
            writeZipEntry(zos, "ppt/slideMasters/slideMaster1.xml", buildSlideMasterXml());
            writeZipEntry(zos, "ppt/slideMasters/_rels/slideMaster1.xml.rels", buildSlideMasterRelsXml());
            writeZipEntry(zos, "ppt/slideLayouts/slideLayout1.xml", buildSlideLayoutXml());
            writeZipEntry(zos, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", buildSlideLayoutRelsXml());

            // 7. Individual Slides & Notes
            for (int i = 0; i < count; i++) {
                int slideNum = i + 1;
                SlideOutline slide = slides.get(i);

                writeZipEntry(zos, "ppt/slides/slide" + slideNum + ".xml", buildSlideXml(slide, slideNum, count, projectName));
                writeZipEntry(zos, "ppt/slides/_rels/slide" + slideNum + ".xml.rels", buildSlideRelsXml(slideNum));

                // Speaker notes
                String notes = slide.getSpeakerNotes();
                if (notes == null || notes.trim().isEmpty()) {
                    notes = "Key discussion points for " + slide.getHeader();
                }
                writeZipEntry(zos, "ppt/notesSlides/notesSlide" + slideNum + ".xml", buildNotesSlideXml(notes, slideNum));
                writeZipEntry(zos, "ppt/notesSlides/_rels/notesSlide" + slideNum + ".xml.rels", buildNotesSlideRelsXml(slideNum));
            }
        }

        return pptxFile;
    }

    private static void writeZipEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }

    private static String buildContentTypesXml(int slideCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n");
        sb.append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n");
        sb.append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");
        sb.append("  <Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>\n");
        sb.append("  <Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>\n");
        sb.append("  <Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>\n");
        sb.append("  <Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>\n");
        sb.append("  <Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>\n");
        sb.append("  <Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>\n");

        for (int i = 1; i <= slideCount; i++) {
            sb.append("  <Override PartName=\"/ppt/slides/slide").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n");
            sb.append("  <Override PartName=\"/ppt/notesSlides/notesSlide").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\"/>\n");
        }

        sb.append("</Types>");
        return sb.toString();
    }

    private static String buildRootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>\n" +
                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>\n" +
                "  <Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>\n" +
                "</Relationships>";
    }

    private static String buildCorePropsXml(String title, String isoDate) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "  <dc:title>" + escapeXml(title) + "</dc:title>\n" +
                "  <dc:creator>ExamPrep AI</dc:creator>\n" +
                "  <cp:lastModifiedBy>ExamPrep AI</cp:lastModifiedBy>\n" +
                "  <dcterms:created xsi:type=\"dcterms:W3CDTF\">" + isoDate + "</dcterms:created>\n" +
                "  <dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + isoDate + "</dcterms:modified>\n" +
                "</cp:coreProperties>";
    }

    private static String buildAppPropsXml(int slideCount) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">\n" +
                "  <TotalTime>0</TotalTime>\n" +
                "  <Words>0</Words>\n" +
                "  <Application>ExamPrep AI</Application>\n" +
                "  <PresentationFormat>Widescreen</PresentationFormat>\n" +
                "  <Slides>" + slideCount + "</Slides>\n" +
                "  <Notes>" + slideCount + "</Notes>\n" +
                "</Properties>";
    }

    private static String buildPresentationXml(int slideCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">\n");
        sb.append("  <p:sldMasterIdLst>\n");
        sb.append("    <p:sldMasterId id=\"2147483648\" r:id=\"rIdMaster1\"/>\n");
        sb.append("  </p:sldMasterIdLst>\n");
        sb.append("  <p:sldIdLst>\n");
        for (int i = 1; i <= slideCount; i++) {
            sb.append("    <p:sldId id=\"").append(255 + i).append("\" r:id=\"rIdSlide").append(i).append("\"/>\n");
        }
        sb.append("  </p:sldIdLst>\n");
        sb.append("  <p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"screen16x9\"/>\n");
        sb.append("  <p:notesSz cx=\"6858000\" cy=\"9144000\"/>\n");
        sb.append("</p:presentation>");
        return sb.toString();
    }

    private static String buildPresentationRelsXml(int slideCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
        sb.append("  <Relationship Id=\"rIdMaster1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>\n");
        sb.append("  <Relationship Id=\"rIdTheme1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>\n");
        for (int i = 1; i <= slideCount; i++) {
            sb.append("  <Relationship Id=\"rIdSlide").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                    .append(i).append(".xml\"/>\n");
        }
        sb.append("</Relationships>");
        return sb.toString();
    }

    private static String buildThemeXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Vintage Blue\">\n" +
                "  <a:themeElements>\n" +
                "    <a:clrScheme name=\"Vintage Blue Scheme\">\n" +
                "      <a:dk1><a:srgbClr val=\"" + COLOR_NAVY_DARK + "\"/></a:dk1>\n" +
                "      <a:lt1><a:srgbClr val=\"" + COLOR_WHITE + "\"/></a:lt1>\n" +
                "      <a:dk2><a:srgbClr val=\"" + COLOR_PRIMARY_BLUE + "\"/></a:dk2>\n" +
                "      <a:lt2><a:srgbClr val=\"" + COLOR_BG_LIGHT + "\"/></a:lt2>\n" +
                "      <a:accent1><a:srgbClr val=\"" + COLOR_PRIMARY_BLUE + "\"/></a:accent1>\n" +
                "      <a:accent2><a:srgbClr val=\"" + COLOR_AMBER + "\"/></a:accent2>\n" +
                "      <a:accent3><a:srgbClr val=\"3B7A57\"/></a:accent3>\n" +
                "      <a:accent4><a:srgbClr val=\"8C5E58\"/></a:accent4>\n" +
                "      <a:accent5><a:srgbClr val=\"4A6984\"/></a:accent5>\n" +
                "      <a:accent6><a:srgbClr val=\"D4A373\"/></a:accent6>\n" +
                "      <a:hlink><a:srgbClr val=\"" + COLOR_PRIMARY_BLUE + "\"/></a:hlink>\n" +
                "      <a:folHlink><a:srgbClr val=\"" + COLOR_AMBER + "\"/></a:folHlink>\n" +
                "    </a:clrScheme>\n" +
                "    <a:fontScheme name=\"Vintage Typography\">\n" +
                "      <a:majorFont><a:latin typeface=\"Georgia\"/></a:majorFont>\n" +
                "      <a:minorFont><a:latin typeface=\"Arial\"/></a:minorFont>\n" +
                "    </a:fontScheme>\n" +
                "    <a:fmtScheme name=\"Vintage Format\"><a:fillStyleLst><a:solidFill><a:srgbClr val=\"" + COLOR_BG_LIGHT + "\"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w=\"9525\"><a:solidFill><a:srgbClr val=\"" + COLOR_BORDER + "\"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst/><a:bgFillStyleLst><a:solidFill><a:srgbClr val=\"" + COLOR_WHITE + "\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme>\n" +
                "  </a:themeElements>\n" +
                "</a:theme>";
    }

    private static String buildSlideMasterXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<p:sldMaster xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">\n" +
                "  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>\n" +
                "  <p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>\n" +
                "  <p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rIdLayout1\"/></p:sldLayoutIdLst>\n" +
                "</p:sldMaster>";
    }

    private static String buildSlideMasterRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rIdLayout1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>\n" +
                "  <Relationship Id=\"rIdTheme\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>\n" +
                "</Relationships>";
    }

    private static String buildSlideLayoutXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<p:sldLayout xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" type=\"blank\">\n" +
                "  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>\n" +
                "  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>\n" +
                "</p:sldLayout>";
    }

    private static String buildSlideLayoutRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rIdMaster\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/>\n" +
                "</Relationships>";
    }

    private static String buildSlideRelsXml(int slideNum) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rIdLayout\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>\n" +
                "  <Relationship Id=\"rIdNotes\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide\" Target=\"../notesSlides/notesSlide" + slideNum + ".xml\"/>\n" +
                "</Relationships>";
    }

    private static String buildNotesSlideXml(String notesText, int slideNum) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<p:notes xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">\n" +
                "  <p:cSld>\n" +
                "    <p:spTree>\n" +
                "      <p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>\n" +
                "      <p:grpSpPr/>\n" +
                "      <p:sp>\n" +
                "        <p:nvSpPr><p:cNvPr id=\"2\" name=\"Notes Box\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>\n" +
                "        <p:spPr><a:xfrm><a:off x=\"457200\" y=\"457200\"/><a:ext cx=\"5943600\" cy=\"8229600\"/></a:xfrm></p:spPr>\n" +
                "        <p:txBody>\n" +
                "          <a:bodyPr/><a:lstStyle/>\n" +
                "          <a:p><a:r><a:rPr sz=\"1400\"/><a:t>" + escapeXml(notesText) + "</a:t></a:r></a:p>\n" +
                "        </p:txBody>\n" +
                "      </p:sp>\n" +
                "    </p:spTree>\n" +
                "  </p:cSld>\n" +
                "</p:notes>";
    }

    private static String buildNotesSlideRelsXml(int slideNum) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rIdSlide\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"../slides/slide" + slideNum + ".xml\"/>\n" +
                "</Relationships>";
    }

    private static String buildSlideXml(SlideOutline slide, int slideNum, int totalSlides, String projectName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">\n");
        sb.append("  <p:cSld>\n");
        sb.append("    <p:spTree>\n");
        sb.append("      <p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>\n");
        sb.append("      <p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>\n");

        int shapeId = 2;

        if ("title".equalsIgnoreCase(slide.getLayout())) {
            // TITLE SLIDE: Dark Navy Hero Background
            sb.append(buildFilledRectShape(shapeId++, 0, 0, SLIDE_WIDTH, SLIDE_HEIGHT, COLOR_NAVY_DARK, null, 0));

            // Top decorative amber accent bar
            sb.append(buildFilledRectShape(shapeId++, 914400, 750000, 2400000, 70000, COLOR_AMBER, null, 0));

            // Main Title
            String title = slide.getHeader() != null && !slide.getHeader().isEmpty() ? slide.getHeader() : projectName;
            sb.append(buildTextShape(shapeId++, 914400, 1500000, 10363200, 2200000, title, 4400, true, COLOR_WHITE, "l", "b"));

            // Subtitle
            String subtitle = slide.getSubheader() != null && !slide.getSubheader().isEmpty() ? slide.getSubheader() : "Comprehensive Study Deck & Topic Review";
            sb.append(buildTextShape(shapeId++, 914400, 3900000, 10363200, 1000000, subtitle, 2200, false, "B0C4DE", "l", "t"));

            // Bottom metadata badge
            String footer = "ExamPrep AI • " + totalSlides + " Slides • Academic Preparation";
            sb.append(buildTextShape(shapeId++, 914400, 5600000, 10363200, 500000, footer, 1400, false, COLOR_AMBER, "l", "t"));

        } else if ("agenda".equalsIgnoreCase(slide.getLayout())) {
            // AGENDA SLIDE
            sb.append(buildFilledRectShape(shapeId++, 0, 0, SLIDE_WIDTH, SLIDE_HEIGHT, COLOR_BG_LIGHT, null, 0));

            // Title
            sb.append(buildTextShape(shapeId++, 914400, 600000, 10363200, 800000, slide.getHeader(), 3400, true, COLOR_PRIMARY_BLUE, "l", "t"));
            sb.append(buildTextShape(shapeId++, 914400, 1350000, 10363200, 500000, "Core Topics Covered in this Deck", 1600, false, COLOR_TEXT_MUTED, "l", "t"));

            // Agenda List
            List<String> bullets = slide.getBulletPoints();
            long y = 2000000;
            for (int b = 0; b < bullets.size() && b < 6; b++) {
                String bullet = bullets.get(b);
                // Numbered pill
                sb.append(buildFilledRectShape(shapeId++, 914400, y, 600000, 500000, COLOR_PRIMARY_BLUE, null, 0));
                sb.append(buildTextShape(shapeId++, 914400, y, 600000, 500000, String.valueOf(b + 1), 1600, true, COLOR_WHITE, "ctr", "ctr"));

                // Topic box
                sb.append(buildFilledRectShape(shapeId++, 1650000, y, 9626400, 500000, COLOR_WHITE, COLOR_BORDER, 9525));
                sb.append(buildTextShape(shapeId++, 1800000, y + 50000, 9300000, 400000, bullet, 1600, false, COLOR_TEXT_DARK, "l", "ctr"));

                y += 680000;
            }

            // Footer
            sb.append(buildFooter(shapeId++, slideNum, totalSlides, projectName));

        } else {
            // CONTENT & SUMMARY SLIDES
            sb.append(buildFilledRectShape(shapeId++, 0, 0, SLIDE_WIDTH, SLIDE_HEIGHT, COLOR_BG_LIGHT, null, 0));

            // Top Header
            sb.append(buildTextShape(shapeId++, 800000, 500000, 8000000, 700000, slide.getHeader(), 3000, true, COLOR_PRIMARY_BLUE, "l", "t"));

            // Subheader
            if (slide.getSubheader() != null && !slide.getSubheader().isEmpty()) {
                sb.append(buildTextShape(shapeId++, 800000, 1200000, 800000, 450000, slide.getSubheader(), 1500, false, COLOR_TEXT_MUTED, "l", "t"));
            }

            // General Knowledge Badge (if applicable)
            if (slide.isFromGeneralKnowledge()) {
                sb.append(buildFilledRectShape(shapeId++, 9000000, 500000, 2400000, 450000, COLOR_AMBER_LIGHT, COLOR_AMBER, 12700));
                sb.append(buildTextShape(shapeId++, 9000000, 500000, 2400000, 450000, "✦ General Knowledge Note", 1100, true, COLOR_AMBER, "ctr", "ctr"));
            }

            // Has callout box on right side?
            boolean hasCallout = (slide.getCalloutContent() != null && !slide.getCalloutContent().trim().isEmpty());

            long bulletsWidth = hasCallout ? 6600000 : 10500000;
            long bulletsStartY = 1750000;

            // Bullets List
            sb.append(buildBulletsShape(shapeId++, 800000, bulletsStartY, bulletsWidth, 4300000, slide.getBulletPoints()));

            // Callout Box on right
            if (hasCallout) {
                long calloutX = 7700000;
                long calloutY = 1750000;
                long calloutW = 3700000;
                long calloutH = 4300000;

                // Box Background & Border
                sb.append(buildFilledRectShape(shapeId++, calloutX, calloutY, calloutW, calloutH, COLOR_CARD_BG, COLOR_PRIMARY_BLUE, 19050));

                // Callout Header Pill
                String calloutTitle = slide.getCalloutTitle() != null && !slide.getCalloutTitle().isEmpty()
                        ? slide.getCalloutTitle() : "EXAM ESSENTIAL";
                sb.append(buildFilledRectShape(shapeId++, calloutX + 250000, calloutY + 250000, calloutW - 500000, 500000, COLOR_PRIMARY_BLUE, null, 0));
                sb.append(buildTextShape(shapeId++, calloutX + 250000, calloutY + 250000, calloutW - 500000, 500000, calloutTitle, 1300, true, COLOR_WHITE, "ctr", "ctr"));

                // Callout Content Text
                sb.append(buildTextShape(shapeId++, calloutX + 300000, calloutY + 900000, calloutW - 600000, calloutH - 1100000,
                        slide.getCalloutContent(), 1400, false, COLOR_TEXT_DARK, "l", "t"));
            }

            // Footer
            sb.append(buildFooter(shapeId++, slideNum, totalSlides, projectName));
        }

        sb.append("    </p:spTree>\n");
        sb.append("  </p:cSld>\n");
        sb.append("</p:sld>");
        return sb.toString();
    }

    private static String buildFooter(int shapeId, int slideNum, int totalSlides, String projectName) {
        String footerText = projectName + " • ExamPrep AI • Slide " + slideNum + " of " + totalSlides;
        return buildTextShape(shapeId, 800000, 6300000, 10592000, 350000, footerText, 1100, false, "8C9BAE", "r", "ctr");
    }

    private static String buildFilledRectShape(
            int id, long x, long y, long cx, long cy,
            String fillColorHex, String borderColorHex, int borderWidthEmus
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("      <p:sp>\n");
        sb.append("        <p:nvSpPr><p:cNvPr id=\"").append(id).append("\" name=\"Rect").append(id).append("\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>\n");
        sb.append("        <p:spPr>\n");
        sb.append("          <a:xfrm><a:off x=\"").append(x).append("\" y=\"").append(y).append("\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm>\n");
        sb.append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n");
        if (fillColorHex != null) {
            sb.append("          <a:solidFill><a:srgbClr val=\"").append(fillColorHex).append("\"/></a:solidFill>\n");
        }
        if (borderColorHex != null && borderWidthEmus > 0) {
            sb.append("          <a:ln w=\"").append(borderWidthEmus).append("\"><a:solidFill><a:srgbClr val=\"").append(borderColorHex).append("\"/></a:solidFill></a:ln>\n");
        } else {
            sb.append("          <a:ln><a:noFill/></a:ln>\n");
        }
        sb.append("        </p:spPr>\n");
        sb.append("      </p:sp>\n");
        return sb.toString();
    }

    private static String buildTextShape(
            int id, long x, long y, long cx, long cy,
            String text, int fontSzHundredths, boolean isBold,
            String colorHex, String align, String vAlign
    ) {
        String pAlign = "ctr".equals(align) ? "ctr" : ("r".equals(align) ? "r" : "l");
        String bAnchor = "ctr".equals(vAlign) ? "ctr" : ("b".equals(vAlign) ? "b" : "t");

        return "      <p:sp>\n" +
                "        <p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"Text" + id + "\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>\n" +
                "        <p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>\n" +
                "        <p:txBody>\n" +
                "          <a:bodyPr anchor=\"" + bAnchor + "\" wrap=\"square\"/><a:lstStyle/>\n" +
                "          <a:p>\n" +
                "            <a:pPr algn=\"" + pAlign + "\"/>\n" +
                "            <a:r><a:rPr lang=\"en-US\" sz=\"" + fontSzHundredths + "\" b=\"" + (isBold ? "1" : "0") + "\"><a:solidFill><a:srgbClr val=\"" + colorHex + "\"/></a:solidFill><a:latin typeface=\"Arial\"/></a:rPr><a:t>" + escapeXml(text) + "</a:t></a:r>\n" +
                "          </a:p>\n" +
                "        </p:txBody>\n" +
                "      </p:sp>\n";
    }

    private static String buildBulletsShape(
            int id, long x, long y, long cx, long cy,
            List<String> bullets
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("      <p:sp>\n");
        sb.append("        <p:nvSpPr><p:cNvPr id=\"").append(id).append("\" name=\"Bullets").append(id).append("\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>\n");
        sb.append("        <p:spPr><a:xfrm><a:off x=\"").append(x).append("\" y=\"").append(y).append("\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>\n");
        sb.append("        <p:txBody>\n");
        sb.append("          <a:bodyPr anchor=\"t\" wrap=\"square\"/><a:lstStyle/>\n");

        if (bullets != null) {
            for (String b : bullets) {
                if (b == null || b.trim().isEmpty()) continue;
                sb.append("          <a:p>\n");
                sb.append("            <a:pPr lvl=\"0\" marL=\"342900\" indent=\"-342900\">\n");
                sb.append("              <a:buClr><a:srgbClr val=\"").append(COLOR_AMBER).append("\"/></a:buClr>\n");
                sb.append("              <a:buChar char=\"•\"/>\n");
                sb.append("              <a:spcBef><a:spcPts val=\"1400\"/></a:spcBef>\n");
                sb.append("            </a:pPr>\n");
                sb.append("            <a:r><a:rPr lang=\"en-US\" sz=\"1700\"><a:solidFill><a:srgbClr val=\"").append(COLOR_TEXT_DARK).append("\"/></a:solidFill><a:latin typeface=\"Arial\"/></a:rPr><a:t>")
                        .append(escapeXml(b)).append("</a:t></a:r>\n");
                sb.append("          </a:p>\n");
            }
        }

        sb.append("        </p:txBody>\n");
        sb.append("      </p:sp>\n");
        return sb.toString();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
