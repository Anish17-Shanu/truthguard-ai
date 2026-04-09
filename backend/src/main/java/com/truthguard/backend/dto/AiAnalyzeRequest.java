package com.truthguard.backend.dto;

import java.util.List;

public record AiAnalyzeRequest(
        String text,
        String url,
        String fileBase64,
        String fileName
) {
    public String sourceType() {
        if (text != null && !text.isBlank()) return "text";
        if (url != null && !url.isBlank()) return "url";
        return "file";
    }
}
