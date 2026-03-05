package com.fedex.automation.model.printful;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthNonceResponse {

    private String nonce;

    @JsonProperty("template_id")
    private String templateId;

    @JsonProperty("expires_at")
    private Long expiresAt;

    private List<String> scopes;
    private String externalProductId;
}