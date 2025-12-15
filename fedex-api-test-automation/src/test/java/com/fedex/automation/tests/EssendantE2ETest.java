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

import java.net.URLDecoder;
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

    // --- Test Data Constants ---
    private static final String STREET_LINE_1 = "550 PEACHTREE ST NE";
    private static final String STREET_LINE_2 = "";
    private static final String CITY = "Los Angeles";
    private static final String REGION_ID = "34";
    private static final String REGION_CODE = "CA";
    private static final String COUNTRY_ID = "US";
    private static final String POSTCODE = "90002";
    private static final String FIRST_NAME = "Diogo";
    private static final String LAST_NAME = "Pereira";
    private static final String COMPANY = "";
    private static final String TELEPHONE = "4247021234";
    private static final String TELEPHONE_EXT = "";
    private static final String EMAIL_ID = "dpereira@mcfadyen.com";
    private static final String RESIDENCE_SHIPPING_LABEL = "No";
    private static final boolean IS_RESIDENCE_SHIPPING = false;

    private static final String PREFERRED_THIRD_PARTY_CARRIER_CODE = "marketplace_2941";
    private static final String PREFERRED_METHOD_CODE = "FREE_GROUND_US";

    private static final String PAYMENT_METHOD = "cc";
    private static final String CARD_YEAR = "2035";
    private static final String CARD_EXPIRE = "02";
    private static final String NAME_ON_CARD = "DIOGO";
    private static final String RAW_CARD_NUMBER = "4111111111111111"; // Real PAN for encryption
    private static final String MASKED_CARD_NUMBER = "*1111";       // Masked for payload
    private static final String CVV = "345";
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

        // ASSERTIONS STEP 1
        assertEquals(302, addResponse.statusCode(), "Add to cart should redirect (302)");
        log.info("Item added to cart successfully.");

        // --- Step 2: Scrape Cart ---
        log.info("--- [Step 2] Scrape Cart ---");
        CartContext cartData = scrapeCartContext(sku);

        // ASSERTIONS STEP 2
        assertNotNull(cartData, "Cart context should not be null");
        assertEquals(1, cartData.getQty(), "Cart quantity should be 1");
        assertNotNull(cartData.getQuoteId(), "Quote ID must be present in cart data");
        log.info("Cart scraped. Item ID: {}, Quote ID: {}", cartData.getItemId(), cartData.getQuoteId());

        // --- Step 3: Estimate Shipping ---
        log.info("--- [Step 3] Estimate Shipping ---");
        String dynamicEstimateUrl = estimateEndpoint.replace("{cartId}", cartData.getMaskedQuoteId());

        Address address = buildAddress();
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

        EstimateShipMethodResponse[] shippingMethods = estimateRawResponse.as(EstimateShipMethodResponse[].class);

        // ASSERTIONS STEP 3
        assertNotNull(shippingMethods, "Estimate response should not be null");
        assertTrue(shippingMethods.length > 0, "Estimate should return at least one method");

        EstimateShipMethodResponse chosenMethod = selectPreferredMethod(shippingMethods);
        log.info("Selected shipping method: {} ({})", chosenMethod.getMethodCode(), chosenMethod.getCarrierCode());

        // Ensure we actually found the method we wanted to test
        assertEquals(PREFERRED_METHOD_CODE, chosenMethod.getMethodCode(), "Should have selected the preferred shipping method");

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

        // ASSERTIONS STEP 4
        assertNotNull(rateQuoteNode, "Step 4 must return 'rateQuote' object");
        JsonNode firstQuoteDetail = rateQuoteNode.path("rateQuoteDetails").get(0);
        double totalAmount = firstQuoteDetail.path("totalAmount").asDouble();
        assertTrue(totalAmount > 0, "Total amount should be greater than 0");
        log.info("Delivery rate verified. Total Amount: {}", totalAmount);

        // --- Step 5: Create Quote ---
        log.info("--- [Step 5] Create Quote ---");
        String rateQuoteString = objectMapper.writeValueAsString(rateQuoteNode);
        JsonNode shippingDetailNode = objectMapper.readTree(shipMethodDataJson);

        Map<String, Object> shippingAddressMap = buildQuoteAddress(false);
        Map<String, Object> billingAddressMap = buildQuoteAddress(true);

        Map<String, Object> addressInformation = new LinkedHashMap<>();
        addressInformation.put("shipping_address", shippingAddressMap);
        addressInformation.put("billing_address", billingAddressMap);
        addressInformation.put("shipping_method_code", chosenMethod.getMethodCode());
        addressInformation.put("shipping_carrier_code", chosenMethod.getCarrierCode());
        addressInformation.put("shipping_detail", objectMapper.convertValue(shippingDetailNode, Map.class));

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

        // ASSERTIONS STEP 5
        String quoteRespString = createQuoteResponse.asString();
        assertFalse(quoteRespString.toLowerCase().contains("exception"), "Quote creation should not return exceptions");
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

        // ASSERTIONS STEP 6
        assertNotNull(payRateResponse.body(), "PayRate response should not be null");
        log.info("Pay Rate API called successfully.");

        // --- Step 7: Submit Order ---
        log.info("--- [Step 7] Submit Order ---");

        // 1. FETCH DYNAMIC KEY
        String publicKeyPEM = fetchEncryptionKey();

        // 2. ENCRYPT
        String encryptedButEncoded = FedExEncryptionUtil.encryptCreditCard(
                RAW_CARD_NUMBER, CARD_EXPIRE, CARD_YEAR, CVV, publicKeyPEM
        );

        // 3. FIX: DECODE IT BACK TO RAW
        String rawEncryptedData = URLDecoder.decode(encryptedButEncoded, StandardCharsets.UTF_8);
        log.info("FIXED Raw Data: {}", rawEncryptedData);

        // 4. BUILD PAYLOAD
        Map<String, Object> paymentBillingAddress = buildPaymentBillingAddress();
        Map<String, Object> paymentData = new LinkedHashMap<>();
        paymentData.put("loginValidationKey", "");
        paymentData.put("paymentMethod", PAYMENT_METHOD);
        paymentData.put("year", CARD_YEAR);
        paymentData.put("expire", CARD_EXPIRE);
        paymentData.put("nameOnCard", NAME_ON_CARD);
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
        submitPayload.put("encCCData", rawEncryptedData);
        submitPayload.put("pickupData", null);
        submitPayload.put("useSiteCreditCard", false);
        submitPayload.put("selectedProductionId", null);
        submitPayload.put("g-recaptcha-response", "");

        String jsonData = objectMapper.writeValueAsString(submitPayload);

        // 5. SEND REQUEST
        Response submitOrderResponse = givenWithSession()
                .cookie("quoteId", cartData.getQuoteId()) // Explicitly inject Quote ID cookie
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .header("Origin", baseUrl)
                .queryParam("pickstore", "0")
                .formParam("data", jsonData)
                .post(submitOrderEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        String submitBody = submitOrderResponse.asString();
        log.info("Step 7 response received. Length: {}", submitBody.length());

        // --- FINAL VALIDATION ---

        // 1. Parse Outer JSON
        JsonNode outerRoot = objectMapper.readTree(submitBody);

        // 2. Check for "unified_data_layer" (Easiest way to verify Order Number)
        JsonNode dataLayer = outerRoot.get("unified_data_layer");
        assertNotNull(dataLayer, "Response must contain 'unified_data_layer'");

        String orderNumber = dataLayer.path("orderNumber").asText();
        assertNotNull(orderNumber, "Order number must be present");
        assertFalse(orderNumber.isEmpty(), "Order number must not be empty");
        log.info("SUCCESS! Order Placed. Order Number: {}", orderNumber);

        // 3. Verify Payment Authorization in the nested JSON string
        // The server response structure is: { "0": { "0": "{\"transactionId\"...}" } }
        String innerJsonString = outerRoot.path("0").path("0").asText();
        assertNotNull(innerJsonString, "Inner Order JSON string should exist");

        JsonNode innerRoot = objectMapper.readTree(innerJsonString);
        String authResponse = innerRoot.path("output")
                .path("checkout")
                .path("tenders")
                .get(0)
                .path("creditCard")
                .path("authResponse")
                .asText();

        assertEquals("APPROVED", authResponse, "Payment must be APPROVED");

        log.info("Payment Authorized: YES");
    }

    // --- Helper Methods ---

    private Address buildAddress() {
        return Address.builder()
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
                        CustomAttribute.builder().attributeCode("residence_shipping").value(IS_RESIDENCE_SHIPPING).label(RESIDENCE_SHIPPING_LABEL).build()
                ))
                .build();
    }

    private EstimateShipMethodResponse selectPreferredMethod(EstimateShipMethodResponse[] methods) {
        Optional<EstimateShipMethodResponse> exact = Arrays.stream(methods)
                .filter(m -> PREFERRED_METHOD_CODE.equals(m.getMethodCode()))
                .filter(m -> PREFERRED_THIRD_PARTY_CARRIER_CODE.equals(m.getCarrierCode()))
                .findFirst();
        return exact.orElse(Arrays.stream(methods)
                .filter(m -> PREFERRED_METHOD_CODE.equals(m.getMethodCode()))
                .findFirst()
                .orElse(methods[0]));
    }

    private String extractShipMethodDataJson(Response estimateRaw, EstimateShipMethodResponse chosen) throws Exception {
        JsonNode root = objectMapper.readTree(estimateRaw.asString());
        if (root != null && root.isArray()) {
            for (JsonNode n : root) {
                String method = n.path("method_code").asText(null);
                String carrier = n.path("carrier_code").asText(null);
                if (Objects.equals(method, chosen.getMethodCode()) && Objects.equals(carrier, chosen.getCarrierCode())) {
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