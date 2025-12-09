package com.fedex.automation.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.base.BaseTest;
import com.fedex.automation.model.*;
import com.fedex.automation.model.EstimateShippingRequest.CustomAttribute;
import com.fedex.automation.model.ShipMethodData.ExtensionAttributes;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

    // Recipient information
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

    @Test
    public void testEssendantAddUpdateCheckoutFlow() throws Exception {
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
                .filter(new CurlLoggingFilter()) // LOG CURL
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
        assertNotNull(cartData.getMaskedQuoteId(), "Masked Quote ID not found!");

        // --- Step 3: Estimate Shipping (REST) ---
        log.info("--- [Step 3] Estimate Shipping (REST) ---");
        String dynamicEstimateUrl = estimateEndpoint.replace("{cartId}", cartData.getMaskedQuoteId());

        var address = Address.builder()
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
                ));

        EstimateShippingRequest estimateRequest = EstimateShippingRequest.builder()
                .productionLocation(null)
                .isPickup(false)
                .reRate(true)
                .address(address.build())
                .build();

        // Now safe to use ShipMethodData[] class because we added constructors to the Model
        ShipMethodData[] shippingMethods = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter()) // LOG CURL
                .contentType(ContentType.JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .body(estimateRequest)
                .post(dynamicEstimateUrl)
                .then()
                .statusCode(200)
                .extract().as(ShipMethodData[].class);

        assertTrue(shippingMethods.length > 0, "No shipping methods returned from estimate API");

        // Extract the first method to use in Step 4
        ShipMethodData firstMethod = shippingMethods[0];
        log.info("Using Shipping Method: {} - {}", firstMethod.getCarrierTitle(), firstMethod.getMethodTitle());

        // --- Step 4: Delivery Rate API (Controller) ---
        log.info("--- [Step 4] Delivery Rate API (Controller) ---");

        ShipMethodData shipMethodData = ShipMethodData.builder()
                .carrierCode(firstMethod.getCarrierCode())
                .methodCode(firstMethod.getMethodCode())
                .carrierTitle(firstMethod.getCarrierTitle())
                .methodTitle(firstMethod.getMethodTitle())
                .offerId(firstMethod.getOfferId())
                .title(firstMethod.getCarrierTitle())
                .selected(firstMethod.getCarrierCode() + "_" + firstMethod.getMethodCode())
                .itemId(cartData.getItemId())
                .shippingTypeLabel(firstMethod.getMethodTitle())
                .marketplace(firstMethod.getMarketplace())
                .sellerId(firstMethod.getSellerId())
                .amount(firstMethod.getAmount())
                .baseAmount(firstMethod.getBaseAmount())
                .available(firstMethod.getAvailable())
                .priceInclTax(firstMethod.getPriceInclTax())
                .priceExclTax(firstMethod.getPriceExclTax())
                .selectedCode(firstMethod.getMethodCode())
                .deliveryDate(firstMethod.getDeliveryDate())
                .deliveryDateText(firstMethod.getDeliveryDateText())
                .sellerName(firstMethod.getSellerName())
                .surchargeAmount(firstMethod.getSurchargeAmount())
                .extensionAttributes(ExtensionAttributes.builder().fastest(true).cheapest(true).build())
                .address(address.build())
                .build();

        String shipMethodDataJson = objectMapper.writeValueAsString(shipMethodData);

        Map<String, String> formParams = getStringStringMap(firstMethod, shipMethodDataJson);

        ObjectMapper mapper = new ObjectMapper();

        Response raw = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .formParams(formParams)
                .post(deliveryRateEndpoint)
                .then()
                .log().ifError()
                .statusCode(200)
                .extract()
                .response();

        String rawJson = raw.asString();
        JsonNode tree = mapper.readTree(rawJson);
        String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);

        log.info("Full response:\n{}", pretty);

        RateQuoteResponse response = mapper.treeToValue(tree, RateQuoteResponse.class);

        // --- Assertions ---
        assertNotNull(response.getRateQuote(), "Rate Quote object is null");
        assertFalse(response.getRateQuote().getRateQuoteDetails().isEmpty(), "No Rate Quote Details found");

        RateQuoteResponse.ProductLine productLine = response.getRateQuote()
                .getRateQuoteDetails().getFirst()
                .getProductLines().getFirst();

        log.info("Validating Product Line: '{}'", productLine.getName());
        assertEquals(1, productLine.getUnitQuantity());
        log.info("TEST PASSED: Delivery Rate API returned correct quantity.");
    }

    private static Map<String, String> getStringStringMap(ShipMethodData firstMethod, String shipMethodDataJson) {
        Map<String, String> formParams = new HashMap<>();
        formParams.put("firstname", FIRST_NAME);
        formParams.put("lastname", LAST_NAME);
        formParams.put("email", EMAIL_ID);
        formParams.put("telephone", TELEPHONE);
        formParams.put("ship_method", firstMethod.getMethodCode());
        formParams.put("zipcode", POSTCODE);
        formParams.put("region_id", REGION_ID);
        formParams.put("city", CITY);
        formParams.put("street[]", STREET_LINE_1);
        formParams.put("is_residence_shipping", String.valueOf(IS_RESIDENCE_SHIPPING));
        formParams.put("ship_method_data", shipMethodDataJson);
        formParams.put("third_party_carrier_code", firstMethod.getCarrierCode());
        formParams.put("third_party_method_code", firstMethod.getMethodCode());
        formParams.put("first_party_carrier_code", "");
        formParams.put("first_party_method_code", "");
        formParams.put("location_id", "");
        return formParams;
    }
}