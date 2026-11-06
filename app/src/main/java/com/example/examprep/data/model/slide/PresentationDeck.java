package com.example.examprep.data.model.slide;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PresentationDeck implements Serializable {
    private String presentationTitle = "Study Presentation";
    private String subtitle = "Comprehensive Topic Review";
    private String language = "en";
    private int totalSlides = 0;
    private List<SlideOutline> slides = new ArrayList<>();

    public PresentationDeck() {}

    public static PresentationDeck fromJson(String jsonString) {
        PresentationDeck deck = new PresentationDeck();
        try {
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            if (root.has("presentation_title")) {
                deck.setPresentationTitle(root.get("presentation_title").getAsString());
            } else if (root.has("title")) {
                deck.setPresentationTitle(root.get("title").getAsString());
            }

            if (root.has("subtitle")) {
                deck.setSubtitle(root.get("subtitle").getAsString());
            }
            if (root.has("language")) {
                deck.setLanguage(root.get("language").getAsString());
            }

            List<SlideOutline> slideList = new ArrayList<>();
            if (root.has("slides") && root.get("slides").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("slides");
                int index = 1;
                for (JsonElement elem : arr) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        SlideOutline slide = new SlideOutline();
                        slide.setSlideNumber(obj.has("slide_number") ? obj.get("slide_number").getAsInt() : index);
                        slide.setLayout(obj.has("layout") ? obj.get("layout").getAsString() : "content");
                        slide.setHeader(obj.has("header") ? obj.get("header").getAsString() : (obj.has("title") ? obj.get("title").getAsString() : "Slide " + index));
                        slide.setSubheader(obj.has("subheader") ? obj.get("subheader").getAsString() : "");

                        if (obj.has("bullet_points") && obj.get("bullet_points").isJsonArray()) {
                            List<String> bullets = new ArrayList<>();
                            for (JsonElement bElem : obj.getAsJsonArray("bullet_points")) {
                                bullets.add(bElem.getAsString());
                            }
                            slide.setBulletPoints(bullets);
                        }

                        if (obj.has("callout_title")) {
                            slide.setCalloutTitle(obj.get("callout_title").getAsString());
                        }
                        if (obj.has("callout_content")) {
                            slide.setCalloutContent(obj.get("callout_content").getAsString());
                        }
                        if (obj.has("speaker_notes")) {
                            slide.setSpeakerNotes(obj.get("speaker_notes").getAsString());
                        } else if (obj.has("notes")) {
                            slide.setSpeakerNotes(obj.get("notes").getAsString());
                        }
                        if (obj.has("from_general_knowledge")) {
                            slide.setFromGeneralKnowledge(obj.get("from_general_knowledge").getAsBoolean());
                        }

                        slideList.add(slide);
                        index++;
                    }
                }
            }
            deck.setSlides(slideList);
            deck.setTotalSlides(slideList.size());
        } catch (Exception e) {
            // fallback minimal deck
            SlideOutline s = new SlideOutline();
            s.setSlideNumber(1);
            s.setLayout("title");
            s.setHeader("Study Presentation");
            deck.getSlides().add(s);
            deck.setTotalSlides(1);
        }
        return deck;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String getPresentationTitle() {
        return presentationTitle;
    }

    public void setPresentationTitle(String presentationTitle) {
        this.presentationTitle = presentationTitle;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getTotalSlides() {
        return totalSlides;
    }

    public void setTotalSlides(int totalSlides) {
        this.totalSlides = totalSlides;
    }

    public List<SlideOutline> getSlides() {
        return slides;
    }

    public void setSlides(List<SlideOutline> slides) {
        this.slides = slides;
    }
}
