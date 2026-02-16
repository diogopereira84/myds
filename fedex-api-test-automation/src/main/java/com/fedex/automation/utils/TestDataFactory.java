package com.fedex.automation.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.model.fedex.CreateQuotePayload.*;
import com.fedex.automation.model.fedex.EstimateShippingRequest.CustomAttribute;

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
                .shipMethodData(selectedMethod)
                .thirdPartyCarrierCode(selectedMethod.getCarrierCode())
                .thirdPartyMethodCode(selectedMethod.getMethodCode())
                .firstPartyCarrierCode("")
                .firstPartyMethodCode("")
                .locationId("")
                .build();
    }

    /**
     * FIX: Returns strict CreateQuotePayload POJO instead of Map to prevent NoSuchMethodError.
     */
    public static CreateQuotePayload buildQuotePayload(JsonNode rateResponse, EstimateShipMethodResponse selectedMethod) {
        try {
            // 1. Extract Rate Quote JSON string
            String rateQuoteString = mapper.writeValueAsString(rateResponse.get("rateQuote"));

            // 2. Build Typed Addresses
            QuoteShippingAddress shippingAddress = buildQuoteShippingAddress();
            QuoteBillingAddress billingAddress = buildQuoteBillingAddress();

            // 3. Build Shipping Detail from Selected Method
            QuoteShippingDetail shippingDetail = QuoteShippingDetail.builder()
                    .carrierCode(selectedMethod.getCarrierCode())
                    .methodCode(selectedMethod.getMethodCode())
                    .carrierTitle(selectedMethod.getCarrierTitle())
                    .methodTitle(selectedMethod.getMethodTitle())
                    .amount(selectedMethod.getAmount())
                    .baseAmount(selectedMethod.getBaseAmount())
                    .available(selectedMethod.getAvailable())
                    .priceInclTax(selectedMethod.getPriceInclTax())
                    .priceExclTax(selectedMethod.getPriceExclTax())
                    .title(selectedMethod.getTitle())
                    .itemId(selectedMethod.getItemId())
                    .shippingTypeLabel(selectedMethod.getShippingTypeLabel())
                    .address(buildQuoteDetailAddress()) // Strict detail address
                    .build();

            // 4. Construct Address Information
            AddressInformation addressInfo = AddressInformation.builder()
                    .shippingAddress(shippingAddress)
                    .billingAddress(billingAddress)
                    .shippingMethodCode(selectedMethod.getMethodCode())
                    .shippingCarrierCode(selectedMethod.getCarrierCode())
                    .shippingDetail(shippingDetail)
                    .build();

            // 5. Return Final Payload Object
            return CreateQuotePayload.builder()
                    .addressInformation(addressInfo)
                    .rateApiResponse(rateQuoteString)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build quote payload", e);
        }
    }

    public static SubmitOrderRequest createOrderRequest(String encryptedCardData) {
        // Using Map here is fine for the JSON string generation required by SubmitOrderRequest
        Map<String, Object> billingAddress = buildPaymentBillingAddressMap();

        String paymentDataJson;
        try {
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

    // --- Private Builders for POJOs ---

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
                .customAttributes(buildCustomAttributes())
                .build();
    }

    private static List<CustomAttribute> buildCustomAttributes() {
        return Arrays.asList(
                CustomAttribute.builder().attributeCode("email_id").value(EMAIL_ID).build(),
                CustomAttribute.builder().attributeCode("ext").value(TELEPHONE_EXT).build(),
                CustomAttribute.builder().attributeCode("residence_shipping").value(IS_RESIDENCE_SHIPPING).label(RESIDENCE_SHIPPING_LABEL).build()
        );
    }

    private static QuoteShippingAddress buildQuoteShippingAddress() {
        return QuoteShippingAddress.builder()
                .countryId(COUNTRY_ID).regionId(REGION_ID).regionCode(REGION_CODE).region(REGION_CODE)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .city(CITY).postcode(POSTCODE)
                .firstname(FIRST_NAME).lastname(LAST_NAME)
                .company(COMPANY).telephone(TELEPHONE)
                .customAttributes(buildQuoteCustomAttributes())
                .altFirstName("").altLastName("").altPhoneNumber("").altEmail("").altPhoneNumberext("")
                .alternate(false)
                .build();
    }

    private static QuoteBillingAddress buildQuoteBillingAddress() {
        return QuoteBillingAddress.builder()
                .countryId(COUNTRY_ID).regionId(REGION_ID).regionCode(REGION_CODE).region(REGION_CODE)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .city(CITY).postcode(POSTCODE)
                .firstname(FIRST_NAME).lastname(LAST_NAME)
                .company(COMPANY).telephone(TELEPHONE)
                .customAttributes(buildQuoteCustomAttributes())
                .altFirstName("").altLastName("").altPhoneNumber("").altEmail("").altPhoneNumberext("")
                .alternate(false)
                .saveInAddressBook(null)
                .build();
    }

    private static QuoteDetailAddress buildQuoteDetailAddress() {
        return QuoteDetailAddress.builder()
                .countryId(COUNTRY_ID).regionId(REGION_ID).regionCode(REGION_CODE).region(REGION_CODE)
                .street(Arrays.asList(STREET_LINE_1, STREET_LINE_2))
                .city(CITY).postcode(POSTCODE)
                .firstname(FIRST_NAME).lastname(LAST_NAME)
                .company(COMPANY).telephone(TELEPHONE)
                .customAttributes(buildQuoteCustomAttributes())
                .build();
    }

    private static List<QuoteCustomAttribute> buildQuoteCustomAttributes() {
        return Arrays.asList(
                QuoteCustomAttribute.builder().attributeCode("email_id").value(EMAIL_ID).build(),
                QuoteCustomAttribute.builder().attributeCode("ext").value(TELEPHONE_EXT).build(),
                QuoteCustomAttribute.builder().attributeCode("residence_shipping").value(IS_RESIDENCE_SHIPPING).label(RESIDENCE_SHIPPING_LABEL).build()
        );
    }

    private static Map<String, Object> buildPaymentBillingAddressMap() {
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("countryId", COUNTRY_ID);
        addr.put("regionId", REGION_ID);
        addr.put("regionCode", REGION_CODE);
        addr.put("region", REGION_CODE);
        addr.put("street", Arrays.asList(STREET_LINE_1, STREET_LINE_2));
        addr.put("city", CITY);
        addr.put("postcode", POSTCODE);
        addr.put("firstname", FIRST_NAME);
        addr.put("lastname", LAST_NAME);
        addr.put("telephone", TELEPHONE);
        addr.put("address", STREET_LINE_1);
        addr.put("addressTwo", STREET_LINE_2);
        return addr;
    }
}