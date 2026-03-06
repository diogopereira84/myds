package com.fedex.automation.service.fedex;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.service.fedex.client.DocumentApiClient;
import com.fedex.automation.service.fedex.exception.DocumentErrorCode;
import com.fedex.automation.service.fedex.exception.DocumentOperationException;
import com.fedex.automation.service.fedex.parser.DocumentDimensionResolver;
import com.fedex.automation.service.fedex.parser.DocumentPayloadMapper;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final String PROP_DEFAULT_IMAGE_WIDTH = "DEFAULT_IMAGE_WIDTH";
    private static final String PROP_DEFAULT_IMAGE_HEIGHT = "DEFAULT_IMAGE_HEIGHT";
    private static final String DEFAULT_WIDTH_IN = "8.5";
    private static final String DEFAULT_HEIGHT_IN = "11";
    private static final String EXPIRATION_JSON = "{\"units\":\"HOURS\",\"value\":24}";

    private final TestContext testContext;
    private final DocumentApiClient documentApiClient;
    private final DocumentPayloadMapper payloadMapper;
    private final DocumentDimensionResolver dimensionResolver;

    public void uploadDocument(File pdfFile) {
        validateReadableFile(pdfFile);
        log.info("Executing Multipart Upload Document: {}", pdfFile.getName());

        Response response = documentApiClient.requestDocumentUpload(pdfFile, EXPIRATION_JSON);
        String originalDocId = payloadMapper.extractDocumentId(response, "Document upload response missing documentId.");

        testContext.setOriginalDocId(originalDocId);
    }

    public void convertToPrintReady() {
        String originalDocId = testContext.getOriginalDocId();
        if (originalDocId == null || originalDocId.isBlank()) {
            throw new DocumentOperationException(
                    DocumentErrorCode.INVALID_REQUEST,
                    "Original documentId is missing. Ensure uploadDocument ran successfully."
            );
        }

        String defaultWidth = dimensionResolver.resolveConfiguredDimension(PROP_DEFAULT_IMAGE_WIDTH, DEFAULT_WIDTH_IN);
        String defaultHeight = dimensionResolver.resolveConfiguredDimension(PROP_DEFAULT_IMAGE_HEIGHT, DEFAULT_HEIGHT_IN);

        String payload = payloadMapper.buildPrintReadyPayload(originalDocId, defaultWidth, defaultHeight);
        Response response = documentApiClient.requestPrintReadyConversion(payload);
        String printReadyDocId = payloadMapper.extractDocumentId(response, "Print-ready response missing documentId.");

        testContext.setPrintReadyDocId(printReadyDocId);
    }

    private void validateReadableFile(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.isFile() || !pdfFile.canRead()) {
            throw new DocumentOperationException(
                    DocumentErrorCode.INVALID_REQUEST,
                    "Upload file must exist and be readable: " + (pdfFile == null ? "null" : pdfFile.getAbsolutePath())
            );
        }
    }
}