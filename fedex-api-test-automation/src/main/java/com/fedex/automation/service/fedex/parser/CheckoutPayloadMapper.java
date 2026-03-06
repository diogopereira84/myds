package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.service.fedex.exception.CheckoutErrorCode;
import com.fedex.automation.service.fedex.exception.CheckoutOperationException;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

@Component
public class CheckoutPayloadMapper {

    private final ObjectMapper objectMapper;

    public CheckoutPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new CheckoutOperationException(CheckoutErrorCode.SERIALIZATION_ERROR, "Error serializing checkout payload.", ex);
        }
    }

    public String extractEncryptionKey(Response response) {
        if (response == null) {
            throw new CheckoutOperationException(CheckoutErrorCode.NULL_RESPONSE, "Encryption key response was null.");
        }

        try {
            String key = response.jsonPath().getString("encryption.key");
            if (key == null || key.isBlank()) {
                throw new CheckoutOperationException(CheckoutErrorCode.MISSING_ENCRYPTION_KEY, "Encryption key missing in response.");
            }
            return key;
        } catch (CheckoutOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CheckoutOperationException(CheckoutErrorCode.PARSE_ERROR, "Failed to parse encryption key response.", ex);
        }
    }
}

