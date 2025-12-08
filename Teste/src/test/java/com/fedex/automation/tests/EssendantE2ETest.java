package com.fedex.automation.tests;

import com.fedex.automation.base.BaseTest;
import com.fedex.automation.model.CartContext;
import com.fedex.automation.model.EstimateShippingRequest;
import com.fedex.automation.model.RateQuoteResponse;
import com.fedex.automation.model.ShippingMethod;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    @Value("${endpoint.cart.update}")
    private String updateEndpoint;

    @Value("${endpoint.shipping.estimate}")
    private String estimateEndpoint;

    @Value("${endpoint.shipping.deliveryrate}")
    private String deliveryRateEndpoint;

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

        given().filter(cookieFilter).contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(addParams).post(addEndpoint).then().statusCode(302);

        // --- Step 2: Scrape Cart ---
        log.info("--- [Step 2] Scrape Cart ---");
        CartContext cartData = scrapeCartContext(sku);
        assertEquals(1, cartData.getQty());
        assertNotNull(cartData.getMaskedQuoteId(), "Masked Quote ID not found!");

/*
        // --- Step 3: Update Cart Quantity ---
        log.info("--- [Step 3] Updating Quantity to 5 ---");
        Map<String, String> updateParams = new HashMap<>();
        updateParams.put("form_key", formKey);
        updateParams.put("cart[" + cartData.getItemId() + "][qty]", "5");
        updateParams.put("update_cart_action", "update_qty");

        given()
                .filter(cookieFilter)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(updateParams)
                .when()
                .post(updateEndpoint)
                .then()
                .statusCode(302);

        CartContext updatedCart = scrapeCartContext(sku);
        assertEquals(5, updatedCart.getQty(), "Cart Update Failed!");
*/
// --- Step 5: Estimate Shipping (REST) ---
        log.info("--- [Step 3] Estimate Shipping (REST) ---");
        String dynamicEstimateUrl = estimateEndpoint.replace("{cartId}", cartData.getMaskedQuoteId());
        log.info("Dynamic EstimateUrl: {}", dynamicEstimateUrl);

        // Build the Request Object (Replacing the raw string)
        EstimateShippingRequest estimateRequest = EstimateShippingRequest.builder()
                .productionLocation(null)
                .isPickup(false)
                .reRate(true)
                .address(EstimateShippingRequest.Address.builder()
                        .street(Arrays.asList("550 PEACHTREE ST NE", ""))
                        .city("ATLANTA")
                        .regionId("55")
                        .region("GA")
                        .countryId("US")
                        .postcode("30308")
                        .firstname("Diogo")
                        .lastname("Pereira")
                        .company("")
                        .telephone("(552) 465-4547")
                        .customAttributes(Arrays.asList(
                                EstimateShippingRequest.CustomAttribute.builder()
                                        .attributeCode("email_id")
                                        .value("dpereira@mcfadyen.com")
                                        .build(),
                                EstimateShippingRequest.CustomAttribute.builder()
                                        .attributeCode("ext")
                                        .value("")
                                        .build(),
                                EstimateShippingRequest.CustomAttribute.builder()
                                        .attributeCode("residence_shipping")
                                        .value(false)
                                        .label("No")
                                        .build()
                        ))
                        .build())
                .build();

        // Execute Request
        ShippingMethod[] shippingMethods = given()
                .filter(cookieFilter) // Uses session cookies from BaseTest
                .contentType(ContentType.JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout") // Added Referer as seen in typical flows
                .body(estimateRequest) // Jackson automatically serializes this
                .post(dynamicEstimateUrl)
                .then()
                .log().ifError()
                .statusCode(200)
                .extract().as(ShippingMethod[].class);

        log.info("Estimated Shipping Methods found: {}", shippingMethods.length);

        // --- Step 4: Delivery Rate API (Controller) ---
        log.info("--- [Step 4] Delivery Rate API (Controller) ---");

        // Construct the nested JSON string for 'ship_method_data' parameter
        String shipMethodDataJson = String.format("""
            {
                "carrier_code": "marketplace_2941",
                "method_code": "FREE_GROUND_US",
                "carrier_title": "Essendant",
                "method_title": "FedEx Ground US",
                "offer_id": "2941",
                "title": "Essendant",
                "selected": "marketplace_2941_FREE_GROUND_US",
                "item_id": "%s",
                "shipping_type_label": "FedEx Ground US",
                "marketplace": true,
                "seller_id": "2021",
                "address": {
                    "countryId": "US",
                    "regionId": "55",
                    "regionCode": "GA",
                    "region": "GA",
                    "street": ["550 PEACHTREE ST NE", ""],
                    "company": "",
                    "telephone": "(552) 465-4547",
                    "postcode": "30308",
                    "city": "ATLANTA",
                    "firstname": "Diogo",
                    "lastname": "Pereira"
                }
            }
        """, cartData.getItemId()); // Dynamic Item ID injection

        // Build Form Params
        Map<String, String> formParams = new HashMap<>();
        formParams.put("firstname", "Diogo");
        formParams.put("lastname", "Pereira");
        formParams.put("email", "dpereira@mcfadyen.com");
        formParams.put("telephone", "5524654547");
        formParams.put("ship_method", "FREE_GROUND_US");
        formParams.put("zipcode", "30308");
        formParams.put("region_id", "55");
        formParams.put("city", "ATLANTA");
        formParams.put("street[]", "550 PEACHTREE ST NE");
        formParams.put("is_residence_shipping", "false");
        formParams.put("ship_method_data", shipMethodDataJson); // The massive JSON string
        formParams.put("third_party_carrier_code", "marketplace_2941");
        formParams.put("third_party_method_code", "FREE_GROUND_US");

        // Execute Request
        RateQuoteResponse response = given()
                .filter(cookieFilter)
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .formParams(formParams)
                .when()
                .post(deliveryRateEndpoint)
                .then()
                .log().ifError()
                .statusCode(200)
                .extract().as(RateQuoteResponse.class);

        // --- Assertions ---
        assertNotNull(response.getRateQuote(), "Rate Quote object is null");
        assertFalse(response.getRateQuote().getRateQuoteDetails().isEmpty(), "No Rate Quote Details found");

        // Navigate to the product line to check quantity
        RateQuoteResponse.ProductLine productLine = response.getRateQuote()
                .getRateQuoteDetails().getFirst()
                .getProductLines().getFirst();

        log.info("Validating Product Line: '{}'", productLine.getName());

        // Assert Quantity is 1 (as per the requirement to match selection)
        assertEquals(1, productLine.getUnitQuantity(), "Unit Quantity in Rate Quote mismatch!");

        log.info("TEST PASSED: Delivery Rate API returned correct quantity of 1.");
    }
}