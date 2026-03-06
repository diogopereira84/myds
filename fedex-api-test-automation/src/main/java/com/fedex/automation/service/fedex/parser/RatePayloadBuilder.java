package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.service.fedex.exception.RateErrorCode;
import com.fedex.automation.service.fedex.exception.RateOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Clock;

@Component
@RequiredArgsConstructor
public class RatePayloadBuilder {

    private static final String TEMPLATE_PATH = "templates/1P_TemplateRateRequest.json";
    private static final String JSON_POINTER_FIRST_PRODUCT = "/rateRequest/products/0";
    private static final String KEY_INSTANCE_ID = "instanceId";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ObjectNode buildInitialRatePayload() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                throw new RateOperationException(RateErrorCode.TEMPLATE_NOT_FOUND, "Template file not found: " + TEMPLATE_PATH);
            }

            JsonNode root = objectMapper.readTree(is);
            if (!(root instanceof ObjectNode payload)) {
                throw new RateOperationException(RateErrorCode.TEMPLATE_SCHEMA_ERROR, "Rate request template root must be a JSON object.");
            }

            JsonNode firstProductNode = payload.at(JSON_POINTER_FIRST_PRODUCT);
            if (!(firstProductNode instanceof ObjectNode firstProduct)) {
                throw new RateOperationException(
                        RateErrorCode.TEMPLATE_SCHEMA_ERROR,
                        "Rate request template missing " + JSON_POINTER_FIRST_PRODUCT
                );
            }

            firstProduct.put(KEY_INSTANCE_ID, clock.millis());
            return payload;
        } catch (RateOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RateOperationException(RateErrorCode.TEMPLATE_PARSE_ERROR, "Failed to load rate request template.", ex);
        }
    }
}

