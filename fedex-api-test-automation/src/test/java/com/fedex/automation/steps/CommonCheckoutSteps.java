package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.service.mirakl.OfferService;
import com.fedex.automation.utils.FedExEncryptionUtil;
import com.fedex.automation.utils.TestDataFactory;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class CommonCheckoutSteps {

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private TestContext testContext;
    @Autowired
    private CartService cartService;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private MiraklAdminTriggerService miraklAdminTriggerService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OfferService offerService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private ConfiguratorService configuratorService; // Added for 1P

    // Default Test Card Data
    private static final String CARD_NUMBER_RAW = "4111111111111111";
    private static final String CARD_EXP_MONTH = "12";
    private static final String CARD_EXP_YEAR = "2029";
    private static final String CARD_CVV = "123";

    @Given("I initialize the FedEx session")
    public void iInitializeTheFedExSession() {
        log.info("--- [Step] Initializing Session ---");
        sessionService.bootstrapSession();
        assertNotNull(sessionService.getFormKey(), "Form Key must be extracted");
    }

    /**
     * Unified Step for Adding Products (1P and 3P).
     * Decides flow based on 'sellerModel' column in DataTable.
     */
    @When("I search and add the following products to the cart:")
    public void iSearchAndAddTheFollowingProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String quantity = row.get("quantity");
            // Default to "3P" for retrocompatibility with existing feature files
            String sellerModel = row.getOrDefault("sellerModel", "3P");

            log.info("--- Processing Item: {} (Qty: {}, Model: {}) ---", productName, quantity, sellerModel);

            // 1. Unified Search using Strategy
            String sku = catalogService.searchProductSku(productName, sellerModel);
            assertNotNull(sku, "SKU not found for: " + productName);

            // 2. Branch Logic based on Model
            if ("1P".equalsIgnoreCase(sellerModel)) {
                // --- 1P Flow ---
                String partnerId = resolve1PPartnerId(productName);
                configuratorService.add1PConfiguredItemToCart(sku, partnerId, Integer.parseInt(quantity));
            } else {
                // --- 3P Flow (Default) ---
                String offerId = offerService.getOfferIdForProduct(sku);
                cartService.addToCart(sku, quantity, offerId);
                testContext.setCurrentOfferId(offerId);
            }

            // 3. Update Shared State
            testContext.setCurrentSku(sku);
        }
    }

    // Moved/Adapted from FedEx1PSteps
    private String resolve1PPartnerId(String productName) {
        // This mapping could be moved to properties or DB if it grows
        if (productName.equalsIgnoreCase("Flyers")) {
            return "CVAFLY1020";
        }
        return "CVAFLY1020"; // Default fallback
    }

    // --- Remaining Checkout Steps (Unchanged) ---

    @Then("I check the cart html")
    public void iCheckTheCartHtml() {
        CartContext cartData = testContext.getCartData();
        String maskedQuoteId = cartData.getMaskedQuoteId();
        if (isNullOrEmpty(maskedQuoteId)) throw new RuntimeException("The attribute maskedQuoteId is null or empty!");

        cartService.checkCartTotalsInformation(cartData.getMaskedQuoteId());
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

        JsonNode rootNode = objectMapper.readTree(responseBody);

        if (rootNode.has("unified_data_layer") && rootNode.path("unified_data_layer").has("orderNumber")) {
            String orderNumber = rootNode.path("unified_data_layer").path("orderNumber").asText();
            if (!isNullOrEmpty(orderNumber)) {
                log.info("SUCCESS: Order Placed! Number: {}", orderNumber);
                testContext.setPlacedOrderNumber(orderNumber);
            } else {
                fail("Order Submission: 'orderNumber' field was empty.");
            }
        } else {
            fail("Order Submission Failed: Response missing 'unified_data_layer.orderNumber'.\nBody: " + responseBody);
        }

        try {
            if (rootNode.has("0") && rootNode.path("0").has("0")) {
                String innerJsonString = rootNode.path("0").path("0").asText();
                JsonNode innerRoot = objectMapper.readTree(innerJsonString);
                if (innerRoot.has("output") && innerRoot.path("output").has("checkout")) {
                    testContext.setCheckoutDetails(innerRoot.path("output").path("checkout"));
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse inner checkout details.", e);
        }
    }

    @And("the order should be placed successfully with a generated Order Number")
    public void theOrderShouldBePlacedSuccessfully() {
        assertNotNull(testContext.getPlacedOrderNumber());
        log.info("VERIFIED: Order Number '{}' is present.", testContext.getPlacedOrderNumber());
    }

    @And("I verify the order contact details:")
    public void iVerifyTheOrderContactDetails(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode contactNode = testContext.getCheckoutDetails().path("contact");
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        if (expected.containsKey("firstName")) assertEquals(expected.get("firstName"), contactNode.path("personName").path("firstName").asText());
        if (expected.containsKey("lastName")) assertEquals(expected.get("lastName"), contactNode.path("personName").path("lastName").asText());
        if (expected.containsKey("email")) assertEquals(expected.get("email"), contactNode.path("emailDetail").path("emailAddress").asText());
    }

    @And("I verify the transaction payment details:")
    public void iVerifyTheTransactionPaymentDetails(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode tenders = testContext.getCheckoutDetails().path("tenders");
        JsonNode tenderNode = tenders.get(0);
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        if (expected.containsKey("paymentType")) assertEquals(expected.get("paymentType"), tenderNode.path("paymentType").asText());
        if (expected.containsKey("currency")) assertEquals(expected.get("currency"), tenderNode.path("currency").asText());
        if (expected.containsKey("amount")) assertEquals(expected.get("amount"), tenderNode.path("requestedAmount").asText());
    }

    @And("I verify the product totals and taxation:")
    public void iVerifyTheOrderTotalsAndTaxation(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode lineItemContainer = getFirstLineItemContainer();
        JsonNode productTotals = lineItemContainer.path("deliveryLines").get(0).path("productTotals");
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        if (expected.containsKey("taxableAmount")) assertEquals(expected.get("taxableAmount"), productTotals.path("productTaxableAmount").asText());
        if (expected.containsKey("taxAmount")) assertEquals(expected.get("taxAmount"), productTotals.path("productTaxAmount").asText());
        if (expected.containsKey("totalAmount")) assertEquals(expected.get("totalAmount"), productTotals.path("productTotalAmount").asText());
    }

    @And("I verify the product line items:")
    public void iVerifyProductLineItems(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode productLines = getFirstLineItemContainer().path("productLines");
        List<Map<String, String>> expectedItems = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> expected : expectedItems) {
            String expectedName = expected.get("productName");
            int expectedQty = Integer.parseInt(expected.get("quantity"));
            boolean found = false;

            for (JsonNode actualItem : productLines) {
                if (actualItem.path("name").asText().contains(expectedName)) {
                    assertEquals(expectedQty, actualItem.path("unitQuantity").asInt());
                    found = true;
                    break;
                }
            }
            if (!found) fail("Expected Product not found: " + expectedName);
        }
    }

    @And("I trigger the order export to Mirakl")
    public void iTriggerTheOrderExportToMirakl() {
        miraklAdminTriggerService.triggerSendToMirakl(testContext.getPlacedOrderNumber());
    }

    private JsonNode getFirstLineItemContainer() {
        JsonNode lineItems = testContext.getCheckoutDetails().path("lineItems");
        if (lineItems.isEmpty()) fail("Verification Failed: 'lineItems' array is empty.");
        JsonNode firstItem = lineItems.get(0);
        if (firstItem.has("retailPrintOrderDetails") && !firstItem.path("retailPrintOrderDetails").isEmpty()) {
            return firstItem.path("retailPrintOrderDetails").get(0);
        }
        return firstItem;
    }

    private void verifyCheckoutDetailsExist() {
        if (testContext.getCheckoutDetails() == null) {
            fail("Checkout Details are null.");
        }
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}