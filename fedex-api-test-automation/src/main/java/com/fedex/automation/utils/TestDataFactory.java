package com.fedex.automation.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.*;
import com.fedex.automation.model.EstimateShippingRequest.CustomAttribute;

import java.util.*;

public class TestDataFactory {

    private static final ObjectMapper mapper = new ObjectMapper();

    // --- Constants ---
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
    private static final String PAYMENT_METHOD = "cc";
    private static final String CARD_YEAR = "2035";
    private static final String CARD_EXPIRE = "02";
    private static final String NAME_ON_CARD = "DIOGO";
    private static final String MASKED_CARD_NUMBER = "*1111";
    private static final String CVV = "345";
    private static final String CREDIT_CARD_TYPE_URL = "https://dev.office.fedex.com/media/wysiwyg/Visa.png";

    // --- Factory Methods ---

    public static EstimateShippingRequest createEstimateRequest() {
        return EstimateShippingRequest.builder()
                .pickup(false)
                .isPickup(false)
                .reRate(true)
                .productionLocation(null)
                .address(buildAddress())
                .build();
    }

    public static DeliveryRateRequestForm createRateForm(EstimateShipMethodResponse selectedMethod) {
        return DeliveryRateRequestForm.builder()
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME)
                .email(EMAIL_ID)
                .telephone(TELEPHONE)
                .shipMethod(selectedMethod.getMethodCode())
                .zipcode(POSTCODE)
                .regionId(REGION_ID)
                .city(CITY)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .company(COMPANY)
                .isResidenceShipping(IS_RESIDENCE_SHIPPING)
                .shipMethodData(selectedMethod) // Pass object directly, Service handles mapping
                .thirdPartyCarrierCode(selectedMethod.getCarrierCode())
                .thirdPartyMethodCode(selectedMethod.getMethodCode())
                .firstPartyCarrierCode("")
                .firstPartyMethodCode("")
                .locationId("")
                .build();
    }

    public static Map<String, Object> buildQuotePayload(JsonNode rateResponse, EstimateShipMethodResponse selectedMethod) {
        try {
            String rateQuoteString = mapper.writeValueAsString(rateResponse.get("rateQuote"));

            Map<String, Object> shippingAddressMap = buildQuoteAddress(false);
            Map<String, Object> billingAddressMap = buildQuoteAddress(true);

            // Convert the selected method object to a Map for the payload
            Map<String, Object> shippingDetailMap = mapper.convertValue(selectedMethod, Map.class);

            Map<String, Object> addressInformation = new LinkedHashMap<>();
            addressInformation.put("shipping_address", shippingAddressMap);
            addressInformation.put("billing_address", billingAddressMap);
            addressInformation.put("shipping_method_code", selectedMethod.getMethodCode());
            addressInformation.put("shipping_carrier_code", selectedMethod.getCarrierCode());
            addressInformation.put("shipping_detail", shippingDetailMap);

            Map<String, Object> quotePayload = new LinkedHashMap<>();
            quotePayload.put("addressInformation", addressInformation);
            quotePayload.put("rateapi_response", rateQuoteString);

            return quotePayload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build quote payload", e);
        }
    }

    public static SubmitOrderRequest createOrderRequest(String encryptedCardData) {
        Map<String, Object> billingAddress = buildPaymentBillingAddress();

        PaymentData paymentDataObj = PaymentData.builder()
                .loginValidationKey("")
                .paymentMethod(PAYMENT_METHOD)
                .year(CARD_YEAR)
                .expire(CARD_EXPIRE)
                .nameOnCard(NAME_ON_CARD)
                .number(MASKED_CARD_NUMBER)
                .cvv(CVV)
                .isBillingAddress(false)
                .isFedexAccountApplied(false)
                .fedexAccountNumber(null)
                .creditCardType(CREDIT_CARD_TYPE_URL)
                // Use a simplified map structure if the PaymentData model is complex to instantiate fully with nested types
                // or ensure PaymentData model matches this structure exactly.
                // For safety in this factory, we can use the ObjectMapper to convert our Map helper to the POJO if needed,
                // or simply return the JSON string if the SubmitOrderRequest expects a string.
                .build();

        // Since SubmitOrderRequest expects paymentData as a JSON String:
        String paymentDataJson;
        try {
            // We manually build the map to ensure it matches the exact structure expected by the server
            // (The PaymentData POJO works too if it aligns perfectly)
            Map<String, Object> paymentMap = new LinkedHashMap<>();
            paymentMap.put("loginValidationKey", "");
            paymentMap.put("paymentMethod", PAYMENT_METHOD);
            paymentMap.put("year", CARD_YEAR);
            paymentMap.put("expire", CARD_EXPIRE);
            paymentMap.put("nameOnCard", NAME_ON_CARD);
            paymentMap.put("number", MASKED_CARD_NUMBER);
            paymentMap.put("cvv", CVV);
            paymentMap.put("isBillingAddress", false);
            paymentMap.put("isFedexAccountApplied", false);
            paymentMap.put("fedexAccountNumber", null);
            paymentMap.put("creditCardType", CREDIT_CARD_TYPE_URL);
            paymentMap.put("billingAddress", billingAddress);

            paymentDataJson = mapper.writeValueAsString(paymentMap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return SubmitOrderRequest.builder()
                .paymentData(paymentDataJson)
                .encCCData(encryptedCardData)
                .pickupData(null)
                .useSiteCreditCard(false)
                .selectedProductionId(null)
                .gRecaptchaResponse("")
                .build();
    }

    // --- Private Helper Methods ---

    private static Address buildAddress() {
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

    private static Map<String, Object> buildQuoteAddress(boolean isBilling) {
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

    private static Map<String, Object> buildPaymentBillingAddress() {
        Map<String, Object> addr = buildQuoteAddress(false);
        addr.put("address", STREET_LINE_1);
        addr.put("addressTwo", STREET_LINE_2);
        return addr;
    }

    private static Map<String, Object> attr(String code, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attribute_code", code);
        m.put("value", value);
        return m;
    }

    private static Map<String, Object> attr(String code, Object value, String label) {
        Map<String, Object> m = attr(code, value);
        m.put("label", label);
        return m;
    }
}