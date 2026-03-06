package com.fedex.automation.service.fedex.parser;

import com.fedex.automation.service.fedex.exception.CheckoutErrorCode;
import com.fedex.automation.service.fedex.exception.CheckoutOperationException;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CheckoutResponseInspector {

    public void assertNoBusinessException(Response response, String operationName) {
        if (response == null) {
            return;
        }

        String body = safeBody(response);
        if (body.isBlank()) {
            return;
        }

        if (looksLikeJson(body)) {
            JsonPath jsonPath = response.jsonPath();
            String exception = jsonPath.getString("exception");
            String error = jsonPath.getString("error");
            String message = jsonPath.getString("message");
            List<?> errors = jsonPath.getList("errors");

            if (isPresent(exception) || isPresent(error) || hasItems(errors)) {
                throw new CheckoutOperationException(
                        CheckoutErrorCode.BUSINESS_RULE_VIOLATION,
                        operationName + " returned business error payload. Body: " + body
                );
            }

            // Some backends place business exceptions in nested messages only.
            if (isPresent(message) && message.toLowerCase(Locale.ROOT).contains("exception")) {
                throw new CheckoutOperationException(
                        CheckoutErrorCode.BUSINESS_RULE_VIOLATION,
                        operationName + " returned exception message. Body: " + body
                );
            }
            return;
        }

        if (body.toLowerCase(Locale.ROOT).contains("exception")) {
            throw new CheckoutOperationException(
                    CheckoutErrorCode.BUSINESS_RULE_VIOLATION,
                    operationName + " returned exception payload. Body: " + body
            );
        }
    }

    private boolean looksLikeJson(String body) {
        String trimmed = body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasItems(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private String safeBody(Response response) {
        try {
            return response.asString();
        } catch (Exception ex) {
            return "";
        }
    }
}

