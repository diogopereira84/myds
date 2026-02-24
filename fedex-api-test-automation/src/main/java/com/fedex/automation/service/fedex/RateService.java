// src/main/java/com/fedex/automation/service/fedex/RateService.java
package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${endpoint.rate.product}")
    private String rateEndpoint;

    public void rateProductEarly() {
        log.info("--- [Call 2] Executing Internal Rate API before Configuration ---");

        try {
            // Load the static Expected Rate JSON template
            InputStream is = getClass().getClassLoader().getResourceAsStream("templates/1P_TemplateRateRequest.json");
            if (is == null) throw new IllegalArgumentException("Template file not found: templates/1P_TemplateRateRequest.json");

            ObjectNode rateRequestPayload = (ObjectNode) objectMapper.readTree(is);

            // Dynamically inject the instanceId to prevent missing property errors
            ObjectNode firstProduct = (ObjectNode) rateRequestPayload.at("/rateRequest/products/0");
            firstProduct.put("instanceId", System.currentTimeMillis());

            Response response = sessionService.authenticatedRequest()
                    .contentType(ContentType.JSON)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .body(rateRequestPayload.toString())
                    .post(rateEndpoint);

            assertEquals(200, response.statusCode(), "Internal Rate API HTTP request failed.");

            String rawBody = response.asString();
            JsonNode rootNode;

            if (rawBody.startsWith("\"") && rawBody.endsWith("\"")) {
                String unescapedJson = objectMapper.readValue(rawBody, String.class);
                rootNode = objectMapper.readTree(unescapedJson);
            } else {
                rootNode = objectMapper.readTree(rawBody);
            }

            if (!rootNode.path("status").asBoolean()) {
                JsonNode errorsNode = rootNode.path("response").path("errors");
                fail("Internal Rate API returned status: false. Backend Errors: " + errorsNode.toString());
            }

            String totalAmount = rootNode.at("/response/output/rate/rateDetails/0/totalAmount").asText("Unknown Price");
            log.info("Internal Rate API Verified successfully. Configured Price: {}", totalAmount);

        } catch (Exception e) {
            fail("Failed to parse or execute Internal Rate API request.", e);
        }
    }
}