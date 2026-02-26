package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.model.fedex.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Value("${endpoint.shipping.estimate}")
    private String estimateEndpoint;

    @Value("${endpoint.shipping.deliveryrate}")
    private String deliveryRateEndpoint;

    @Value("${endpoint.quote.create}")
    private String createQuoteEndpoint;

    @Value("${endpoint.pay.rate}")
    private String payRateEndpoint;

    @Value("${endpoint.order.submit}")
    private String submitOrderEndpoint;

    @Value("${endpoint.delivery.encryptionkey}")
    private String encryptionKeyEndpoint;

    @Value("${fedex.constants.header.x-requested-with}")
    private String headerXRequestedWith;

    @Value("${fedex.constants.value.xmlhttprequest}")
    private String valueXmlHttpRequest;

    public EstimateShipMethodResponse[] estimateShipping(String maskedQuoteId, EstimateShippingRequest request) {
        Objects.requireNonNull(maskedQuoteId, "Masked Quote ID cannot be null.");
        String url = estimateEndpoint.replace("{cartId}", maskedQuoteId);

        return sessionService.checkoutRequest()
                .contentType(ContentType.JSON)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .body(request)
                .post(url)
                .then()
                .statusCode(200)
                .extract().as(EstimateShipMethodResponse[].class);
    }

    public JsonNode getDeliveryRate(DeliveryRateRequestForm form) {
        Response response = sessionService.checkoutRequest()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .formParam("firstname", form.getFirstname())
                .formParam("lastname", form.getLastname())
                .formParam("email", form.getEmail())
                .formParam("telephone", form.getTelephone())
                .formParam("ship_method", form.getShipMethod())
                .formParam("zipcode", form.getZipcode())
                .formParam("region_id", form.getRegionId())
                .formParam("city", form.getCity())
                .formParam("street[]", form.getStreet().get(0))
                .formParam("street[]", form.getStreet().size() > 1 ? form.getStreet().get(1) : "")
                .formParam("company", form.getCompany())
                .formParam("is_residence_shipping", form.getIsResidenceShipping().toString())
                .formParam("ship_method_data", mapToJson(form.getShipMethodData()))
                .formParam("third_party_carrier_code", form.getThirdPartyCarrierCode())
                .formParam("third_party_method_code", form.getThirdPartyMethodCode())
                .formParam("first_party_carrier_code", "")
                .formParam("first_party_method_code", "")
                .formParam("location_id", "")
                .post(deliveryRateEndpoint);

        try {
            return objectMapper.readTree(response.asString());
        } catch (Exception e) {
            throw new RuntimeException("Error parsing delivery rate: " + response.asString(), e);
        }
    }

    public void createQuote(CreateQuotePayload quotePayload) {
        try {
            Response response = sessionService.checkoutRequest()
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                    .formParam("data", objectMapper.writeValueAsString(quotePayload))
                    .post(createQuoteEndpoint);

            if (response.statusCode() != 200 || response.asString().toLowerCase().contains("exception")) {
                fail("Create Quote Failed. Status: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating quote", e);
        }
    }

    public void callPayRate() {
        sessionService.checkoutRequest()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .formParam("fedexAccount", "")
                .formParam("shippingAccount", "")
                .formParam("selectedProductionId", "")
                .post(payRateEndpoint)
                .then()
                .statusCode(200);
    }

    public String submitOrder(SubmitOrderRequest request, String quoteId) {
        try {
            Map<String, String> checkoutCookies = new java.util.HashMap<>();
            checkoutCookies.put("quoteId", quoteId);

            Response response = sessionService.checkoutRequest(checkoutCookies)
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                    .queryParam("pickstore", "0")
                    .formParam("data", objectMapper.writeValueAsString(request))
                    .post(submitOrderEndpoint);

            return response.asString();
        } catch (Exception e) {
            throw new RuntimeException("Error submitting order", e);
        }
    }

    public String fetchEncryptionKey() {
        return sessionService.checkoutRequest()
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .get(encryptionKeyEndpoint)
                .jsonPath().getString("encryption.key");
    }

    private String mapToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}