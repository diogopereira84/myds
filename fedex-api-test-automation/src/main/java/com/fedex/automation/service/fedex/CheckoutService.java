package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Value("${base.url}")
    private String baseUrl;

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

    public EstimateShipMethodResponse[] estimateShipping(String maskedQuoteId, EstimateShippingRequest request) {
        Objects.requireNonNull(maskedQuoteId, "Masked Quote ID cannot be null. Check 'Scrape Cart' step.");
        log.info("Estimating Shipping Methods for Quote: {}", maskedQuoteId);

        String url = estimateEndpoint.replace("{cartId}", maskedQuoteId);
        return sessionService.authenticatedRequest()
                .contentType(ContentType.JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .body(request)
                .post(url)
                .then()
                .statusCode(200)
                .extract().as(EstimateShipMethodResponse[].class);
    }

    public JsonNode getDeliveryRate(DeliveryRateRequestForm form) {
        // FIX: safely access carrier code from nested object for logging
        String carrier = (form.getShipMethodData() != null) ? form.getShipMethodData().getCarrierCode() : "Unknown";
        String method = (form.getShipMethodData() != null) ? form.getShipMethodData().getMethodCode() : "Unknown";

        log.info("Retrieving Delivery Rate for Carrier: {}, Method: {}", carrier, method);

        Response response = sessionService.authenticatedRequest()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
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

    // FIX: Method signature now accepts CreateQuotePayload object
    public void createQuote(CreateQuotePayload quotePayload) {
        log.info("Creating Pricing Quote...");
        try {
            Response response = sessionService.authenticatedRequest()
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", baseUrl + "/default/checkout")
                    .formParam("data", objectMapper.writeValueAsString(quotePayload))
                    .post(createQuoteEndpoint);

            String body = response.asString();
            if (response.statusCode() != 200 || body.toLowerCase().contains("exception")) {
                fail("Create Quote Failed. Status: " + response.statusCode() + ", Body: " + body);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating quote", e);
        }
    }

    public void callPayRate() {
        sessionService.authenticatedRequest()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .formParam("fedexAccount", "")
                .formParam("shippingAccount", "")
                .formParam("selectedProductionId", "")
                .post(payRateEndpoint)
                .then()
                .statusCode(200);
    }

    public String submitOrder(SubmitOrderRequest request, String quoteId) {
        log.info("Submitting Order for QuoteID: {}", quoteId);
        try {
            String jsonData = objectMapper.writeValueAsString(request);

            // Pass quoteId safely into our single Cookie Header builder
            Map<String, String> checkoutCookies = new java.util.HashMap<>();
            checkoutCookies.put("quoteId", quoteId);

            Response response = sessionService.authenticatedRequest(checkoutCookies)
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", baseUrl + "/default/checkout")
                    .queryParam("pickstore", "0")
                    .formParam("data", jsonData)
                    .post(submitOrderEndpoint);

            return response.asString();
        } catch (Exception e) {
            throw new RuntimeException("Error submitting order", e);
        }
    }

    public String fetchEncryptionKey() {
        return sessionService.authenticatedRequest()
                .header("X-Requested-With", "XMLHttpRequest")
                .get("/default/delivery/index/encryptionkey")
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