package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.service.fedex.exception.DocumentErrorCode;
import com.fedex.automation.service.fedex.exception.DocumentOperationException;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

@Component
public class DocumentPayloadMapper {

    private static final int DEFAULT_MIN_DPI = 200;

    private final ObjectMapper objectMapper;

    public DocumentPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildPrintReadyPayload(String originalDocId, String widthInches, String heightInches) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode request = root.putObject("printReadyRequest");
            request.put("documentId", originalDocId);

            ObjectNode conversionOptions = request.putObject("conversionOptions");
            conversionOptions.put("lockContentOrientation", false);
            conversionOptions.put("minDPI", DEFAULT_MIN_DPI);
            conversionOptions.put("defaultImageWidthInInches", widthInches);
            conversionOptions.put("defaultImageHeightInInches", heightInches);

            ObjectNode normalizationOptions = request.putObject("normalizationOptions");
            normalizationOptions.put("lockContentOrientation", false);
            normalizationOptions.put("marginWidthInInches", "0");
            normalizationOptions.put("targetWidthInInches", "");
            normalizationOptions.put("targetHeightInInches", "");
            normalizationOptions.put("targetOrientation", "UNKNOWN");

            request.put("previewURL", true);

            ObjectNode expiration = request.putObject("expiration");
            expiration.put("units", "HOURS");
            expiration.put("value", 24);

            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new DocumentOperationException(DocumentErrorCode.SERIALIZATION_ERROR, "Failed to build print-ready payload.", ex);
        }
    }

    public String extractDocumentId(Response response, String missingMessage) {
        if (response == null) {
            throw new DocumentOperationException(DocumentErrorCode.NULL_RESPONSE, "Response was null while extracting documentId.");
        }

        try {
            String documentId = response.jsonPath().getString("output.document.documentId");
            if (documentId == null || documentId.isBlank()) {
                throw new DocumentOperationException(DocumentErrorCode.MISSING_DOCUMENT_ID, missingMessage);
            }
            return documentId;
        } catch (DocumentOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DocumentOperationException(DocumentErrorCode.PARSE_ERROR, "Failed to parse documentId from response.", ex);
        }
    }
}

