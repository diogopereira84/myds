package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EssendantSteps {

    @Autowired private SessionService sessionService;
    @Autowired private CatalogService catalogService;
    @Autowired private CartService cartService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private OfferService offerService;
    @Autowired private MiraklAdminTriggerService miraklAdminTriggerService;
    @Autowired private ObjectMapper objectMapper;

    // Shared State between steps
    private String foundSku;       // Will hold the SKU of the LAST processed item
    private String foundOfferId;
    private CartContext cartData;
    private EstimateShipMethodResponse selectedMethod;
    private JsonNode rateResponse;
    private String placedOrderNumber;

    // Encryption Constants
    private static final String CARD_NUMBER_RAW = "4111111111111111";
    private static final String CARD_EXP_MONTH = "02";
    private static final String CARD_EXP_YEAR = "2035";
    private static final String CARD_CVV = "345";

    @Given("I initialize the FedEx session")
    public void iInitializeTheFedExSession() {
        log.info("--- [Step] Initializing Session ---");
        sessionService.bootstrapSession();
        assertNotNull(sessionService.getFormKey(), "Form Key must be extracted");
    }

    /**
     * NEW STEP: Handles multiple products from a Cucumber DataTable.
     * Iterates through the list and performs Search -> Offer -> Add for each.
     */
    @When("I add the following products to the cart:")
    public void iAddTheFollowingProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            String quantity = row.get("quantity");

            log.info("--- Processing Item: {} (Qty: {}) ---", productName, quantity);

            // 1. Search Logic
            foundSku = catalogService.searchProductSku(productName);
            assertNotNull(foundSku, "SKU should not be null for product: " + productName);

            // 2. Offer Logic
            foundOfferId = offerService.getOfferIdForProduct(foundSku);
            assertNotNull(foundOfferId, "Offer ID should not be null for SKU: " + foundSku);

            // 3. Add to Cart Logic
            cartService.addToCart(foundSku, quantity, foundOfferId);
        }

        log.info("--- All items added to cart. Last SKU: {} ---", foundSku);
    }

    // --- Legacy Single-Step Definitions (Optional: Kept for backward compatibility) ---
    @When("I search for the product {string}")
    public void iSearchForTheProduct(String productName) {
        foundSku = catalogService.searchProductSku(productName);
        assertNotNull(foundSku, "SKU should not be null");
    }

    @And("I fetch the dynamic Offer ID from Mirakl")
    public void iFetchTheDynamicOfferIDFromMirakl() {
        foundOfferId = offerService.getOfferIdForProduct(foundSku);
    }

    @And("I add {string} quantity of the product to the cart")
    public void iAddQuantityOfTheProductToTheCart(String qty) {
        cartService.addToCart(foundSku, qty, foundOfferId);
    }
    // ---------------------------------------------------------------------------------

    @And("I scrape the cart context data")
    public void iScrapeTheCartContextData() {
        log.info("--- [Step] Scrape Cart Context ---");
        // Uses the 'foundSku' (last item added) to find the quote ID.
        // This is safe because Quote ID is global to the cart.
        cartData = cartService.scrapeCartContext(foundSku);
        assertNotNull(cartData.getQuoteId(), "Quote ID should not be null");
        log.info("Cart Scraped. QuoteID: {}", cartData.getQuoteId());
    }

    @And("I estimate shipping methods and select {string}")
    public void iEstimateShippingMethodsAndSelect(String methodCode) {
        log.info("--- [Step] Estimate Shipping ---");
        EstimateShippingRequest estimateReq = TestDataFactory.createEstimateRequest();
        EstimateShipMethodResponse[] methods = checkoutService.estimateShipping(cartData.getMaskedQuoteId(), estimateReq);

        selectedMethod = null;
        for (EstimateShipMethodResponse m : methods) {
            if (m.getMethodCode().equals(methodCode)) {
                selectedMethod = m;
                break;
            }
        }
        // Fallback to first available if preferred method not found
        if (selectedMethod == null && methods.length > 0) selectedMethod = methods[0];

        assertNotNull(selectedMethod, "Could not find shipping method: " + methodCode);
        assertEquals(methodCode, selectedMethod.getMethodCode());
    }

    @And("I retrieve the delivery rate")
    public void iRetrieveTheDeliveryRate() {
        log.info("--- [Step] Delivery Rate ---");
        DeliveryRateRequestForm rateForm = TestDataFactory.createRateForm(selectedMethod);
        rateResponse = checkoutService.getDeliveryRate(rateForm);
        assertNotNull(rateResponse.get("rateQuote"), "Response must contain 'rateQuote'");
    }

    @And("I create a quote")
    public void iCreateAQuote() {
        log.info("--- [Step] Create Quote ---");
        checkoutService.createQuote(TestDataFactory.buildQuotePayload(rateResponse, selectedMethod));
    }

    @And("I validate the pay rate API")
    public void iValidateThePayRateAPI() {
        log.info("--- [Step] Pay Rate API ---");
        checkoutService.callPayRate();
    }

    @Then("I submit the order using a secure credit card")
    public void iSubmitTheOrderUsingASecureCreditCard() throws Exception {
        log.info("--- [Step] Submit Order ---");
        String publicKey = checkoutService.fetchEncryptionKey();

        String encryptedCard = FedExEncryptionUtil.encryptCreditCard(
                CARD_NUMBER_RAW, CARD_EXP_MONTH, CARD_EXP_YEAR, CARD_CVV, publicKey
        );
        String rawEncrypted = URLDecoder.decode(encryptedCard, StandardCharsets.UTF_8);

        SubmitOrderRequest orderRequest = TestDataFactory.createOrderRequest(rawEncrypted);
        String responseBody = checkoutService.submitOrder(orderRequest, cartData.getQuoteId());

        JsonNode submitRoot = objectMapper.readTree(responseBody);
        if (submitRoot.has("unified_data_layer")) {
            placedOrderNumber = submitRoot.path("unified_data_layer").path("orderNumber").asText();
        } else {
            fail("Order Submission Failed: " + responseBody);
        }
    }

    @And("the order should be placed successfully with a generated Order Number")
    public void theOrderShouldBePlacedSuccessfully() {
        log.info("Order Placed Successfully! Order Number: {}", placedOrderNumber);
        assertNotNull(placedOrderNumber);
        assertFalse(placedOrderNumber.isEmpty());
    }

    @And("I trigger the order export to Mirakl")
    public void iTriggerTheOrderExportToMirakl() {
        log.info("--- [Step] Send Order to Mirakl ---");
        miraklAdminTriggerService.triggerSendToMirakl(placedOrderNumber);
    }
}