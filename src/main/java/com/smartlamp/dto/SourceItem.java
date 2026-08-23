package com.smartlamp.dto;

import lombok.Data;

@Data
public class SourceItem {
    private String title;
    private String section;
    private double score;

    public SourceItem(String title, String section, double score) {
        this.title = title;
        this.section = section;
        this.score = score;
    }
}