package com.fedex.automation.service.fedex.client;

import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.fedex.exception.ProductCatalogErrorCode;
import com.fedex.automation.service.fedex.exception.ProductCatalogOperationException;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCatalogApiClient {

    private static final int HTTP_OK = 200;
    private static final String HEADER_ACCEPT = "accept";
    private static final String HEADER_SEC_FETCH_SITE = "sec-fetch-site";
    private static final String HEADER_ACCEPT_ALL = "*/*";
    private static final String SEC_FETCH_SITE_SAME_SITE = "same-site";

    private final SessionService sessionService;

    @Value("${base.url.www}")
    private String wwwBaseUrl;

    public String requestWwwBody(String path, String failureMessage) {
        if (path == null || path.isBlank()) {
            throw new ProductCatalogOperationException(ProductCatalogErrorCode.INVALID_REQUEST, "Request path cannot be null/blank.");
        }

        Response response = sessionService.authenticatedRequest()
                .baseUri(wwwBaseUrl)
                .header(HEADER_ACCEPT, HEADER_ACCEPT_ALL)
                .header(HEADER_SEC_FETCH_SITE, SEC_FETCH_SITE_SAME_SITE)
                .get(path);

        return extractBodyOrThrow(response, failureMessage);
    }

    private String extractBodyOrThrow(Response response, String failureMessage) {
        if (response == null) {
            throw new ProductCatalogOperationException(ProductCatalogErrorCode.NULL_RESPONSE, failureMessage + ": response was null.");
        }

        int status = response.statusCode();
        String body = safeBody(response);
        if (status != HTTP_OK) {
            throw new ProductCatalogOperationException(
                    ProductCatalogErrorCode.UPSTREAM_STATUS_ERROR,
                    failureMessage + ". Status: " + status + ". Body: " + body
            );
        }

        return body;
    }

    private String safeBody(Response response) {
        try {
            return response.asString();
        } catch (Exception ex) {
            return "<unavailable>";
        }
    }
}

