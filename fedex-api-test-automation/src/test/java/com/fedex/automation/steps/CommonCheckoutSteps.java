package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.utils.FedExEncryptionUtil;
import com.fedex.automation.utils.TestDataFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class CommonCheckoutSteps {

    @Autowired private TestContext testContext; // Injected Shared State
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private MiraklAdminTriggerService miraklAdminTriggerService;
    @Autowired private ObjectMapper objectMapper;

    // Constants for encryption
    private static final String CARD_NUMBER_RAW = "4111111111111111";
    private static final String CARD_EXP_MONTH = "12";
    private static final String CARD_EXP_YEAR = "2029";
    private static final String CARD_CVV = "123";

    @Then("I check the cart html")
    public void iCheckTheCartHtml() {
        cartService.checkCartTotalsInformation();
    }

    @And("I scrape the cart context data")
    public void iScrapeTheCartContextData() {
        // Retrieve SKU set by previous Vendor Step
        String skuToLookFor = testContext.getCurrentSku();
        assertNotNull(skuToLookFor, "SKU is null in TestContext. The 'When' step did not update the context.");

        log.info("--- [Step] Scrape Cart Context for SKU: {} ---", skuToLookFor);

        CartContext cartData = cartService.scrapeCartContext(skuToLookFor);
        testContext.setCartData(cartData); // Store for later steps

        assertNotNull(cartData.getQuoteId(), "Quote ID should not be null");
    }

    @And("I estimate shipping methods and select {string}")
    public void iEstimateShippingMethodsAndSelect(String methodCode) {
        log.info("--- [Step] Estimate Shipping ---");
        CartContext cartData = testContext.getCartData();
        EstimateShippingRequest request = TestDataFactory.createEstimateRequest();

        // Use Masked Quote ID for estimation
        EstimateShipMethodResponse[] methods = checkoutService.estimateShipping(cartData.getMaskedQuoteId(), request);

        EstimateShipMethodResponse selected = null;
        for (EstimateShipMethodResponse m : methods) {
            if (m.getMethodCode().equals(methodCode)) {
                selected = m;
                break;
            }
        }

        if (selected == null && methods.length > 0) {
            log.warn("Method {} not found, defaulting to first available.", methodCode);
            selected = methods[0];
        }
        assertNotNull(selected, "No shipping methods available.");
        testContext.setSelectedShippingMethod(selected);
    }

    @And("I retrieve the delivery rate")
    public void iRetrieveTheDeliveryRate() {
        log.info("--- [Step] Retrieve Delivery Rate ---");
        EstimateShipMethodResponse selectedMethod = testContext.getSelectedShippingMethod();
        DeliveryRateRequestForm form = TestDataFactory.createRateForm(selectedMethod);

        JsonNode response = checkoutService.getDeliveryRate(form);
        testContext.setRateResponse(response);
    }

    @And("I create a quote")
    public void iCreateAQuote() {
        log.info("--- [Step] Create Quote ---");
        checkoutService.createQuote(TestDataFactory.buildQuotePayload(
                testContext.getRateResponse(),
                testContext.getSelectedShippingMethod()
        ));
    }

    @And("I validate the pay rate API")
    public void iValidateThePayRateAPI() {
        log.info("--- [Step] Pay Rate API ---");
        checkoutService.callPayRate();
    }

    @And("I submit the order using a secure credit card")
    public void iSubmitTheOrderUsingASecureCreditCard() throws Exception {
        log.info("--- [Step] Submit Order ---");
        String publicKey = checkoutService.fetchEncryptionKey();

        String encryptedCard = FedExEncryptionUtil.encryptCreditCard(
                CARD_NUMBER_RAW, CARD_EXP_MONTH, CARD_EXP_YEAR, CARD_CVV, publicKey
        );
        String rawEncrypted = URLDecoder.decode(encryptedCard, StandardCharsets.UTF_8);

        SubmitOrderRequest orderRequest = TestDataFactory.createOrderRequest(rawEncrypted);
        String responseBody = checkoutService.submitOrder(orderRequest, testContext.getCartData().getQuoteId());

        JsonNode submitRoot = objectMapper.readTree(responseBody);
        if (submitRoot.has("unified_data_layer")) {
            String orderNumber = submitRoot.path("unified_data_layer").path("orderNumber").asText();
            testContext.setPlacedOrderNumber(orderNumber);
        } else {
            fail("Order Submission Failed: " + responseBody);
        }
    }

    @And("the order should be placed successfully with a generated Order Number")
    public void theOrderShouldBePlacedSuccessfully() {
        String orderNumber = testContext.getPlacedOrderNumber();
        log.info("Order Placed Successfully! Order Number: {}", orderNumber);
        assertNotNull(orderNumber);
        assertFalse(orderNumber.isEmpty());
    }

    @And("I trigger the order export to Mirakl")
    public void iTriggerTheOrderExportToMirakl() {
        log.info("--- [Step] Send Order to Mirakl ---");
        miraklAdminTriggerService.triggerSendToMirakl(testContext.getPlacedOrderNumber());
    }
}