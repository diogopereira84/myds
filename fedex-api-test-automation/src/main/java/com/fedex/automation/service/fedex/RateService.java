// src/main/java/com/fedex/automation/service/fedex/RateService.java
package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.service.fedex.client.RateApiClient;
import com.fedex.automation.service.fedex.parser.RatePayloadBuilder;
import com.fedex.automation.service.fedex.parser.RateResponseParser;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateService {

    private final RateApiClient rateApiClient;
    private final RatePayloadBuilder ratePayloadBuilder;
    private final RateResponseParser rateResponseParser;

    public Response rateProductEarly() {
        log.info("--- Executing Internal Rate API before Configuration ---");

        ObjectNode rateRequestPayload = ratePayloadBuilder.buildInitialRatePayload();
        Response response = rateApiClient.requestInitialRate(rateRequestPayload);

        JsonNode rootNode = rateResponseParser.parseRawBody(response.asString());
        rateResponseParser.assertBusinessSuccess(rootNode);

        String totalAmount = rateResponseParser.extractTotalAmount(rootNode);
        log.info("Internal Rate API verified successfully. Configured Price: {}", totalAmount);

        return response;
    }
}