package com.fedex.automation.service.fedex;

import com.fedex.automation.model.fedex.CreateQuotePayload;
import com.fedex.automation.model.fedex.DeliveryRateRequestForm;
import com.fedex.automation.model.fedex.EstimateShippingRequest;
import com.fedex.automation.model.fedex.SubmitOrderRequest;
import com.fedex.automation.service.fedex.client.CheckoutApiClient;
import com.fedex.automation.service.fedex.exception.CheckoutErrorCode;
import com.fedex.automation.service.fedex.exception.CheckoutOperationException;
import com.fedex.automation.service.fedex.parser.CheckoutPayloadMapper;
import com.fedex.automation.service.fedex.validation.CheckoutRequestValidator;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String OP_ESTIMATE_SHIPPING = "estimate shipping";
    private static final String OP_DELIVERY_RATE = "delivery rate";
    private static final String OP_CREATE_QUOTE = "create quote";
    private static final String OP_PAY_RATE = "pay rate";
    private static final String OP_SUBMIT_ORDER = "submit order";
    private static final String OP_ENCRYPTION_KEY = "fetch encryption key";

    private static final String MSG_MASKED_QUOTE_ID_REQUIRED = "Masked Quote ID cannot be null/blank.";
    private static final String MSG_ESTIMATE_REQUEST_REQUIRED = "EstimateShippingRequest cannot be null.";
    private static final String MSG_DELIVERY_RATE_FORM_REQUIRED = "DeliveryRateRequestForm cannot be null.";
    private static final String MSG_QUOTE_PAYLOAD_REQUIRED = "Quote payload cannot be null.";
    private static final String MSG_SUBMIT_ORDER_REQUIRED = "SubmitOrderRequest cannot be null.";
    private static final String MSG_QUOTE_ID_REQUIRED = "quoteId cannot be null/blank.";

    private final CheckoutApiClient checkoutApiClient;
    private final CheckoutPayloadMapper payloadMapper;
    private final CheckoutRequestValidator validator;

    public Response estimateShippingResponse(String maskedQuoteId, EstimateShippingRequest request) {
        validator.requireNonBlank(maskedQuoteId, MSG_MASKED_QUOTE_ID_REQUIRED);
        validator.requireNonNull(request, MSG_ESTIMATE_REQUEST_REQUIRED);

        return executeResponse(OP_ESTIMATE_SHIPPING,
                () -> checkoutApiClient.requestEstimateShipping(maskedQuoteId, request));
    }

    public Response getDeliveryRateResponse(DeliveryRateRequestForm form) {
        validator.requireNonNull(form, MSG_DELIVERY_RATE_FORM_REQUIRED);
        String shipMethodDataJson = toJson(form.getShipMethodData(), "shipMethodData");

        return executeResponse(OP_DELIVERY_RATE,
                () -> checkoutApiClient.requestDeliveryRate(form, shipMethodDataJson));
    }

    public Response createQuoteResponse(CreateQuotePayload quotePayload) {
        validator.requireNonNull(quotePayload, MSG_QUOTE_PAYLOAD_REQUIRED);
        String quotePayloadJson = toJson(quotePayload, "quote payload");

        return executeResponse(OP_CREATE_QUOTE,
                () -> checkoutApiClient.requestCreateQuote(quotePayloadJson));
    }

    public Response callPayRateResponse() {
        return executeResponse(OP_PAY_RATE, checkoutApiClient::requestPayRate);
    }

    public Response submitOrderResponse(SubmitOrderRequest request, String quoteId) {
        validator.requireNonNull(request, MSG_SUBMIT_ORDER_REQUIRED);
        validator.requireNonBlank(quoteId, MSG_QUOTE_ID_REQUIRED);
        String requestJson = toJson(request, "submit order request");

        return executeResponse(OP_SUBMIT_ORDER,
                () -> checkoutApiClient.requestSubmitOrder(requestJson, quoteId));
    }

    public Response fetchEncryptionKeyResponse() {
        return executeResponse(OP_ENCRYPTION_KEY, checkoutApiClient::requestEncryptionKey);
    }

    public String fetchEncryptionKey() {
        return payloadMapper.extractEncryptionKey(fetchEncryptionKeyResponse());
    }

    private String toJson(Object value, String label) {
        try {
            return payloadMapper.toJson(value);
        } catch (CheckoutOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CheckoutOperationException(
                    CheckoutErrorCode.SERIALIZATION_ERROR,
                    "Failed to serialize " + label + ".",
                    ex
            );
        }
    }

    private Response executeResponse(String operationName, ResponseSupplier responseSupplier) {
        try {
            Response response = responseSupplier.get();
            if (response == null) {
                throw new CheckoutOperationException(
                        CheckoutErrorCode.NULL_RESPONSE,
                        "Unexpected null response during " + operationName + "."
                );
            }
            return response;
        } catch (CheckoutOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected failure during {}: {}", operationName, ex.toString());
            throw new CheckoutOperationException(
                    CheckoutErrorCode.UPSTREAM_STATUS_ERROR,
                    "Unexpected failure during " + operationName + ".",
                    ex
            );
        }
    }


    @FunctionalInterface
    private interface ResponseSupplier {
        Response get();
    }
}