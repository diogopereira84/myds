// src/main/java/com/fedex/automation/utils/TestDataFactory.java
package com.fedex.automation.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.model.fedex.CreateQuotePayload.*;
import com.fedex.automation.model.fedex.EstimateShippingRequest.CustomAttribute;

import java.util.*;

public class TestDataFactory {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, String> getDefaultAddressMap() {
        Map<String, String> map = new HashMap<>();
        map.put("firstName", "Diogo");
        map.put("lastName", "Pereira");
        map.put("street", "550 PEACHTREE ST NE");
        map.put("city", "Los Angeles");
        map.put("regionId", "34");
        map.put("regionCode", "CA");
        map.put("countryId", "US");
        map.put("postcode", "90002");
        map.put("telephone", "4247021234");
        map.put("email", "dpereira@mcfadyen.com");
        return map;
    }

    public static Map<String, String> getDefaultPaymentMap() {
        Map<String, String> map = new HashMap<>();
        map.put("cardNumber", "4111111111111111");
        map.put("expMonth", "12");
        map.put("expYear", "2035");
        map.put("cvv", "123");
        return map;
    }

    public static EstimateShippingRequest createEstimateRequest(Map<String, String> addr) {
        return EstimateShippingRequest.builder()
                .isPickup(false)
                .reRate(true)
                .productionLocation(null)
                .address(buildAddress(addr))
                .build();
    }

    public static DeliveryRateRequestForm createRateForm(EstimateShipMethodResponse selectedMethod, String sellerModel, Map<String, String> addr) {
        boolean is1P = "1P".equalsIgnoreCase(sellerModel);

        EstimateShipMethodResponse.Address methodAddress = EstimateShipMethodResponse.Address.builder()
                .countryId(addr.get("countryId"))
                .regionId(addr.get("regionId"))
                .regionCode(addr.get("regionCode"))
                .region(addr.get("regionCode"))
                .street(Arrays.asList(addr.get("street"), ""))
                .company("")
                .telephone(addr.get("telephone"))
                .postcode(addr.get("postcode"))
                .city(addr.get("city"))
                .firstname(addr.get("firstName"))
                .lastname(addr.get("lastName"))
                .customAttributes(Arrays.asList(
                        EstimateShipMethodResponse.CustomAttribute.builder().attributeCode("email_id").value(addr.get("email")).build(),
                        EstimateShipMethodResponse.CustomAttribute.builder().attributeCode("ext").value("").build(),
                        EstimateShipMethodResponse.CustomAttribute.builder().attributeCode("residence_shipping").value(false).label("No").build()
                ))
                .build();

        selectedMethod.setAddress(methodAddress);

        return DeliveryRateRequestForm.builder()
                .firstname(addr.get("firstName"))
                .lastname(addr.get("lastName"))
                .email(addr.get("email"))
                .telephone(addr.get("telephone"))
                .shipMethod(selectedMethod.getMethodCode())
                .zipcode(addr.get("postcode"))
                .regionId(addr.get("regionId"))
                .city(addr.get("city"))
                .street(Arrays.asList(addr.get("street"), ""))
                .company("")
                .isResidenceShipping(false)
                .shipMethodData(selectedMethod)
                .firstPartyCarrierCode(is1P ? selectedMethod.getCarrierCode() : "")
                .firstPartyMethodCode(is1P ? selectedMethod.getMethodCode() : "")
                .thirdPartyCarrierCode(!is1P ? selectedMethod.getCarrierCode() : "")
                .thirdPartyMethodCode(!is1P ? selectedMethod.getMethodCode() : "")
                .locationId("")
                .build();
    }

    public static CreateQuotePayload buildQuotePayload(JsonNode rateResponse, EstimateShipMethodResponse selectedMethod, Map<String, String> addr) {
        try {
            if (rateResponse == null || !rateResponse.has("rateQuote")) {
                throw new RuntimeException("Cannot build quote: Missing 'rateQuote' in response.");
            }

            String rateQuoteString = mapper.writeValueAsString(rateResponse.get("rateQuote"));

            return CreateQuotePayload.builder()
                    .addressInformation(AddressInformation.builder()
                            .shippingAddress(buildQuoteShippingAddress(addr))
                            .billingAddress(buildQuoteBillingAddress(addr))
                            .shippingMethodCode(selectedMethod.getMethodCode())
                            .shippingCarrierCode(selectedMethod.getCarrierCode())
                            .shippingDetail(QuoteShippingDetail.builder()
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
                                    .address(buildQuoteDetailAddress(addr))
                                    .build())
                            .build())
                    .rateApiResponse(rateQuoteString)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build quote payload", e);
        }
    }

