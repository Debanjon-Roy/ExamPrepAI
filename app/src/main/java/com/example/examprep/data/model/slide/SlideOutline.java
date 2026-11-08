package com.example.examprep.data.model.slide;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SlideOutline implements Serializable {
    private int slideNumber;
    private String layout = "content"; // "title", "agenda", "content", "summary"
    private String header = "";
    private String subheader = "";
    private List<String> bulletPoints = new ArrayList<>();
    private String calloutTitle = "";
    private String calloutContent = "";
    private String speakerNotes = "";
    private boolean fromGeneralKnowledge = false;
    private String visualType = "none";
    private List<String> visualLabels = new ArrayList<>();
    private List<Double> visualValues = new ArrayList<>();
    private String visualTitle = "";
    private String visualUnit = "";
    public List<Double> getVisualValues() { return visualValues; }
    public void setVisualValues(List<Double> values) { visualValues = values; }
    public String getVisualTitle() { return visualTitle; }
    public void setVisualTitle(String title) { visualTitle = title; }
    public String getVisualUnit() { return visualUnit; }
    public void setVisualUnit(String unit) { visualUnit = unit; }
    public boolean hasChart() {
        if (!("bar".equals(visualType) || "line".equals(visualType) || "pie".equals(visualType))
                || visualLabels == null || visualValues == null || visualLabels.size() < 2
                || visualLabels.size() != visualValues.size() || visualLabels.size() > 8) return false;
        double sum = 0;
        for (Double value : visualValues) {
            if (value == null || !Double.isFinite(value) || ("pie".equals(visualType) && value < 0)) return false;
            sum += value;
        }
        return !"pie".equals(visualType) || (Double.isFinite(sum) && sum > 0);
    }

    public String getVisualType() { return visualType; }
    public void setVisualType(String type) { visualType = type; }
    public List<String> getVisualLabels() { return visualLabels; }
    public void setVisualLabels(List<String> labels) { visualLabels = labels; }
    public boolean hasVisual() {
        return hasChart() || ("process".equals(visualType) || "concept".equals(visualType)
                || "comparison".equals(visualType)) && visualLabels != null && visualLabels.size() >= 2;
    }

    public SlideOutline() {}

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getSubheader() {
        return subheader;
    }

    public void setSubheader(String subheader) {
        this.subheader = subheader;
    }

    public List<String> getBulletPoints() {
        return bulletPoints;
    }

    public void setBulletPoints(List<String> bulletPoints) {
        this.bulletPoints = bulletPoints;
    }

    public String getCalloutTitle() {
        return calloutTitle;
    }

    public void setCalloutTitle(String calloutTitle) {
        this.calloutTitle = calloutTitle;
    }

    public String getCalloutContent() {
        return calloutContent;
    }

    public void setCalloutContent(String calloutContent) {
        this.calloutContent = calloutContent;
    }

    public String getSpeakerNotes() {
        return speakerNotes;
    }

    public void setSpeakerNotes(String speakerNotes) {
        this.speakerNotes = speakerNotes;
    }

    public boolean isFromGeneralKnowledge() {
        return fromGeneralKnowledge;
    }

    public void setFromGeneralKnowledge(boolean fromGeneralKnowledge) {
        this.fromGeneralKnowledge = fromGeneralKnowledge;
    }
}
