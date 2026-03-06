package com.fedex.automation.model.printful;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrintfulFileCallbackResponse {
    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String temporaryFileKey;
        private String filename;
        private String mimeType;
        private Boolean isReady;
        private Boolean isProcessing;
    }
}