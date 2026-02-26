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
import java.util.List;
import java.util.Map;

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

    @Given("I initialize the FedEx session")
    public void iInitializeTheFedExSession() {
        log.info("--- [Step] Initializing Session ---");
        sessionService.bootstrapSession();
        assertNotNull(sessionService.getFormKey(), "Form Key must be extracted");
    }

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

            if ("3P".equalsIgnoreCase(sellerModel)) {
                String offerId = offerService.getOfferIdForProduct(sku);
                itemContext.setOfferId(offerId);
            }

            testContext.getSearchedProducts().add(itemContext);
            testContext.setCurrentSku(sku);
            testContext.setSellerModel(sellerModel);
            if (itemContext.getOfferId() != null) testContext.setCurrentOfferId(itemContext.getOfferId());
        }
    }

    @And("I add the following products to the cart:")
    public void iAddTheFollowingProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String quantity = row.get("quantity");

            TestContext.ProductItemContext item = testContext.getSearchedProducts().stream()
                    .filter(p -> p.getProductName().equalsIgnoreCase(productName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Product not found in search context."));

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

    @io.cucumber.java.en.And("I provide the shipping address:")
    public void iProvideTheShippingAddress(io.cucumber.datatable.DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        testContext.setShippingAddress(rows.getFirst());
        log.info("Injected Shipping Address: {}", testContext.getShippingAddress().get("city"));
    }

    @io.cucumber.java.en.And("I provide the payment details:")
    public void iProvideThePaymentDetails(io.cucumber.datatable.DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        testContext.setPaymentDetails(rows.getFirst());

        // Safety check to prevent substring exceptions if card is too short
        String maskedCard = testContext.getPaymentDetails().get("cardNumber");
        if (maskedCard != null && maskedCard.length() >= 4) {
            maskedCard = maskedCard.substring(maskedCard.length() - 4);
        }
        log.info("Injected Payment Details for card ending in: {}", maskedCard);
    }

    @And("I estimate shipping methods and select {string}")
    public void iEstimateShippingMethodsAndSelect(String methodCode) {
        CartContext cartData = testContext.getCartData();
        String quoteIdToUse = isNullOrEmpty(cartData.getMaskedQuoteId()) ? cartData.getQuoteId() : cartData.getMaskedQuoteId();

        if (isNullOrEmpty(quoteIdToUse)) throw new RuntimeException("Cannot Estimate Shipping: IDs missing.");

        log.info("--- [Step] Estimating Shipping for Quote: {} ---", quoteIdToUse);

        EstimateShippingRequest request = TestDataFactory.createEstimateRequest(testContext.getShippingAddress());
        EstimateShipMethodResponse[] methods = checkoutService.estimateShipping(quoteIdToUse, request);
        assertNotNull(methods, "Shipping methods API returned null");

        EstimateShipMethodResponse selected = null;
        for (EstimateShipMethodResponse m : methods) {
            if (m.getMethodCode().equalsIgnoreCase(methodCode)) {
                selected = m;
                break;
            }
        }

        if (selected == null) {
            selected = methods[0];
        }

        log.info("SELECTED METHOD: {} (Amount: {})", selected.getMethodCode(), selected.getAmount());
        testContext.setSelectedShippingMethod(selected);
    }

    @And("I retrieve the delivery rate")
    public void iRetrieveTheDeliveryRate() {
        String sellerModel = testContext.getSellerModel() != null ? testContext.getSellerModel() : "3P";
        log.info("Retrieving Delivery Rate (Model: {})", sellerModel);

        DeliveryRateRequestForm form = TestDataFactory.createRateForm(testContext.getSelectedShippingMethod(), sellerModel, testContext.getShippingAddress());
        JsonNode response = checkoutService.getDeliveryRate(form);
        testContext.setRateResponse(response);
    }

    @And("I create a quote")
    public void iCreateAQuote() {
        checkoutService.createQuote(TestDataFactory.buildQuotePayload(testContext.getRateResponse(), testContext.getSelectedShippingMethod(), testContext.getShippingAddress()));
    }

    @And("I validate the pay rate API")
    public void iValidateThePayRateAPI() {
        checkoutService.callPayRate();
    }

    @And("I submit the order using a secure credit card")
    public void iSubmitTheOrderUsingASecureCreditCard() throws Exception {
        log.info("--- [Step] Submit Order ---");

        Map<String, String> paymentInfo = testContext.getPaymentDetails();
        String publicKey = checkoutService.fetchEncryptionKey();

        String encryptedCard = FedExEncryptionUtil.encryptCreditCard(
                paymentInfo.get("cardNumber"), paymentInfo.get("expMonth"), paymentInfo.get("expYear"), paymentInfo.get("cvv"), publicKey
        );
        String rawEncrypted = URLDecoder.decode(encryptedCard, StandardCharsets.UTF_8);

        SubmitOrderRequest orderRequest = TestDataFactory.createOrderRequest(rawEncrypted, paymentInfo, testContext.getShippingAddress());
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

    // --- BDD Verification Steps (Unchanged from existing logic, checks remain functional) ---
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

        // Debug context to immediately show what the API returned upon failure
        String debugContext = "\nActual JSON returned by API: " + productTotals.toPrettyString();

        if (expected.containsKey("taxableAmount")) {
            assertEquals(Double.parseDouble(expected.get("taxableAmount")),
                    productTotals.path("productTaxableAmount").asDouble(),
                    "Mismatch in field: taxableAmount" + debugContext);
        }
        if (expected.containsKey("taxAmount")) {
            assertEquals(Double.parseDouble(expected.get("taxAmount")),
                    productTotals.path("productTaxAmount").asDouble(),
                    "Mismatch in field: taxAmount" + debugContext);
        }
        if (expected.containsKey("totalAmount")) {
            assertEquals(Double.parseDouble(expected.get("totalAmount")),
                    productTotals.path("productTotalAmount").asDouble(),
                    "Mismatch in field: totalAmount" + debugContext);
        }
    }

    @And("I verify the product line items:")
    public void iVerifyProductLineItems(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode container = getFirstLineItemContainer();
        JsonNode productLines = container.path("productLines");
        List<Map<String, String>> expectedItems = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> expected : expectedItems) {
            String expectedName = expected.get("productName");
            int expectedQty = Integer.parseInt(expected.get("quantity"));
            boolean found = false;

            for (JsonNode actualItem : productLines) {
                String name = actualItem.path("name").asText();
                String userProductName = actualItem.path("userProductName").asText();
                if (name.contains(expectedName) || userProductName.contains(expectedName)) {
                    assertEquals(expectedQty, actualItem.path("unitQuantity").asInt(), "Quantity mismatch for " + expectedName);
                    found = true;
                    break;
                }
            }
            if (!found) fail(String.format("Expected Product not found: %s", expectedName));
        }
    }

    @And("I verify the unified data layer:")
    public void iVerifyTheUnifiedDataLayer(DataTable dataTable) {
        if (testContext.getUnifiedDataLayer() == null) fail("Unified Data Layer is null.");
        JsonNode udl = testContext.getUnifiedDataLayer();
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        expected.forEach((key, value) -> {
            if (udl.has(key)) assertEquals(value, udl.path(key).asText(), "Mismatch for key: " + key);
            else fail("Unified Data Layer missing key: " + key);
        });
    }

    @And("I verify the order totals:")
    public void iVerifyTheOrderTotals(DataTable dataTable) {
        verifyCheckoutDetailsExist();
        JsonNode transactionTotals = testContext.getCheckoutDetails().path("transactionTotals");
        Map<String, String> expected = dataTable.asMap(String.class, String.class);

        // Debug context to immediately show what the API returned upon failure
        String debugContext = "\nActual JSON returned by API: " + transactionTotals.toPrettyString();

        if (expected.containsKey("grossAmount")) {
            assertEquals(Double.parseDouble(expected.get("grossAmount")),
                    transactionTotals.path("grossAmount").asDouble(),
                    "Mismatch in field: grossAmount" + debugContext);
        }
        if (expected.containsKey("netAmount")) {
            assertEquals(Double.parseDouble(expected.get("netAmount")),
                    transactionTotals.path("netAmount").asDouble(),
                    "Mismatch in field: netAmount" + debugContext);
        }
        if (expected.containsKey("taxAmount")) {
            assertEquals(Double.parseDouble(expected.get("taxAmount")),
                    transactionTotals.path("taxAmount").asDouble(),
                    "Mismatch in field: taxAmount" + debugContext);
        }
        if (expected.containsKey("totalAmount")) {
            assertEquals(Double.parseDouble(expected.get("totalAmount")),
                    transactionTotals.path("totalAmount").asDouble(),
                    "Mismatch in field: totalAmount" + debugContext);
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
        if (testContext.getCheckoutDetails() == null) fail("Checkout Details are null.");
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}