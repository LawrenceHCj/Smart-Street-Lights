package com.smartlamp.dto;

import lombok.Data;

import java.util.List;

@Data
public class AskResponse {
    private String answer;
    private List<SourceItem> sources;

    public AskResponse(String answer, List<SourceItem> sources) {
        this.answer = answer;
        this.sources = sources;
    }
}