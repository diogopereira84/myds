package com.fedex.automation.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fedex.automation.base.BaseTest;
import com.fedex.automation.model.*;
import com.fedex.automation.model.EstimateShippingRequest.CustomAttribute;
import com.fedex.automation.utils.FedExEncryptionUtil;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class EssendantE2ETest extends BaseTest {

    @Value("${test.product.sku}")
    private String sku;

    @Value("${test.product.offer_id}")
    private String offerId;

    @Value("${endpoint.cart.add}")
    private String addEndpoint;

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

    private static final String STREET_LINE_1 = "550 PEACHTREE ST NE";
    private static final String STREET_LINE_2 = "";
    private static final String CITY = "ATLANTA";
    private static final String REGION_ID = "55";
    private static final String REGION_CODE = "GA";
    private static final String COUNTRY_ID = "US";
    private static final String POSTCODE = "30308";
    private static final String FIRST_NAME = "Diogo";
    private static final String LAST_NAME = "Pereira";
    private static final String COMPANY = "";
    private static final String TELEPHONE = "5524654547";
    private static final String TELEPHONE_EXT = "";
    private static final String EMAIL_ID = "dpereira@mcfadyen.com";

    // --- FIX 1: Align Residence Flags with UI (Must be TRUE/YES for this test context) ---
    private static final String RESIDENCE_SHIPPING_LABEL = "Yes";
    private static final boolean IS_RESIDENCE_SHIPPING = true;

    // Aligned with UI
    private static final String PREFERRED_THIRD_PARTY_CARRIER_CODE = "marketplace_2941";
    private static final String PREFERRED_METHOD_CODE = "FREE_GROUND_US";

    // Dummy card data (as in UI)
    private static final String PAYMENT_METHOD = "cc";
    private static final String CARD_YEAR = "2035";
    private static final String CARD_EXPIRE = "02";
    private static final String NAME_ON_CARD = "RALPH";

    // Raw number for encryption only
    private static final String RAW_CARD_NUMBER = "4111111111111111";
    // --- FIX 2: Masked number for the JSON payload ---
    private static final String MASKED_CARD_NUMBER = "*1111";

    private static final String CVV = "471";
    private static final String CREDIT_CARD_TYPE_URL = "https://dev.office.fedex.com/media/wysiwyg/Visa.png";

    @Test
    public void testEssendantAddUpdateCheckoutFlow() throws Exception {
        // --- Step 0: Bootstrap ---
        bootstrapSession();
        assertNotNull(formKey, "Form key must be available after bootstrap");

        // --- Step 1: Add to Cart ---
        log.info("--- [Step 1] Add to Cart ---");
        Map<String, String> addParams = new HashMap<>();
        addParams.put("form_key", formKey);
        addParams.put("sku", sku);
        addParams.put("qty", "1");
        addParams.put("offer_id", offerId);
        addParams.put("punchout_disabled", "1");
        addParams.put("super_attribute", "");

        Response addResponse = givenWithSession()
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(addParams)
                .post(addEndpoint)
                .then()
                .extract().response();

        assertEquals(302, addResponse.statusCode(), "Add to cart should redirect (302)");
        log.info("Item added to cart successfully. Status: {}", addResponse.statusCode());

        // --- Step 2: Scrape Cart ---
        log.info("--- [Step 2] Scrape Cart ---");
        CartContext cartData = scrapeCartContext(sku);

        assertNotNull(cartData, "Cart context should not be null");
        assertEquals(1, cartData.getQty(), "Cart quantity should be 1");
        log.info("Cart scraped. Item ID: {}, Quote ID: {}", cartData.getItemId(), cartData.getQuoteId());

        // --- Step 3: Estimate Shipping ---
        log.info("--- [Step 3] Estimate Shipping ---");
        String dynamicEstimateUrl = estimateEndpoint.replace("{cartId}", cartData.getMaskedQuoteId());

        Address address = Address.builder()
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .city(CITY)
                .regionId(REGION_ID)
                .region(REGION_CODE)
                .countryId(COUNTRY_ID)
                .postcode(POSTCODE)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .company(COMPANY)
                .telephone(TELEPHONE)
                .customAttributes(Arrays.asList(
                        CustomAttribute.builder().attributeCode("email_id").value(EMAIL_ID).build(),
                        CustomAttribute.builder().attributeCode("ext").value(TELEPHONE_EXT).build(),
                        CustomAttribute.builder()
                                .attributeCode("residence_shipping")
                                .value(IS_RESIDENCE_SHIPPING) // Uses TRUE
                                .label(RESIDENCE_SHIPPING_LABEL) // Uses "Yes"
                                .build()
                ))
                .build();

        EstimateShippingRequest estimateRequest = EstimateShippingRequest.builder()
                .pickup(false)
                .isPickup(false)
                .reRate(true)
                .productionLocation(null)
                .address(address)
                .build();

        Response estimateRawResponse = givenWithSession()
                .contentType(ContentType.JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .body(estimateRequest)
                .post(dynamicEstimateUrl)
                .then()
                .statusCode(200)
                .extract()
                .response();

        EstimateShipMethodResponse[] shippingMethods =
                estimateRawResponse.as(EstimateShipMethodResponse[].class);

        assertNotNull(shippingMethods, "Estimate response should not be null");
        assertTrue(shippingMethods.length > 0, "Estimate should return at least one method");
        log.info("Shipping methods estimated: {}", shippingMethods.length);

        EstimateShipMethodResponse chosenMethod = selectPreferredMethod(shippingMethods);
        log.info("Selected shipping method: {} ({})", chosenMethod.getMethodCode(), chosenMethod.getCarrierCode());

        String shipMethodDataJson = extractShipMethodDataJson(estimateRawResponse, chosenMethod);

        // --- Step 4: Delivery Rate API ---
        log.info("--- [Step 4] Delivery Rate API ---");

        Response rawRateResponse = givenWithSession()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .header("Accept", "*/*")
                .formParam("firstname", FIRST_NAME)
                .formParam("lastname", LAST_NAME)
                .formParam("email", EMAIL_ID)
                .formParam("telephone", TELEPHONE)
                .formParam("ship_method", chosenMethod.getMethodCode())
                .formParam("zipcode", POSTCODE)
                .formParam("region_id", REGION_ID)
                .formParam("city", CITY)
                .formParam("street[]", STREET_LINE_1, STREET_LINE_2)
                .formParam("company", COMPANY)
                .formParam("is_residence_shipping", String.valueOf(IS_RESIDENCE_SHIPPING))
                .formParam("ship_method_data", shipMethodDataJson)
                .formParam("third_party_carrier_code", chosenMethod.getCarrierCode())
                .formParam("third_party_method_code", chosenMethod.getMethodCode())
                .formParam("first_party_carrier_code", "")
                .formParam("first_party_method_code", "")
                .formParam("location_id", "")
                .post(deliveryRateEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        JsonNode step4Root = objectMapper.readTree(rawRateResponse.asString());
        JsonNode rateQuoteNode = step4Root.get("rateQuote");
        assertNotNull(rateQuoteNode, "Step 4 must return 'rateQuote' object");
        log.info("Delivery rate verified.");

        // --- Step 5: Create Quote ---
        log.info("--- [Step 5] Create Quote ---");
        String rateQuoteString = objectMapper.writeValueAsString(rateQuoteNode);
        JsonNode shippingDetailNode = objectMapper.readTree(shipMethodDataJson);

        Map<String, Object> shippingAddress = buildQuoteAddress(false);
        Map<String, Object> billingAddress = buildQuoteAddress(true);

        Map<String, Object> addressInformation = new LinkedHashMap<>();
        addressInformation.put("shipping_address", shippingAddress);
        addressInformation.put("billing_address", billingAddress);
        addressInformation.put("shipping_method_code", chosenMethod.getMethodCode());
        addressInformation.put("shipping_carrier_code", chosenMethod.getCarrierCode());
        addressInformation.put("shipping_detail",
                objectMapper.convertValue(shippingDetailNode, Map.class));

        Map<String, Object> quotePayload = new LinkedHashMap<>();
        quotePayload.put("addressInformation", addressInformation);
        quotePayload.put("rateapi_response", rateQuoteString);

        Response createQuoteResponse = givenWithSession()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .header("Accept", "*/*")
                .formParam("data", objectMapper.writeValueAsString(quotePayload))
                .post(createQuoteEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        String quoteRespString = createQuoteResponse.asString();
        assertFalse(quoteRespString.toLowerCase().contains("exception"),
                "Quote creation should not return exceptions");
        log.info("Quote successfully created/updated.");

        // --- Step 6: Pay Rate API ---
        log.info("--- [Step 6] Pay Rate API ---");

        Response payRateResponse = givenWithSession()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .header("Accept", "*/*")
                .formParam("fedexAccount", "")
                .formParam("shippingAccount", "")
                .formParam("selectedProductionId", "")
                .post(payRateEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        assertNotNull(payRateResponse.body(), "PayRate response should not be null");
        log.info("Pay Rate API called successfully.");

        // --- Step 7: Submit Order ---
        log.info("--- [Step 7] Submit Order ---");

        String publicKeyPEM = fetchEncryptionKey();
        assertNotNull(publicKeyPEM, "Public key must be fetched for encryption");

        // --- CRITICAL: Encrypt using the RAW card number ---
        String rawEncCCData = FedExEncryptionUtil.encryptCreditCard(
                RAW_CARD_NUMBER,
                CARD_EXPIRE,
                CARD_YEAR,
                CVV,
                publicKeyPEM
        );

        // URL-encode the standard Base64 output so it matches the UI format
        String finalEncCCData = URLEncoder.encode(rawEncCCData, StandardCharsets.UTF_8);

        Map<String, Object> paymentBillingAddress = buildPaymentBillingAddress();

        Map<String, Object> paymentData = new LinkedHashMap<>();
        paymentData.put("loginValidationKey", "");
        paymentData.put("paymentMethod", PAYMENT_METHOD);
        paymentData.put("year", CARD_YEAR);
        paymentData.put("expire", CARD_EXPIRE);
        paymentData.put("nameOnCard", NAME_ON_CARD);

        // --- CRITICAL: Payload uses MASKED number ---
        paymentData.put("number", MASKED_CARD_NUMBER);

        paymentData.put("cvv", CVV);
        paymentData.put("isBillingAddress", false);
        paymentData.put("isFedexAccountApplied", false);
        paymentData.put("fedexAccountNumber", null);
        paymentData.put("creditCardType", CREDIT_CARD_TYPE_URL);
        paymentData.put("billingAddress", paymentBillingAddress);

        String paymentDataString = objectMapper.writeValueAsString(paymentData);

        Map<String, Object> submitPayload = new LinkedHashMap<>();
        submitPayload.put("paymentData", paymentDataString);
        submitPayload.put("encCCData", finalEncCCData);
        submitPayload.put("pickupData", null);
        submitPayload.put("useSiteCreditCard", false);
        submitPayload.put("selectedProductionId", null);
        submitPayload.put("g-recaptcha-response", "");

        Response submitOrderResponse = givenWithSession()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", baseUrl)
                .header("Referer", baseUrl + "/default/checkout")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .queryParam("pickstore", "0")
                .formParam("data", objectMapper.writeValueAsString(submitPayload))
                .post(submitOrderEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        log.info("Step 7 response:\n{}", submitOrderResponse.asPrettyString());

        String submitBody = submitOrderResponse.asString();
        assertFalse(submitBody.contains("\"error\":true"), "Order submission returned an error");
        assertFalse(submitBody.contains("\"success\":false"), "Order submission success flag is false");
        log.info("Order submitted successfully. Response length: {}", submitBody.length());
    }

    private EstimateShipMethodResponse selectPreferredMethod(EstimateShipMethodResponse[] methods) {
        Optional<EstimateShipMethodResponse> exact =
                Arrays.stream(methods)
                        .filter(m -> PREFERRED_METHOD_CODE.equals(m.getMethodCode()))
                        .filter(m -> PREFERRED_THIRD_PARTY_CARRIER_CODE.equals(m.getCarrierCode()))
                        .findFirst();

        if (exact.isPresent()) return exact.get();

        Optional<EstimateShipMethodResponse> byMethod =
                Arrays.stream(methods)
                        .filter(m -> PREFERRED_METHOD_CODE.equals(m.getMethodCode()))
                        .findFirst();

        return byMethod.orElse(methods[0]);
    }

    private String extractShipMethodDataJson(Response estimateRaw, EstimateShipMethodResponse chosen) throws Exception {
        JsonNode root = objectMapper.readTree(estimateRaw.asString());

        if (root != null && root.isArray()) {
            for (JsonNode n : root) {
                String method = n.path("method_code").asText(null);
                String carrier = n.path("carrier_code").asText(null);

                if (Objects.equals(method, chosen.getMethodCode())
                        && Objects.equals(carrier, chosen.getCarrierCode())) {
                    return objectMapper.writeValueAsString(n);
                }
            }
        }

        return objectMapper.writeValueAsString(chosen);
    }

    private Map<String, Object> buildQuoteAddress(boolean isBilling) {
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("countryId", COUNTRY_ID);
        addr.put("regionId", REGION_ID);
        addr.put("regionCode", REGION_CODE);
        addr.put("region", REGION_CODE);
        addr.put("street", Arrays.asList(STREET_LINE_1, STREET_LINE_2));
        addr.put("company", COMPANY);
        addr.put("telephone", TELEPHONE);
        addr.put("postcode", POSTCODE);
        addr.put("city", CITY);
        addr.put("firstname", FIRST_NAME);
        addr.put("lastname", LAST_NAME);

        List<Map<String, Object>> customAttributes = new ArrayList<>();
        customAttributes.add(attr("email_id", EMAIL_ID));
        customAttributes.add(attr("ext", TELEPHONE_EXT));
        customAttributes.add(attr("residence_shipping", IS_RESIDENCE_SHIPPING, RESIDENCE_SHIPPING_LABEL));
        addr.put("customAttributes", customAttributes);

        addr.put("altFirstName", "");
        addr.put("altLastName", "");
        addr.put("altPhoneNumber", "");
        addr.put("altEmail", "");
        addr.put("altPhoneNumberext", "");
        addr.put("is_alternate", false);

        if (isBilling) {
            addr.put("saveInAddressBook", null);
        }

        return addr;
    }

    private Map<String, Object> buildPaymentBillingAddress() {
        Map<String, Object> addr = buildQuoteAddress(false);
        addr.put("address", STREET_LINE_1);
        addr.put("addressTwo", STREET_LINE_2);
        return addr;
    }

    private Map<String, Object> attr(String code, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attribute_code", code);
        m.put("value", value);
        return m;
    }

    private Map<String, Object> attr(String code, Object value, String label) {
        Map<String, Object> m = attr(code, value);
        m.put("label", label);
        return m;
    }
}