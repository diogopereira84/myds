package com.fedex.automation.service.fedex.client;

import com.fedex.automation.config.CheckoutEndpoints;
import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.model.fedex.DeliveryRateRequestForm;
import com.fedex.automation.model.fedex.EstimateShippingRequest;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.fedex.exception.CheckoutErrorCode;
import com.fedex.automation.service.fedex.exception.CheckoutOperationException;
import com.fedex.automation.service.fedex.parser.CheckoutResponseInspector;
import com.fedex.automation.service.fedex.validation.CheckoutRequestValidator;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CheckoutApiClient {

    private static final String FORM_URLENC = "application/x-www-form-urlencoded; charset=UTF-8";
    private static final String FIELD_STREET = "street";
    private static final String FIELD_FIRSTNAME = "firstname";
    private static final String FIELD_LASTNAME = "lastname";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_TELEPHONE = "telephone";
    private static final String FIELD_SHIP_METHOD = "ship_method";
    private static final String FIELD_ZIPCODE = "zipcode";
    private static final String FIELD_REGION_ID = "region_id";
    private static final String FIELD_CITY = "city";
    private static final String FIELD_COMPANY = "company";
    private static final String FIELD_IS_RESIDENCE_SHIPPING = "is_residence_shipping";
    private static final String FIELD_SHIP_METHOD_DATA = "ship_method_data";
    private static final String FIELD_THIRD_PARTY_CARRIER_CODE = "third_party_carrier_code";
    private static final String FIELD_THIRD_PARTY_METHOD_CODE = "third_party_method_code";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_PICKSTORE = "pickstore";
    private static final String VALUE_EMPTY = "";
    private static final String VALUE_PICKSTORE_NO = "0";
    private static final String FIELD_FIRST_PARTY_CARRIER_CODE = "first_party_carrier_code";
    private static final String FIELD_FIRST_PARTY_METHOD_CODE = "first_party_method_code";
    private static final String FIELD_LOCATION_ID = "location_id";
    private static final String FIELD_FEDEX_ACCOUNT = "fedexAccount";
    private static final String FIELD_SHIPPING_ACCOUNT = "shippingAccount";
    private static final String FIELD_SELECTED_PRODUCTION_ID = "selectedProductionId";

    private final SessionService sessionService;
    private final CheckoutEndpoints checkoutEndpoints;
    private final CheckoutRequestValidator validator;
    private final CheckoutResponseInspector responseInspector;

    public CheckoutApiClient(
            SessionService sessionService,
            CheckoutEndpoints checkoutEndpoints,
            CheckoutRequestValidator validator,
            CheckoutResponseInspector responseInspector
    ) {
        this.sessionService = sessionService;
        this.checkoutEndpoints = checkoutEndpoints;
        this.validator = validator;
        this.responseInspector = responseInspector;
    }

    public Response requestEstimateShipping(String maskedQuoteId, EstimateShippingRequest request) {
        validator.requireNonBlank(maskedQuoteId, "maskedQuoteId cannot be null/blank.");
        validator.requireNonNull(request, "EstimateShippingRequest cannot be null.");

        String url = checkoutEndpoints.estimate().replace("{cartId}", maskedQuoteId);

        Response response = sessionService.checkoutRequest()
                .contentType(ContentType.JSON)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .body(request)
                .post(url);

        return ensureSuccess(response, "Estimate shipping failed");
    }

    public Response requestDeliveryRate(DeliveryRateRequestForm form, String shipMethodDataJson) {
        validator.requireNonNull(form, "DeliveryRateRequestForm cannot be null.");
        List<String> streetLines = validator.requireStreetLines(form);
        validator.requireNonBlank(shipMethodDataJson, "shipMethodDataJson cannot be null/blank.");

        RequestSpecification request = formRequest();
        applyDeliveryRateFormParams(request, form, streetLines, shipMethodDataJson);

        Response response = request.post(checkoutEndpoints.deliveryRate());
        return ensureSuccess(response, "Delivery rate request failed");
    }

    public Response requestCreateQuote(String quotePayloadJson) {
        validator.requireNonBlank(quotePayloadJson, "quotePayloadJson cannot be null/blank.");

        Response response = formRequest()
                .formParam(FIELD_DATA, quotePayloadJson)
                .post(checkoutEndpoints.createQuote());

        response = ensureSuccess(response, "Create quote request failed");
        responseInspector.assertNoBusinessException(response, "Create quote");

        return response;
    }

    public Response requestPayRate() {
        Response response = formRequest()
                .formParam(FIELD_FEDEX_ACCOUNT, VALUE_EMPTY)
                .formParam(FIELD_SHIPPING_ACCOUNT, VALUE_EMPTY)
                .formParam(FIELD_SELECTED_PRODUCTION_ID, VALUE_EMPTY)
                .post(checkoutEndpoints.payRate());

        return ensureSuccess(response, "Pay rate request failed");
    }

    public Response requestSubmitOrder(String requestJson, String quoteId) {
        validator.requireNonBlank(requestJson, "requestJson cannot be null/blank.");
        validator.requireNonBlank(quoteId, "quoteId cannot be null/blank.");

        Map<String, String> checkoutCookies = new HashMap<>();
        checkoutCookies.put("quoteId", quoteId);

        Response response = formRequest(checkoutCookies)
                .queryParam(FIELD_PICKSTORE, VALUE_PICKSTORE_NO)
                .formParam(FIELD_DATA, requestJson)
                .post(checkoutEndpoints.submitOrder());

        return ensureSuccess(response, "Submit order failed");
    }

    public Response requestEncryptionKey() {
        Response response = sessionService.checkoutRequest()
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .get(checkoutEndpoints.encryptionKey());

        return ensureSuccess(response, "Fetch encryption key failed");
    }

    private void applyDeliveryRateFormParams(
            RequestSpecification request,
            DeliveryRateRequestForm form,
            List<String> streetLines,
            String shipMethodDataJson
    ) {
        request
                .formParam(FIELD_FIRSTNAME, form.getFirstname())
                .formParam(FIELD_LASTNAME, form.getLastname())
                .formParam(FIELD_EMAIL, form.getEmail())
                .formParam(FIELD_TELEPHONE, form.getTelephone())
                .formParam(FIELD_SHIP_METHOD, form.getShipMethod())
                .formParam(FIELD_ZIPCODE, form.getZipcode())
                .formParam(FIELD_REGION_ID, form.getRegionId())
                .formParam(FIELD_CITY, form.getCity())
                .formParam(FIELD_STREET + "[]", streetLines.get(0))
                .formParam(FIELD_STREET + "[]", streetLines.size() > 1 ? nullSafe(streetLines.get(1)) : VALUE_EMPTY)
                .formParam(FIELD_COMPANY, form.getCompany())
                .formParam(FIELD_IS_RESIDENCE_SHIPPING, String.valueOf(form.getIsResidenceShipping()))
                .formParam(FIELD_SHIP_METHOD_DATA, shipMethodDataJson)
                .formParam(FIELD_LOCATION_ID, nullSafe(form.getLocationId()));

        boolean isFirstParty = hasText(form.getFirstPartyCarrierCode()) || hasText(form.getFirstPartyMethodCode());

        if (isFirstParty) {
            request
                    .formParam(FIELD_FIRST_PARTY_CARRIER_CODE, nullSafe(form.getFirstPartyCarrierCode()))
                    .formParam(FIELD_FIRST_PARTY_METHOD_CODE, nullSafe(form.getFirstPartyMethodCode()));
            return;
        }

        request
                .formParam(FIELD_THIRD_PARTY_CARRIER_CODE, nullSafe(form.getThirdPartyCarrierCode()))
                .formParam(FIELD_THIRD_PARTY_METHOD_CODE, nullSafe(form.getThirdPartyMethodCode()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Response ensureSuccess(Response response, String message) {
        if (response == null) {
            throw new CheckoutOperationException(CheckoutErrorCode.NULL_RESPONSE, message + ": response was null.");
        }

        int statusCode = response.statusCode();
        if (statusCode >= 300) {
            throw new CheckoutOperationException(
                    CheckoutErrorCode.UPSTREAM_STATUS_ERROR,
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

    private String nullSafe(String value) {
        return value == null ? VALUE_EMPTY : value;
    }

    private RequestSpecification formRequest() {
        return sessionService.checkoutRequest()
                .contentType(FORM_URLENC)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST);
    }

    private RequestSpecification formRequest(Map<String, String> cookies) {
        return sessionService.checkoutRequest(cookies)
                .contentType(FORM_URLENC)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST);
    }
}
