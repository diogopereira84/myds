package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.*;
import com.fedex.automation.service.fedex.*;
import com.fedex.automation.service.mirakl.OfferService;
import com.fedex.automation.utils.FedExEncryptionUtil;
import com.fedex.automation.utils.TestDataFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
    private String foundSku;
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

    @When("I search for the product {string}")
    public void iSearchForTheProduct(String productName) {
        log.info("--- [Step] Search Product: {} ---", productName);
        foundSku = catalogService.searchProductSku(productName);
        assertNotNull(foundSku, "SKU should not be null");
    }

    @And("I fetch the dynamic Offer ID from Mirakl")
    public void iFetchTheDynamicOfferIDFromMirakl() {
        log.info("--- [Step] Get Offer ID ---");
        foundOfferId = offerService.getOfferIdForProduct(foundSku);
        assertNotNull(foundOfferId, "Offer ID should not be null");
    }

    @And("I add {string} quantity of the product to the cart")
    public void iAddQuantityOfTheProductToTheCart(String qty) {
        log.info("--- [Step] Add to Cart (Qty: {}) ---", qty);
        cartService.addToCart(foundSku, qty, foundOfferId);
    }

    @And("I scrape the cart context data")
    public void iScrapeTheCartContextData() {
        log.info("--- [Step] Scrape Cart Context ---");
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
        // Fallback or fail
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