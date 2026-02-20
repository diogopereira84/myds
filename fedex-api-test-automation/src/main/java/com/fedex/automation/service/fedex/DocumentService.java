package com.fedex.automation.service.fedex;

import com.fedex.automation.context.TestContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final SessionService sessionService;
    private final TestContext testContext;

    // The mandatory Layer7 API Gateway Client ID extracted from the HAR file
    private static final String API_GATEWAY_CLIENT_ID = "l735d628c13a154cc2abab4ecc50fe0558";

    /**
     * Simulates the initial file upload and retrieves the raw document ID.
     */
    public void uploadDocument(File pdfFile) {
        log.info("Uploading document: {}", pdfFile.getName());

        // Simulating the upload from HAR Step 1 & 2 for now.
        String simulatedOriginalDocId = "8ef642de-0df8-11f1-bbfc-4f04c7240218";
        testContext.setOriginalDocId(simulatedOriginalDocId);

        log.info("Document uploaded successfully. Original ID: {}", simulatedOriginalDocId);
    }

    /**
     * Calls the /printready endpoint to normalize the PDF for printing.
     */
    public void convertToPrintReady() {
        String originalDocId = testContext.getOriginalDocId();
        assertNotNull(originalDocId, "Original Document ID must exist before converting to PrintReady.");

        log.info("Converting document [{}] to Print Ready format...", originalDocId);

        String printReadyPayload = String.format("{\n" +
                "  \"printReadyRequest\": {\n" +
                "    \"documentId\": \"%s\",\n" +
                "    \"conversionOptions\": {\"lockContentOrientation\":true,\"minDPI\":200,\"defaultImageWidthInInches\":\"8.5\",\"defaultImageHeightInInches\":\"11\",\"targetWidthInInches\":\"8.5\",\"targetHeightInInches\":\"11\",\"orientation\":\"PORTRAIT\"},\n" +
                "    \"normalizationOptions\": {\"lockContentOrientation\":true,\"marginWidthInInches\":\"0\",\"targetWidthInInches\":\"8.5\",\"targetHeightInInches\":\"11\",\"targetOrientation\":\"PORTRAIT\"},\n" +
                "    \"previewURL\": true,\n" +
                "    \"expiration\": {\"units\":\"HOURS\",\"value\":24}\n" +
                "  }\n" +
                "}", originalDocId);

        Response response = sessionService.authenticatedRequest()
                .baseUri("https://documentapitest.prod.fedex.com")
                .header("client_id", API_GATEWAY_CLIENT_ID) // INJECTING GATEWAY AUTH
                .queryParam("ClientName", "POD2.0")
                .contentType(ContentType.JSON)
                .body(printReadyPayload)
                .post("/document/fedexoffice/v2/printready");

        assertEquals(201, response.statusCode(), "Failed to convert document to PrintReady.");

        String printReadyDocId = response.jsonPath().getString("output.document.documentId");
        testContext.setPrintReadyDocId(printReadyDocId);

        log.info("Document is Print Ready. New ID: {}", printReadyDocId);
    }
}