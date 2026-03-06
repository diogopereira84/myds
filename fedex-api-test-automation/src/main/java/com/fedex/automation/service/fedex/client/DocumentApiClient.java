package com.fedex.automation.service.fedex.client;

import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.fedex.exception.DocumentErrorCode;
import com.fedex.automation.service.fedex.exception.DocumentOperationException;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@RequiredArgsConstructor
public class DocumentApiClient {

    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;
    private static final String HEADER_ACCEPT = "application/json, text/javascript, */*; q=0.01";
    private static final String QUERY_CLIENT_NAME = FedExConstants.PARAM_CLIENT_NAME;
    private static final String FIELD_DOCUMENT = "document";
    private static final String FIELD_DOCUMENT_NAME = "documentName";
    private static final String FIELD_DOCUMENT_TYPE = "documentType";
    private static final String FIELD_SENSITIVE_DATA = "sensitiveData";
    private static final String FIELD_CHECK_PDF_FORM = "checkPdfForm";
    private static final String FIELD_EXPIRATION = "expiration";
    private static final String VALUE_DOCUMENT_TYPE_PDF = "PDF";
    private static final String VALUE_FALSE = "false";

    private final SessionService sessionService;

    @Value("${endpoint.document.create}")
    private String createDocEndpoint;

    @Value("${endpoint.document.printready}")
    private String printReadyEndpoint;

    @Value("${fedex.api.gateway.client-id}")
    private String apiGatewayClientId;

    public Response requestDocumentUpload(File file, String expirationJson) {
        if (file == null) {
            throw new DocumentOperationException(DocumentErrorCode.INVALID_REQUEST, "Upload file cannot be null.");
        }

        Response response = sessionService.authenticatedRequest()
                .header(FedExConstants.HEADER_CLIENT_ID, apiGatewayClientId)
                .queryParam(QUERY_CLIENT_NAME, FedExConstants.INTEGRATOR_ID_POD2)
                .multiPart(FIELD_DOCUMENT, file)
                .multiPart(FIELD_DOCUMENT_NAME, file.getName())
                .multiPart(FIELD_DOCUMENT_TYPE, VALUE_DOCUMENT_TYPE_PDF)
                .multiPart(FIELD_SENSITIVE_DATA, VALUE_FALSE)
                .multiPart(FIELD_CHECK_PDF_FORM, VALUE_FALSE)
                .multiPart(FIELD_EXPIRATION, expirationJson)
                .post(createDocEndpoint);

        return ensureSuccess(response, "Document upload failed");
    }

    public Response requestPrintReadyConversion(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new DocumentOperationException(DocumentErrorCode.INVALID_REQUEST, "Print-ready payload cannot be null/blank.");
        }

        Response response = sessionService.authenticatedRequest()
                .header(FedExConstants.HEADER_CLIENT_ID, apiGatewayClientId)
                .header("Accept", HEADER_ACCEPT)
                .queryParam(QUERY_CLIENT_NAME, FedExConstants.INTEGRATOR_ID_POD2)
                .contentType(ContentType.JSON)
                .body(payloadJson)
                .post(printReadyEndpoint);

        return ensureSuccess(response, "Print-ready conversion failed");
    }

    private Response ensureSuccess(Response response, String message) {
        if (response == null) {
            throw new DocumentOperationException(DocumentErrorCode.NULL_RESPONSE, message + ": response was null.");
        }

        int statusCode = response.statusCode();
        if (statusCode >= HTTP_SUCCESS_MAX_EXCLUSIVE) {
            throw new DocumentOperationException(
                    DocumentErrorCode.UPSTREAM_STATUS_ERROR,
                    message + ". Status: " + statusCode + ". Body: " + safeBody(response)
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

