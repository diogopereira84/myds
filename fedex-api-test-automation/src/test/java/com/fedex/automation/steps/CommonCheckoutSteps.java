package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.utils.FedExEncryptionUtil;
import com.fedex.automation.utils.TestDataFactory;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class CommonCheckoutSteps {

    @Autowired private TestContext testContext;
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private MiraklAdminTriggerService miraklAdminTriggerService;
    @Autowired private ObjectMapper objectMapper;

    // Default Test Card Data
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
        String skuToLookFor = testContext.getCurrentSku();
        assertNotNull(skuToLookFor, "SKU is null in TestContext.");
        CartContext cartData = cartService.scrapeCartContext(skuToLookFor);
        testContext.setCartData(cartData);

        if (isNullOrEmpty(cartData.getQuoteId()) && isNullOrEmpty(cartData.getMaskedQuoteId())) {
            fail("CRITICAL: Scrape failed to retrieve ANY Quote ID. Cart page might not be loaded correctly.");
        }
    }

    @And("I estimate shipping methods and select {string}")
    public void iEstimateShippingMethodsAndSelect(String methodCode) {
        CartContext cartData = testContext.getCartData();
        String quoteIdToUse = isNullOrEmpty(cartData.getMaskedQuoteId()) ? cartData.getQuoteId() : cartData.getMaskedQuoteId();

        if (isNullOrEmpty(quoteIdToUse)) throw new RuntimeException("Cannot Estimate Shipping: IDs missing.");

        EstimateShippingRequest request = TestDataFactory.createEstimateRequest();
        EstimateShipMethodResponse[] methods = checkoutService.estimateShipping(quoteIdToUse, request);

        EstimateShipMethodResponse selected = null;
        for (EstimateShipMethodResponse m : methods) {
            if (m.getMethodCode().equals(methodCode)) {
                selected = m;
                break;
            }
        }
        if (selected == null && methods.length > 0) selected = methods[0];
        testContext.setSelectedShippingMethod(selected);
    }

    @And("I retrieve the delivery rate")
    public void iRetrieveTheDeliveryRate() {
        DeliveryRateRequestForm form = TestDataFactory.createRateForm(testContext.getSelectedShippingMethod());
        JsonNode response = checkoutService.getDeliveryRate(form);
        testContext.setRateResponse(response);
    }

    @And("I create a quote")
    public void iCreateAQuote() {
        checkoutService.createQuote(TestDataFactory.buildQuotePayload(testContext.getRateResponse(), testContext.getSelectedShippingMethod()));
    }

    @And("I validate the pay rate API")
    public void iValidateThePayRateAPI() {
        checkoutService.callPayRate();
    }

    @And("I submit the order using a secure credit card")
    public void iSubmitTheOrderUsingASecureCreditCard() throws Exception {
        log.info("--- [Step] Submit Order ---");
        String publicKey = checkoutService.fetchEncryptionKey();
        String encryptedCard = FedExEncryptionUtil.encryptCreditCard(CARD_NUMBER_RAW, CARD_EXP_MONTH, CARD_EXP_YEAR, CARD_CVV, publicKey);
        String rawEncrypted = URLDecoder.decode(encryptedCard, StandardCharsets.UTF_8);

        SubmitOrderRequest orderRequest = TestDataFactory.createOrderRequest(rawEncrypted);
        String responseBody = checkoutService.submitOrder(orderRequest, testContext.getCartData().getQuoteId());

        // --- JSON Parsing & Order Extraction ---
        JsonNode rootNode = objectMapper.readTree(responseBody);

        if (rootNode.has("unified_data_layer") && rootNode.path("unified_data_layer").has("orderNumber")) {
            String orderNumber = rootNode.path("unified_data_layer").path("orderNumber").asText();
            if (!isNullOrEmpty(orderNumber)) {
                log.info("************************************************************");
                log.info("   SUCCESS: Order Placed! Number: {}", orderNumber);
                log.info("************************************************************");
                testContext.setPlacedOrderNumber(orderNumber);
            } else {
                fail("Order Submission: 'orderNumber' field was empty.");
            }
        } else {
            fail("Order Submission Failed: Response missing 'unified_data_layer.orderNumber'.\nBody: " + responseBody);
        }

        // --- Capture Detailed Response for Verification Steps ---
        try {
            if (rootNode.has("0") && rootNode.path("0").has("0")) {
                String innerJsonString = rootNode.path("0").path("0").asText();
                JsonNode innerRoot = objectMapper.readTree(innerJsonString);

                if (innerRoot.has("output") && innerRoot.path("output").has("checkout")) {
                    testContext.setCheckoutDetails(innerRoot.path("output").path("checkout"));
                    log.info("Detailed Checkout Response captured for verification.");
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse inner checkout details. Verification steps may fail.", e);
        }
    }

    @And("the order should be placed successfully with a generated Order Number")
    public void theOrderShouldBePlacedSuccessfully() {
        assertNotNull(testContext.getPlacedOrderNumber());
        log.info("VERIFIED: Order Number '{}' is present.", testContext.getPlacedOrderNumber());
    }

    // --- BDD VERIFICATION STEPS (Robust Version) ---

    @And("I verify the order contact details:")
    public void iVerifyTheOrderContactDetails(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode contactNode = testContext.getCheckoutDetails().path("contact");
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        log.info("--- [Step] Verifying Contact ---");
        if (expected.containsKey("firstName")) assertEquals(expected.get("firstName"), contactNode.path("personName").path("firstName").asText());
        if (expected.containsKey("lastName")) assertEquals(expected.get("lastName"), contactNode.path("personName").path("lastName").asText());
        if (expected.containsKey("email")) assertEquals(expected.get("email"), contactNode.path("emailDetail").path("emailAddress").asText());
    }

    @And("I verify the transaction payment details:")
    public void iVerifyTheTransactionPaymentDetails(DataTable dataTable) {
        verifyCheckoutDetailsExist();

        JsonNode tenders = testContext.getCheckoutDetails().path("tenders");
        if (tenders.isEmpty()) fail("Verification Failed: 'tenders' array is empty.");

        JsonNode tenderNode = tenders.get(0);
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        log.info("--- [Step] Verifying Payment ---");
        if (expected.containsKey("paymentType")) assertEquals(expected.get("paymentType"), tenderNode.path("paymentType").asText());
        if (expected.containsKey("currency")) assertEquals(expected.get("currency"), tenderNode.path("currency").asText());
        if (expected.containsKey("amount")) assertEquals(expected.get("amount"), tenderNode.path("requestedAmount").asText());

        JsonNode cardNode = tenderNode.path("creditCard");
        if (expected.containsKey("authResponse")) assertEquals(expected.get("authResponse"), cardNode.path("authResponse").asText());
        if (expected.containsKey("maskedNumber")) assertEquals(expected.get("maskedNumber"), cardNode.path("maskedAccountNumber").asText());
    }

    @And("I verify the product totals and taxation:")
    public void iVerifyTheOrderTotalsAndTaxation(DataTable dataTable) {
        verifyCheckoutDetailsExist();

        // Safe navigation to productTotals
        JsonNode lineItems = testContext.getCheckoutDetails().path("lineItems");
        if (lineItems.isEmpty()) fail("Verification Failed: 'lineItems' array is empty.");

        JsonNode deliveryLines = lineItems.get(0).path("deliveryLines");
        if (deliveryLines.isEmpty()) fail("Verification Failed: 'deliveryLines' array is empty inside first lineItem.");

        JsonNode productTotals = deliveryLines.get(0).path("productTotals");
        if (productTotals.isMissingNode()) fail("Verification Failed: 'productTotals' node is missing.");

        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        log.info("--- [Step] Verifying Totals ---");
        if (expected.containsKey("taxableAmount")) assertEquals(expected.get("taxableAmount"), productTotals.path("productTaxableAmount").asText());
        if (expected.containsKey("taxAmount")) assertEquals(expected.get("taxAmount"), productTotals.path("productTaxAmount").asText());
        if (expected.containsKey("totalAmount")) assertEquals(expected.get("totalAmount"), productTotals.path("productTotalAmount").asText());
    }

    @And("I verify the product line items:")
    public void iVerifyProductLineItems(DataTable dataTable) {
        verifyCheckoutDetailsExist();

        JsonNode lineItems = testContext.getCheckoutDetails().path("lineItems");
        if (lineItems.isEmpty()) fail("Verification Failed: 'lineItems' array is empty.");

        JsonNode productLines = lineItems.get(0).path("productLines");
        if (productLines.isEmpty()) fail("Verification Failed: 'productLines' array is empty.");

        JsonNode productLine = productLines.get(0);
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        log.info("--- [Step] Verifying Product Line ---");
        if (expected.containsKey("productName")) {
            String actualName = productLine.path("name").asText();
            assertTrue(actualName.contains(expected.get("productName")),
                    "Expected Product Name to contain: '" + expected.get("productName") + "', but got: '" + actualName + "'");
        }
        if (expected.containsKey("quantity")) {
            assertEquals(Integer.parseInt(expected.get("quantity")), productLine.path("unitQuantity").asInt());
        }
    }

    @And("I trigger the order export to Mirakl")
    public void iTriggerTheOrderExportToMirakl() {
        miraklAdminTriggerService.triggerSendToMirakl(testContext.getPlacedOrderNumber());
    }

    private void verifyCheckoutDetailsExist() {
        if (testContext.getCheckoutDetails() == null) {
            fail("Checkout Details are null. The order submission response was likely not parsed correctly or failed.");
        }
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}