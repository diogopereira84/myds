package com.fedex.automation.service.fedex;

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

    private static final String API_GATEWAY_CLIENT_ID = "l735d628c13a154cc2abab4ecc50fe0558";

    public void uploadDocument(File pdfFile) {
        log.info("Executing Multipart Upload Document: {}", pdfFile.getName());

        Response response = sessionService.authenticatedRequest()
                .header("client_id", API_GATEWAY_CLIENT_ID)
                .queryParam("ClientName", "POD2.0")
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
        log.info("Document Uploaded. Original ID: {}", originalDocId);
    }

    public void convertToPrintReady() {
        String originalDocId = testContext.getOriginalDocId();
        log.info("Converting document [{}] to Print Ready...", originalDocId);

        String payload = String.format("{\"printReadyRequest\":{\"documentId\":\"%s\",\"conversionOptions\":{\"lockContentOrientation\":false,\"minDPI\":200,\"defaultImageWidthInInches\":\"8.5\",\"defaultImageHeightInInches\":\"11\"},\"normalizationOptions\":{\"lockContentOrientation\":false,\"marginWidthInInches\":\"0\",\"targetWidthInInches\":\"\",\"targetHeightInInches\":\"\",\"targetOrientation\":\"UNKNOWN\"},\"previewURL\":true,\"expiration\":{\"units\":\"HOURS\",\"value\":24}}}", originalDocId);

        Response response = sessionService.authenticatedRequest()
                .header("client_id", API_GATEWAY_CLIENT_ID)
                .queryParam("ClientName", "POD2.0")
                .contentType(ContentType.JSON)
                .body(payload)
                .post(printReadyEndpoint);

        response.then().statusCode(lessThan(300));

        String printReadyDocId = response.jsonPath().getString("output.document.documentId");
        testContext.setPrintReadyDocId(printReadyDocId);
        log.info("Document Processed to Print Ready. New ID: {}", printReadyDocId);
    }
}