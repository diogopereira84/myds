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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    @Value("${endpoint.quote.create}")
    private String createQuoteEndpoint;

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
                .filter(new CurlLoggingFilter())
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .formParams(addParams)
                .post(addEndpoint)
                .then()
                .statusCode(302);

        // --- Step 2: Scrape Cart ---
        CartContext cartData = scrapeCartContext(sku);
        assertEquals(1, cartData.getQty());

        // --- Step 3: Estimate Shipping ---
        log.info("--- [Step 3] Estimate Shipping ---");
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
                ))
                .build();

        EstimateShippingRequest estimateRequest = EstimateShippingRequest.builder()
                .productionLocation(null)
                .isPickup(false)
                .reRate(true)
                .address(address)
                .build();

        ShipMethodData[] shippingMethods = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .contentType(ContentType.JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .body(estimateRequest)
                .post(dynamicEstimateUrl)
                .then()
                .statusCode(200)
                .extract().as(ShipMethodData[].class);

        ShipMethodData firstMethod = shippingMethods[0];

        // --- Step 4: Delivery Rate API ---
        log.info("--- [Step 4] Delivery Rate API ---");

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
                .address(address)
                .build();

        String shipMethodDataJson = objectMapper.writeValueAsString(shipMethodData);
        Map<String, String> formParams = getStringStringMap(firstMethod, shipMethodDataJson);

        Response rawRateResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .contentType(ContentType.URLENC)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .formParams(formParams)
                .post(deliveryRateEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        // Strict extraction of the rateQuote node
        String rawRateJson = rawRateResponse.asString();
        JsonNode rootNode = objectMapper.readTree(rawRateJson);
        String rateQuoteString = objectMapper.writeValueAsString(rootNode.get("rateQuote"));

        // --- Step 5: Create Quote ---
        log.info("--- [Step 5] Create Quote ---");

        List<CreateQuotePayload.QuoteCustomAttribute> quoteCustomAttributes = new ArrayList<>();
        quoteCustomAttributes.add(CreateQuotePayload.QuoteCustomAttribute.builder().attributeCode("email_id").value(EMAIL_ID).build());
        quoteCustomAttributes.add(CreateQuotePayload.QuoteCustomAttribute.builder().attributeCode("ext").value(TELEPHONE_EXT).build());
        quoteCustomAttributes.add(CreateQuotePayload.QuoteCustomAttribute.builder().attributeCode("residence_shipping").value(IS_RESIDENCE_SHIPPING).label(RESIDENCE_SHIPPING_LABEL).build());

        // 1. Shipping Address (Uses QuoteShippingAddress -> Has 'alternate' & 'alt' fields)
        CreateQuotePayload.QuoteShippingAddress mainShippingAddress = CreateQuotePayload.QuoteShippingAddress.builder()
                .countryId(COUNTRY_ID)
                .regionId(REGION_ID)
                .regionCode(REGION_CODE)
                .region(REGION_CODE)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .company(COMPANY)
                .telephone(TELEPHONE)
                .postcode(POSTCODE)
                .city(CITY)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .customAttributes(quoteCustomAttributes)
                .altFirstName("")
                .altLastName("")
                .altPhoneNumber("")
                .altEmail("")
                .altPhoneNumberext("")
                .alternate(false)
                .build();

        // 2. Billing Address (Uses QuoteBillingAddress -> Has 'alternate' & 'saveInAddressBook')
        CreateQuotePayload.QuoteBillingAddress mainBillingAddress = CreateQuotePayload.QuoteBillingAddress.builder()
                .countryId(COUNTRY_ID)
                .regionId(REGION_ID)
                .regionCode(REGION_CODE)
                .region(REGION_CODE)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .company(COMPANY)
                .telephone(TELEPHONE)
                .postcode(POSTCODE)
                .city(CITY)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .customAttributes(quoteCustomAttributes)
                .altFirstName("")
                .altLastName("")
                .altPhoneNumber("")
                .altEmail("")
                .altPhoneNumberext("")
                .alternate(false)
                .saveInAddressBook(null)
                .build();

        // 3. Detail Address (Uses QuoteDetailAddress -> STRICTLY NO 'alternate' OR 'alt' fields)
        CreateQuotePayload.QuoteDetailAddress quoteDetailAddress = CreateQuotePayload.QuoteDetailAddress.builder()
                .countryId(COUNTRY_ID)
                .regionId(REGION_ID)
                .regionCode(REGION_CODE)
                .region(REGION_CODE)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .company(COMPANY)
                .telephone(TELEPHONE)
                .postcode(POSTCODE)
                .city(CITY)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .customAttributes(quoteCustomAttributes)
                .build();

        CreateQuotePayload.QuoteShippingDetail quoteShippingDetail = CreateQuotePayload.QuoteShippingDetail.builder()
                .carrierCode(shipMethodData.getCarrierCode())
                .methodCode(shipMethodData.getMethodCode())
                .carrierTitle(shipMethodData.getCarrierTitle())
                .methodTitle(shipMethodData.getMethodTitle())
                .amount(0) // Integer 0
                .baseAmount(0)
                .available(shipMethodData.getAvailable())
                .priceInclTax(0)
                .priceExclTax(0)
                .offerId(shipMethodData.getOfferId())
                .title(shipMethodData.getTitle())
                .selected(shipMethodData.getSelected())
                .selectedCode(shipMethodData.getSelectedCode())
                .itemId(shipMethodData.getItemId())
                .shippingTypeLabel(shipMethodData.getShippingTypeLabel())
                .deliveryDate(shipMethodData.getDeliveryDate())
                .deliveryDateText(shipMethodData.getDeliveryDateText())
                .marketplace(shipMethodData.getMarketplace())
                .sellerId(shipMethodData.getSellerId())
                .sellerName(shipMethodData.getSellerName())
                .surchargeAmount(shipMethodData.getSurchargeAmount())
                .extensionAttributes(shipMethodData.getExtensionAttributes())
                .address(quoteDetailAddress) // Uses the Strict Detail Address
                .fedexShipReferenceId("")
                .productionLocation("")
                .build();

        CreateQuotePayload.AddressInformation addressInfo = CreateQuotePayload.AddressInformation.builder()
                .shippingAddress(mainShippingAddress)
                .billingAddress(mainBillingAddress)
                .shippingMethodCode(firstMethod.getMethodCode())
                .shippingCarrierCode(firstMethod.getCarrierCode())
                .shippingDetail(quoteShippingDetail)
                .build();

        CreateQuotePayload quotePayload = CreateQuotePayload.builder()
                .addressInformation(addressInfo)
                .rateApiResponse(rateQuoteString)
                .build();

        String quotePayloadJson = objectMapper.writeValueAsString(quotePayload);

        // Uses --data-raw emulation via body string to prevent URL encoding
        Response quoteResponse = given()
                .filter(cookieFilter)
                .filter(new CurlLoggingFilter())
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/default/checkout")
                .body("data=" + quotePayloadJson)
                .post(createQuoteEndpoint)
                .then()
                .log().ifError()
                .statusCode(200)
                .extract()
                .response();

        log.info("Quote Response: {}", quoteResponse.asString());
        JsonNode quoteTree = objectMapper.readTree(quoteResponse.asString());
        assertTrue(quoteTree.has("stateOrProvinceCode"), "Response missing stateOrProvinceCode");
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