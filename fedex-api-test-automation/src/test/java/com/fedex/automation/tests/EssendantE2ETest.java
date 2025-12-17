package com.fedex.automation.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.base.BaseTest;
import com.fedex.automation.model.*;
import com.fedex.automation.service.fedex.CartService;
import com.fedex.automation.service.fedex.CatalogService;
import com.fedex.automation.service.fedex.CheckoutService;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.mirakl.OfferService;
import com.fedex.automation.utils.FedExEncryptionUtil;
import com.fedex.automation.utils.TestDataFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class EssendantE2ETest extends BaseTest {

    @Autowired
    private SessionService sessionService;
    @Autowired
    private CatalogService catalogService;
    @Autowired
    private CartService cartService;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private OfferService offerService;
    @Autowired
    private ObjectMapper objectMapper;

    // Data Constants
    private static final String PRODUCT_NAME = "ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box";
    private static final String PRODUCT_QTY = "1";
    private static final String PREFERRED_METHOD_CODE = "FREE_GROUND_US";

    // Encryption Data
    private static final String CARD_NUMBER_RAW = "4111111111111111";
    private static final String CARD_EXP_MONTH = "02";
    private static final String CARD_EXP_YEAR = "2035";
    private static final String CARD_CVV = "345";

    @Test
    public void testEssendantAddUpdateCheckoutFlow() throws Exception {
        log.info("--- [Step 0] Initializing Session ---");
        sessionService.bootstrapSession();
        String formKey = sessionService.getFormKey();
        assertNotNull(formKey, "Form Key must be extracted from the cart page");

        log.info("--- [Step 1] Search Product From Catalog Service ---");
        String sku = catalogService.searchProductSku(PRODUCT_NAME);

        log.info("--- [Step 2] Get the dynamic Offer ID from Mirakl ---");
        String offerId = offerService.getOfferIdForProduct(sku);

        log.info("--- [Step 3] Add to Cart ---");
        cartService.addToCart(sku, PRODUCT_QTY, offerId);

        log.info("--- [Step 4] Scrape Cart Context ---");
        CartContext cartData = cartService.scrapeCartContext(sku);

        log.info("Cart Scraped. QuoteID: {}, ItemID: {}, Qty: {}",
                cartData.getQuoteId(), cartData.getItemId(), cartData.getQty());

        assertEquals(PRODUCT_QTY, String.valueOf(cartData.getQty()), "Cart Quantity mismatch");
        assertNotNull(cartData.getQuoteId(), "Quote ID should not be null");
        assertFalse(cartData.getQuoteId().isEmpty(), "Quote ID should not be empty");

        log.info("--- [Step 5] Estimate Shipping ---");
        EstimateShippingRequest estimateReq = TestDataFactory.createEstimateRequest();

        EstimateShipMethodResponse[] methods = checkoutService.estimateShipping(cartData.getMaskedQuoteId(), estimateReq);
        assertNotNull(methods, "Shipping methods response should not be null");

        EstimateShipMethodResponse selectedMethod = selectMethod(methods, PREFERRED_METHOD_CODE);
        log.info("Selected Method: {} | Carrier: {}", selectedMethod.getMethodCode(), selectedMethod.getCarrierCode());

        assertEquals(PREFERRED_METHOD_CODE, selectedMethod.getMethodCode(), "Shipping Method mismatch");

        log.info("--- [Step 6] Delivery Rate ---");
        DeliveryRateRequestForm rateForm = TestDataFactory.createRateForm(selectedMethod);
        JsonNode rateResponse = checkoutService.getDeliveryRate(rateForm);

        assertNotNull(rateResponse.get("rateQuote"), "Response must contain 'rateQuote'");

        double totalAmount = rateResponse.get("rateQuote")
                .get("rateQuoteDetails")
                .get(0)
                .get("totalAmount")
                .asDouble();

        log.info("Delivery Rate Total Amount: {}", totalAmount);
        if (totalAmount <= 0) {
            fail("Total Amount should be greater than 0. Actual: " + totalAmount);
        }

        log.info("--- [Step 7] Create Quote ---");
        // This method inside CheckoutService now validates that the response doesn't contain "exception"
        checkoutService.createQuote(TestDataFactory.buildQuotePayload(rateResponse, selectedMethod));
        log.info("Quote successfully created.");

        log.info("--- [Step 8] Pay Rate API ---");
        checkoutService.callPayRate();
        log.info("Pay Rate API success.");

        log.info("--- [Step 9] Submit Order ---");

        String publicKey = checkoutService.fetchEncryptionKey();
        log.info("Fetched Public Key. Length: {}", publicKey.length());

        String encryptedCard = FedExEncryptionUtil.encryptCreditCard(
                CARD_NUMBER_RAW, CARD_EXP_MONTH, CARD_EXP_YEAR, CARD_CVV, publicKey
        );
        // decode it back to raw.
        String rawEncrypted = URLDecoder.decode(encryptedCard, StandardCharsets.UTF_8);

        // Build Request
        SubmitOrderRequest orderRequest = TestDataFactory.createOrderRequest(rawEncrypted);

        // Ensure we pass the QuoteID for the cookie
        String responseBody = checkoutService.submitOrder(orderRequest, cartData.getQuoteId());

        // --- Final Assertions ---
        log.info("Submit Order Response: {}", responseBody);

        JsonNode submitRoot = objectMapper.readTree(responseBody);

        if (submitRoot.has("unified_data_layer")) {
            String orderNumber = submitRoot.path("unified_data_layer").path("orderNumber").asText();
            log.info("Order Placed Successfully! Order Number: {}", orderNumber);
            assertNotNull(orderNumber, "Order Number should be present");
            assertFalse(orderNumber.isEmpty(), "Order Number should not be empty");
        } else {
            fail("Order Submission Failed. 'unified_data_layer' not found in response: " + responseBody);
        }
    }

    private EstimateShipMethodResponse selectMethod(EstimateShipMethodResponse[] methods, String code) {
        for(EstimateShipMethodResponse m : methods) {
            if(m.getMethodCode().equals(code)) return m;
        }
        return methods[0];
    }
}