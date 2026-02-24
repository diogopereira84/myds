package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.service.mirakl.MiraklAdminTriggerService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class CommonCheckoutSteps {

    @Autowired private CatalogService catalogService;
    @Autowired private TestContext testContext;
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private MiraklAdminTriggerService miraklAdminTriggerService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OfferService offerService;
    @Autowired private SessionService sessionService;
    @Autowired private ConfiguratorService configuratorService;

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

    @When("I search and add the following products to the cart:")
    public void iSearchAndAddTheFollowingProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String quantity = row.get("quantity");
            // Default to "3P" for retrocompatibility
            String sellerModel = row.getOrDefault("sellerModel", "3P");

            log.info("--- Processing Item: {} (Qty: {}, Model: {}) ---", productName, quantity, sellerModel);

            String sku = catalogService.searchProductSku(productName, sellerModel);
            assertNotNull(sku, "SKU not found for: " + productName);

            if ("1P".equalsIgnoreCase(sellerModel)) {
                String partnerId = resolve1PPartnerId(productName);
                configuratorService.add1PConfiguredItemToCart(sku, partnerId, Integer.parseInt(quantity));
            } else {
                String offerId = offerService.getOfferIdForProduct(sku);
                cartService.addToCart(sku, quantity, offerId);
                testContext.setCurrentOfferId(offerId);
            }

            // Persist the model for later steps
            testContext.setCurrentSku(sku);
            testContext.setSellerModel(sellerModel);
        }
    }

    // --- NEW: Step 1 - Isolated Search Step ---
    @When("I search for the following products:")
    public void iSearchForTheFollowingProducts(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        testContext.getSearchedProducts().clear();

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String sellerModel = row.getOrDefault("sellerModel", "3P");

            log.info("--- Processing Search: {} (Model: {}) ---", productName, sellerModel);

            String sku = catalogService.searchProductSku(productName, sellerModel);
            assertNotNull(sku, "SKU not found for: " + productName);

            TestContext.ProductItemContext itemContext = new TestContext.ProductItemContext();
            itemContext.setProductName(productName);
            itemContext.setSku(sku);
            itemContext.setSellerModel(sellerModel);

            // Fetch OfferID exclusively for 3P products
            if ("3P".equalsIgnoreCase(sellerModel)) {
                String offerId = offerService.getOfferIdForProduct(sku);
                itemContext.setOfferId(offerId);
            }

            testContext.getSearchedProducts().add(itemContext);

            // Persist the last item searched to shared context for legacy steps
            testContext.setCurrentSku(sku);
            testContext.setSellerModel(sellerModel);
            if (itemContext.getOfferId() != null) testContext.setCurrentOfferId(itemContext.getOfferId());
        }
    }

    // --- NEW: Step 2 - Isolated Add To Cart Step ---
    @And("I add the following products to the cart:")
    public void iAddTheFollowingProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String quantity = row.get("quantity");

            // Look up the product details we just searched for
            TestContext.ProductItemContext item = testContext.getSearchedProducts().stream()
                    .filter(p -> p.getProductName().equalsIgnoreCase(productName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Product not found in search context. Did you run the search step? Product: " + productName));

            log.info("--- Adding Item to Cart: {} (Qty: {}, Model: {}) ---", productName, quantity, item.getSellerModel());

            if ("1P".equalsIgnoreCase(item.getSellerModel())) {
                String partnerId = resolve1PPartnerId(productName);
                configuratorService.add1PConfiguredItemToCart(item.getSku(), partnerId, Integer.parseInt(quantity));
            } else {
                cartService.addToCart(item.getSku(), quantity, item.getOfferId());
            }
        }
    }

    private String resolve1PPartnerId(String productName) {
        return "CVAFLY1020";
    }

    @Then("I check the cart html")
    public void iCheckTheCartHtml() {
        cartService.checkCart();
    }

    @And("I scrape the cart context data")
    public void iScrapeTheCartContextData() {
        String skuToLookFor = testContext.getCurrentSku();
        assertNotNull(skuToLookFor, "SKU is null in TestContext.");
        CartContext cartData = cartService.scrapeCartContext(skuToLookFor);
        testContext.setCartData(cartData);

        if (isNullOrEmpty(cartData.getQuoteId()) && isNullOrEmpty(cartData.getMaskedQuoteId())) {
            fail("CRITICAL: Scrape failed to retrieve ANY Quote ID.");
        }
    }

    @And("I estimate shipping methods and select {string}")
    public void iEstimateShippingMethodsAndSelect(String methodCode) {
        CartContext cartData = testContext.getCartData();
        String quoteIdToUse = isNullOrEmpty(cartData.getMaskedQuoteId()) ? cartData.getQuoteId() : cartData.getMaskedQuoteId();

        if (isNullOrEmpty(quoteIdToUse)) throw new RuntimeException("Cannot Estimate Shipping: IDs missing.");

        log.info("--- [Step] Estimating Shipping for Quote: {} ---", quoteIdToUse);

        EstimateShippingRequest request = TestDataFactory.createEstimateRequest();
        EstimateShipMethodResponse[] methods = checkoutService.estimateShipping(quoteIdToUse, request);
        assertNotNull(methods, "Shipping methods API returned null");

        log.info("Available Shipping Methods: {}",
                Arrays.stream(methods).map(EstimateShipMethodResponse::getMethodCode).collect(Collectors.joining(", ")));

        EstimateShipMethodResponse selected = null;
        for (EstimateShipMethodResponse m : methods) {
            if (m.getMethodCode().equalsIgnoreCase(methodCode)) {
                selected = m;
                break;
            }
        }

        if (selected == null) {
            if (methods.length > 0) {
                log.warn("Requested method '{}' not found. Defaulting to first available: {}", methodCode, methods[0].getMethodCode());
                selected = methods[0];
            } else {
                fail("No shipping methods available for selection.");
            }
        }

        log.info("SELECTED METHOD: {} (Amount: {})", selected.getMethodCode(), selected.getAmount());
        testContext.setSelectedShippingMethod(selected);
    }

    @And("I retrieve the delivery rate")
    public void iRetrieveTheDeliveryRate() {
        // Retrieve the seller model saved in previous step
        String sellerModel = testContext.getSellerModel() != null ? testContext.getSellerModel() : "3P";

        log.info("Retrieving Delivery Rate (Model: {})", sellerModel);

        // Pass model to Factory to generate correct 1P/3P payload
        DeliveryRateRequestForm form = TestDataFactory.createRateForm(testContext.getSelectedShippingMethod(), sellerModel);

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

        if (rootNode.has("unified_data_layer")) {
            testContext.setUnifiedDataLayer(rootNode.path("unified_data_layer"));
            if (rootNode.path("unified_data_layer").has("orderNumber")) {
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
        } else {
            fail("Order Submission Failed: Response missing 'unified_data_layer'.\nBody: " + responseBody);
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

    // --- BDD Verification Steps ---

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
        if (tenders.isEmpty()) fail("No tender details found");
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
        JsonNode container = getFirstLineItemContainer();
        JsonNode productLines = container.path("productLines");

        if (productLines.isMissingNode() || productLines.isEmpty()) {
            log.warn("Validation Warning: 'productLines' array is missing or empty in the checkout response.");
            log.info("Container Node: {}", container);
        }

        List<Map<String, String>> expectedItems = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> expected : expectedItems) {
            String expectedName = expected.get("productName");
            int expectedQty = Integer.parseInt(expected.get("quantity"));
            boolean found = false;
            List<String> seenProducts = new ArrayList<>();

            for (JsonNode actualItem : productLines) {
                String name = actualItem.path("name").asText();
                String userProductName = actualItem.path("userProductName").asText();
                seenProducts.add(String.format("[name=%s, userProductName=%s]", name, userProductName));

                if (name.contains(expectedName) || userProductName.contains(expectedName)) {
                    assertEquals(expectedQty, actualItem.path("unitQuantity").asInt(), "Quantity mismatch for " + expectedName);
                    found = true;
                    break;
                }
            }
            if (!found) {
                fail(String.format("Expected Product not found: %s. Available items: %s", expectedName, seenProducts));
            }
        }
    }

    @And("I verify the unified data layer:")
    public void iVerifyTheUnifiedDataLayer(DataTable dataTable) {
        if (testContext.getUnifiedDataLayer() == null) {
            fail("Unified Data Layer is null.");
        }
        JsonNode udl = testContext.getUnifiedDataLayer();
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        expected.forEach((key, value) -> {
            if (udl.has(key)) {
                assertEquals(value, udl.path(key).asText(), "Mismatch for key: " + key);
            } else {
                fail("Unified Data Layer missing key: " + key);
            }
        });
    }

    @And("I verify the order totals:")
    public void iVerifyTheOrderTotals(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode transactionTotals = testContext.getCheckoutDetails().path("transactionTotals");
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        if (expected.containsKey("grossAmount")) assertEquals(expected.get("grossAmount"), transactionTotals.path("grossAmount").asText());
        if (expected.containsKey("netAmount")) assertEquals(expected.get("netAmount"), transactionTotals.path("netAmount").asText());
        if (expected.containsKey("taxAmount")) assertEquals(expected.get("taxAmount"), transactionTotals.path("taxAmount").asText());
        if (expected.containsKey("totalAmount")) assertEquals(expected.get("totalAmount"), transactionTotals.path("totalAmount").asText());
    }

    @And("I trigger the order export to Mirakl")
    public void iTriggerTheOrderExportToMirakl() {
        miraklAdminTriggerService.triggerSendToMirakl(testContext.getPlacedOrderNumber());
    }

    private JsonNode getFirstLineItemContainer() {
        JsonNode lineItems = testContext.getCheckoutDetails().path("lineItems");
        if (lineItems.isEmpty()) fail("Verification Failed: 'lineItems' array is empty.");
        JsonNode firstItem = lineItems.get(0);
        // 1P orders might have 'retailPrintOrderDetails'
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