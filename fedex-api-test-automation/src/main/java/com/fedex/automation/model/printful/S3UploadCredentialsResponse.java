package com.fedex.automation.model.printful;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3UploadCredentialsResponse {
    private S3Credentials result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3Credentials {
        private String temporaryFileId;

        @JsonProperty("success_action_status")
        private String successActionStatus;

        private String acl;
        private String key;

        @JsonProperty("X-Amz-Credential")
        private String xAmzCredential;

        @JsonProperty("X-Amz-Algorithm")
        private String xAmzAlgorithm;

        @JsonProperty("X-Amz-Date")
        private String xAmzDate;

        @JsonProperty("Policy")
        private String policy;

        @JsonProperty("X-Amz-Signature")
        private String xAmzSignature;
    }
}