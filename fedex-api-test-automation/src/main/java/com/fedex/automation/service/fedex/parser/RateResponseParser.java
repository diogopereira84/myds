package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.service.fedex.exception.RateErrorCode;
import com.fedex.automation.service.fedex.exception.RateOperationException;
import org.springframework.stereotype.Component;

@Component
public class RateResponseParser {

    private static final String JSON_POINTER_TOTAL_AMOUNT = "/response/output/rate/rateDetails/0/totalAmount";

    private final ObjectMapper objectMapper;

    public RateResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseRawBody(String rawBody) {
        try {
            if (rawBody == null || rawBody.isBlank()) {
                throw new RateOperationException(RateErrorCode.PARSE_ERROR, "Rate response body is empty.");
            }

            String normalizedBody = rawBody.trim();

            if (normalizedBody.startsWith("\"") && normalizedBody.endsWith("\"")) {
                String unescapedJson = objectMapper.readValue(normalizedBody, String.class);
                return objectMapper.readTree(unescapedJson);
            }
            return objectMapper.readTree(normalizedBody);
        } catch (RateOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RateOperationException(RateErrorCode.PARSE_ERROR, "Failed to parse internal rate response body.", ex);
        }
    }

    public void assertBusinessSuccess(JsonNode rootNode) {
        if (rootNode == null || !rootNode.path("status").asBoolean(false)) {
            JsonNode errorsNode = (rootNode == null)
                    ? objectMapper.createArrayNode()
                    : rootNode.path("response").path("errors");
            throw new RateOperationException(
                    RateErrorCode.BUSINESS_STATUS_FALSE,
                    "Internal Rate API returned status: false. Backend Errors: " + errorsNode
            );
        }
    }

    public String extractTotalAmount(JsonNode rootNode) {
        if (rootNode == null) {
            throw new RateOperationException(RateErrorCode.PARSE_ERROR, "Cannot extract totalAmount from null response tree.");
        }

        JsonNode totalAmountNode = rootNode.at(JSON_POINTER_TOTAL_AMOUNT);
        String totalAmount = totalAmountNode.asText("").trim();
        if (totalAmount.isEmpty()) {
            throw new RateOperationException(
                    RateErrorCode.MISSING_TOTAL_AMOUNT,
                    "Internal Rate API response missing totalAmount at path: " + JSON_POINTER_TOTAL_AMOUNT
            );
        }

        return totalAmount;
    }
}
