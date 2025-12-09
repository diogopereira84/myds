package com.fedex.automation.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fedex.automation.base.BaseTest;
import com.fedex.automation.model.*;
import com.fedex.automation.model.EstimateShippingRequest.CustomAttribute;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import com.fedex.automation.utils.FedExEncryptionUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.restassured.RestAssured.given;
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
    private static final String RESIDENCE_SHIPPING_LABEL = "No";
    private static final boolean IS_RESIDENCE_SHIPPING = false;

    // Alinhado com UI
    private static final String PREFERRED_THIRD_PARTY_CARRIER_CODE = "marketplace_2941";
    private static final String PREFERRED_METHOD_CODE = "FREE_GROUND_US";

    // Dados "dummy" do cartão (como no curl)
    private static final String PAYMENT_METHOD = "cc";
    private static final String CARD_YEAR = "2035";
    private static final String CARD_EXPIRE = "02";
    private static final String NAME_ON_CARD = "RALPH";
    private static final String MASKED_NUMBER = "4111111111111111";
    private static final String CVV = "471";
    private static final String CREDIT_CARD_TYPE_URL = "https://dev.office.fedex.com/media/wysiwyg/Visa.png";

    @Test
    public void testEssendantAddUpdateCheckoutFlow() throws Exception {
        // --- Step 0: Bootstrap ---
        bootstrapSession();

        // --- Step 1: Add to Cart ---
        log.info("--- [Step 1] Add to Cart ---");
        Map<String, String> addParams = new HashMap<>();
        addParams.put("form_key", formKey);
        addParams.put("sku", sku);
        addParams.put("qty", "1");
        addParams.put("offer_id", offerId);
        addParams.put("punchout_disabled", "1");
        addParams.put("super_attribute", "");

        given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(addParams)
                .post(addEndpoint)
                .then()
                .statusCode(302);

        // --- Step 2: Scrape Cart ---
        log.info("--- [Step 2] Scrape Cart ---");
        CartContext cartData = scrapeCartContext(sku);
        assertEquals(1, cartData.getQty());

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
                                .value(IS_RESIDENCE_SHIPPING)
                                .label(RESIDENCE_SHIPPING_LABEL)
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

        Response estimateRawResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
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

        EstimateShipMethodResponse chosenMethod =
                selectPreferredMethod(shippingMethods);

        // Pega o nó original do método no JSON (mais fiel à UI)
        String shipMethodDataJson =
                extractShipMethodDataJson(estimateRawResponse, chosenMethod);

        // --- Step 4: Delivery Rate API ---
        log.info("--- [Step 4] Delivery Rate API ---");

        Response rawRateResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
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

        log.debug("Step 4 response:\n{}", rawRateResponse.asPrettyString());

        // --- Step 5: Create Quote ---
        log.info("--- [Step 5] Create Quote ---");

        JsonNode step4Root = objectMapper.readTree(rawRateResponse.asString());
        JsonNode rateQuoteNode = step4Root.get("rateQuote");
        assertNotNull(rateQuoteNode, "Step 4 must return rateQuote");

        String rateQuoteString = objectMapper.writeValueAsString(rateQuoteNode);

        // shipping_detail vem do ship_method_data (UI)
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

        Response createQuoteResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
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

        log.debug("Step 5 response:\n{}", createQuoteResponse.asPrettyString());

        log.info("--- [Step 6] Pay Rate API ---");

        Response payRateResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
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

        log.debug("Step 6 response:\n{}", payRateResponse.asPrettyString());

        log.info("--- [Step 7] Submit Order ---");

        // 1. Fetch Key
        String publicKeyPEM = fetchEncryptionKey();

        // 2. Encrypt (Generates Standard Base64: "abc+123/==")
        String rawEncCCData = com.fedex.automation.utils.FedExEncryptionUtil.encryptCreditCard(
                MASKED_NUMBER,
                CARD_EXPIRE,
                CARD_YEAR,
                CVV,
                publicKeyPEM
        );

        // --- CRITICAL FIX: URL ENCODE THE OUTPUT ---
        // Turns "abc+123/==" into "abc%2B123%2F%3D%3D"
        String finalEncCCData = URLEncoder.encode(rawEncCCData, StandardCharsets.UTF_8);

        Map<String, Object> paymentBillingAddress = buildPaymentBillingAddress();

        Map<String, Object> paymentData = new LinkedHashMap<>();
        paymentData.put("loginValidationKey", "");
        paymentData.put("paymentMethod", PAYMENT_METHOD);
        paymentData.put("year", CARD_YEAR);
        paymentData.put("expire", CARD_EXPIRE);
        paymentData.put("nameOnCard", NAME_ON_CARD);
        paymentData.put("number", MASKED_NUMBER);
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

        Response submitOrderResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .queryParam("pickstore", "0")
                .formParam("data", objectMapper.writeValueAsString(submitPayload))
                .post(submitOrderEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        log.debug("Step 7 response:\n{}", submitOrderResponse.asPrettyString());
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

    /**
     * Retorna o JSON "original" do método escolhido direto do array do response,
     * evitando diferenças de serialização entre POJO vs UI.
     */
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

        // fallback seguro
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

        // campos alternates como na UI
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
        // Estrutura do billingAddress dentro do paymentData (Step 7),
        // que inclui address/addressTwo além dos campos comuns. :contentReference[oaicite:6]{index=6}
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
