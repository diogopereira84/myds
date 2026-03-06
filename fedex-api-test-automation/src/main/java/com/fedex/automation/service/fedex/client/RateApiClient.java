package com.fedex.automation.service.fedex.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.config.RateEndpoints;
import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.fedex.exception.RateErrorCode;
import com.fedex.automation.service.fedex.exception.RateOperationException;
import com.fedex.automation.service.fedex.validation.RateRequestValidator;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

@Component
public class RateApiClient {

    private static final int HTTP_OK = 200;

    private final SessionService sessionService;
    private final RateEndpoints rateEndpoints;
    private final RateRequestValidator validator;

    public RateApiClient(SessionService sessionService, RateEndpoints rateEndpoints, RateRequestValidator validator) {
        this.sessionService = sessionService;
        this.rateEndpoints = rateEndpoints;
        this.validator = validator;
    }

    public Response requestInitialRate(ObjectNode payload) {
        validator.requireNonNull(payload, "Rate payload cannot be null.");

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.JSON)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .body(payload.toString())
                .post(rateEndpoints.productRate());

        if (response == null) {
            throw new RateOperationException(RateErrorCode.NULL_RESPONSE, "Internal Rate API response was null.");
        }

        if (response.statusCode() != HTTP_OK) {
            throw new RateOperationException(
                    RateErrorCode.UPSTREAM_STATUS_ERROR,
                    "Internal Rate API HTTP request failed. Status: " + response.statusCode() + ". Body: " + safeBody(response)
            );
        }

        return response;
    }

    private String safeBody(Response response) {
        try {
            return response.asString();
        } catch (Exception ex) {
            return "<unavailable>";
        }
    }
}
