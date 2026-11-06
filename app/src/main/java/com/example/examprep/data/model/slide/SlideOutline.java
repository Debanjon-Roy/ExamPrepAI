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