    public static SubmitOrderRequest createOrderRequest(String encryptedCardData, Map<String, String> paymentMap, Map<String, String> addrMap) {
        Map<String, Object> paymentPayload = new LinkedHashMap<>();
        paymentPayload.put("loginValidationKey", "");
        paymentPayload.put("paymentMethod", "cc");
        paymentPayload.put("year", paymentMap.get("expYear"));
        paymentPayload.put("expire", paymentMap.get("expMonth"));
        paymentPayload.put("nameOnCard", (addrMap.get("firstName") + " " + addrMap.get("lastName")).toUpperCase());
        paymentPayload.put("number", "*1111");
        paymentPayload.put("cvv", paymentMap.get("cvv"));
        paymentPayload.put("isBillingAddress", false);
        paymentPayload.put("isFedexAccountApplied", false);
        paymentPayload.put("fedexAccountNumber", null);
        paymentPayload.put("creditCardType", "https://dev.office.fedex.com/media/wysiwyg/Visa.png");
        paymentPayload.put("billingAddress", buildPaymentBillingAddressMap(addrMap));

        String paymentDataJson;
        try {
            paymentDataJson = mapper.writeValueAsString(paymentPayload);
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

    private static Address buildAddress(Map<String, String> addr) {
        return Address.builder()
                .street(Arrays.asList(addr.get("street"), ""))
                .city(addr.get("city"))
                .regionId(addr.get("regionId"))
                .region(addr.get("regionCode"))
                .countryId(addr.get("countryId"))
                .postcode(addr.get("postcode"))
                .firstname(addr.get("firstName"))
                .lastname(addr.get("lastName"))
                .company("")
                .telephone(addr.get("telephone"))
                .customAttributes(buildCustomAttributes(addr.get("email")))
                .build();
    }

    private static List<CustomAttribute> buildCustomAttributes(String email) {
        return Arrays.asList(
                CustomAttribute.builder().attributeCode("email_id").value(email).build(),
                CustomAttribute.builder().attributeCode("ext").value("").build(),
                CustomAttribute.builder().attributeCode("residence_shipping").value(false).label("No").build()
        );
    }

    private static QuoteShippingAddress buildQuoteShippingAddress(Map<String, String> addr) {
        return QuoteShippingAddress.builder()
                .countryId(addr.get("countryId")).regionId(addr.get("regionId")).regionCode(addr.get("regionCode")).region(addr.get("regionCode"))
                .street(Arrays.asList(addr.get("street"), ""))
                .city(addr.get("city")).postcode(addr.get("postcode"))
                .firstname(addr.get("firstName")).lastname(addr.get("lastName"))
                .company("").telephone(addr.get("telephone"))
                .customAttributes(buildQuoteCustomAttributes(addr.get("email")))
                .altFirstName("").altLastName("").altPhoneNumber("").altEmail("").altPhoneNumberext("")
                .alternate(false)
                .build();
    }

    private static QuoteBillingAddress buildQuoteBillingAddress(Map<String, String> addr) {
        return QuoteBillingAddress.builder()
                .countryId(addr.get("countryId")).regionId(addr.get("regionId")).regionCode(addr.get("regionCode")).region(addr.get("regionCode"))
                .street(Arrays.asList(addr.get("street"), ""))
                .city(addr.get("city")).postcode(addr.get("postcode"))
                .firstname(addr.get("firstName")).lastname(addr.get("lastName"))
                .company("").telephone(addr.get("telephone"))
                .customAttributes(buildQuoteCustomAttributes(addr.get("email")))
                .altFirstName("").altLastName("").altPhoneNumber("").altEmail("").altPhoneNumberext("")
                .alternate(false)
                .saveInAddressBook(null)
                .build();
    }

    private static QuoteDetailAddress buildQuoteDetailAddress(Map<String, String> addr) {
        return QuoteDetailAddress.builder()
                .countryId(addr.get("countryId")).regionId(addr.get("regionId")).regionCode(addr.get("regionCode")).region(addr.get("regionCode"))
                .street(Arrays.asList(addr.get("street"), ""))
                .city(addr.get("city")).postcode(addr.get("postcode"))
                .firstname(addr.get("firstName")).lastname(addr.get("lastName"))
                .company("").telephone(addr.get("telephone"))
                .customAttributes(buildQuoteCustomAttributes(addr.get("email")))
                .build();
    }

    private static List<QuoteCustomAttribute> buildQuoteCustomAttributes(String email) {
        return Arrays.asList(
                QuoteCustomAttribute.builder().attributeCode("email_id").value(email).build(),
                QuoteCustomAttribute.builder().attributeCode("ext").value("").build(),
                QuoteCustomAttribute.builder().attributeCode("residence_shipping").value(false).label("No").build()
        );
    }

    private static Map<String, Object> buildPaymentBillingAddressMap(Map<String, String> addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("countryId", addr.get("countryId"));
        map.put("regionId", addr.get("regionId"));
        map.put("regionCode", addr.get("regionCode"));
        map.put("region", addr.get("regionCode"));
        map.put("street", Arrays.asList(addr.get("street"), ""));
        map.put("city", addr.get("city"));
        map.put("postcode", addr.get("postcode"));
        map.put("firstname", addr.get("firstName"));
        map.put("lastname", addr.get("lastName"));
        map.put("telephone", addr.get("telephone"));
        map.put("address", addr.get("street"));
        map.put("addressTwo", "");
        return map;
    }
}