package com.fedex.automation.service.fedex.client;

import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.model.fedex.AddToCartRequest;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.fedex.exception.CartErrorCode;
import com.fedex.automation.service.fedex.exception.CartOperationException;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartApiClient {

    private static final int HTTP_OK = 200;
    private static final int HTTP_REDIRECT = 302;
    private static final String VALUE_ACCEPT_ANY = "*/*";
    private static final String VALUE_ACCEPT_AJAX = "application/json, text/javascript, */*; q=0.01";
    private static final String QUERY_PARAM_SECTIONS = "sections";
    private static final String QUERY_PARAM_FORCE_NEW_SECTION_TIMESTAMP = "force_new_section_timestamp";
    private static final String QUERY_PARAM_CACHE_BUSTER = "_";
    private static final String VALUE_TRUE = "true";

    private final SessionService sessionService;

    @Value("${endpoint.cart.get}")
    private String cartGetEndpoint;

    @Value("${endpoint.cart.add.3p}")
    private String cartAdd3PEndpoint;

    @Value("${endpoint.customer.section.load}")
    private String customerSectionLoadEndpoint;

    public String requestCartPageBody() {
        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.JSON)
                .header("accept", VALUE_ACCEPT_ANY)
                .get(cartGetEndpoint);
        return extractBodyOrThrow(response, HTTP_OK, "Failed to load cart page");
    }

    public void addToCartExpectRedirect(AddToCartRequest request) {
        if (request == null) {
            throw new CartOperationException(CartErrorCode.INVALID_REQUEST, "Add-to-cart request cannot be null.");
        }

        Response response = authenticatedAjaxRequest()
                .contentType(ContentType.URLENC)
                .formParam("form_key", request.getFormKey())
                .formParam("sku", request.getSku())
                .formParam("qty", String.valueOf(request.getQuantity()))
                .formParam("offer_id", request.getOfferId())
                .formParam("punchout_disabled", request.getPunchoutDisabled())
                .formParam("super_attribute", request.getSuperAttribute())
                .post(cartAdd3PEndpoint);

        extractBodyOrThrow(response, HTTP_REDIRECT, "Add to cart should redirect (302)");
    }

    public String requestCustomerSectionForValidationBody(long timestamp) {
        Response response = authenticatedAjaxRequest()
                .baseUri(sessionService.getBaseUrl())
                .header("Accept", VALUE_ACCEPT_AJAX)
                .header("Referer", sessionService.getBaseUrl() + cartGetEndpoint)
                .queryParam(QUERY_PARAM_SECTIONS, "cart,messages")
                .queryParam(QUERY_PARAM_FORCE_NEW_SECTION_TIMESTAMP, VALUE_TRUE)
                .queryParam(QUERY_PARAM_CACHE_BUSTER, String.valueOf(timestamp))
                .get(customerSectionLoadEndpoint);

        return extractBodyOrThrow(response, HTTP_OK, "Customer section load failed");
    }

    public Response requestCustomerSectionUi(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new CartOperationException(CartErrorCode.INVALID_REQUEST, "timestamp cannot be null/blank.");
        }

        return checkoutAjaxRequest()
                .queryParam(QUERY_PARAM_SECTIONS, "messages,company,cart,session,marketplace,sso_section")
                .queryParam(QUERY_PARAM_FORCE_NEW_SECTION_TIMESTAMP, VALUE_TRUE)
                .queryParam(QUERY_PARAM_CACHE_BUSTER, timestamp)
                .get(customerSectionLoadEndpoint);
    }

    private String extractBodyOrThrow(Response response, int expectedStatus, String message) {
        if (response == null) {
            throw new CartOperationException(CartErrorCode.NULL_RESPONSE, message + ": response was null.");
        }

        int actualStatus = response.statusCode();
        if (actualStatus != expectedStatus) {
            throw new CartOperationException(
                    CartErrorCode.UNEXPECTED_HTTP_STATUS,
                    String.format("%s. Expected HTTP %d but got %d. Body: %s",
                            message,
                            expectedStatus,
                            actualStatus,
                            safeBody(response))
            );
        }

        return safeBody(response);
    }

    private String safeBody(Response response) {
        try {
            return response.asString();
        } catch (Exception ex) {
            return "<unavailable>";
        }
    }

    private RequestSpecification authenticatedAjaxRequest() {
        return sessionService.authenticatedRequest()
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST);
    }

    private RequestSpecification checkoutAjaxRequest() {
        return sessionService.checkoutRequest()
                .header("Accept", VALUE_ACCEPT_AJAX)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST);
    }
}
