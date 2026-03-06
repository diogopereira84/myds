package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.context.TestContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

import static org.hamcrest.Matchers.lessThan;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final SessionService sessionService;
    private final TestContext testContext;

    @Value("${endpoint.document.create}")
    private String createDocEndpoint;

    @Value("${endpoint.document.printready}")
    private String printReadyEndpoint;

    @Value("${fedex.api.gateway.client-id}")
    private String apiGatewayClientId;

    @Value("${fedex.constants.header.client-id}")
    private String headerClientId;

    @Value("${fedex.constants.param.client-name}")
    private String paramClientName;

    @Value("${fedex.constants.integrator-id}")
    private String integratorIdPod2;

    public void uploadDocument(File pdfFile) {
        log.info("Executing Multipart Upload Document: {}", pdfFile.getName());

        Response response = sessionService.authenticatedRequest()
                .header(FedExConstants.HEADER_CLIENT_ID, apiGatewayClientId)
                .queryParam(FedExConstants.PARAM_CLIENT_NAME, FedExConstants.INTEGRATOR_ID_POD2)
                .multiPart("document", pdfFile)
                .multiPart("documentName", pdfFile.getName())
                .multiPart("documentType", "PDF")
                .multiPart("sensitiveData", "false")
                .multiPart("checkPdfForm", "false")
                .multiPart("expiration", "{\"units\":\"HOURS\",\"value\":24}")
                .post(createDocEndpoint);

        response.then().statusCode(lessThan(300));

        String originalDocId = response.jsonPath().getString("output.document.documentId");
        testContext.setOriginalDocId(originalDocId);
    }

    public void convertToPrintReady() {
        String originalDocId = testContext.getOriginalDocId();
        String defaultWidth = resolveConfiguredDimension("DEFAULT_IMAGE_WIDTH", "8.5");
        String defaultHeight = resolveConfiguredDimension("DEFAULT_IMAGE_HEIGHT", "11");
        String payload = String.format("{\"printReadyRequest\":{\"documentId\":\"%s\",\"conversionOptions\":{\"lockContentOrientation\":false,\"minDPI\":200,\"defaultImageWidthInInches\":\"%s\",\"defaultImageHeightInInches\":\"%s\"},\"normalizationOptions\":{\"lockContentOrientation\":false,\"marginWidthInInches\":\"0\",\"targetWidthInInches\":\"\",\"targetHeightInInches\":\"\",\"targetOrientation\":\"UNKNOWN\"},\"previewURL\":true,\"expiration\":{\"units\":\"HOURS\",\"value\":24}}}", originalDocId, defaultWidth, defaultHeight);

        Response response = sessionService.authenticatedRequest()
                .header(FedExConstants.HEADER_CLIENT_ID, apiGatewayClientId)
                .queryParam(FedExConstants.PARAM_CLIENT_NAME, FedExConstants.INTEGRATOR_ID_POD2)
                .contentType(ContentType.JSON)
                .body(payload)
                .post(printReadyEndpoint);

        response.then().statusCode(lessThan(300));

        String printReadyDocId = response.jsonPath().getString("output.document.documentId");
        testContext.setPrintReadyDocId(printReadyDocId);
    }

    private String resolveConfiguredDimension(String propertyName, String fallback) {
        JsonNode productNode = testContext.getCurrentConfiguredProductNode();
        if (productNode == null) {
            return fallback;
        }
        JsonNode properties = productNode.path("properties");
        if (!properties.isArray()) {
            return fallback;
        }
        for (JsonNode prop : properties) {
            if (propertyName.equals(prop.path("name").asText())) {
                String value = prop.path("value").asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return fallback;
    }
}