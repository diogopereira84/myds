package com.fedex.automation.steps;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.service.fedex.CartService;
import com.fedex.automation.service.fedex.CatalogService;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.mirakl.OfferService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
public class EssendantSteps {

    @Autowired private SessionService sessionService;
    @Autowired private CatalogService catalogService;
    @Autowired private CartService cartService;
    @Autowired private OfferService offerService;
    @Autowired private TestContext testContext;

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

            log.info("--- Processing Essendant 3P Item: {} (Qty: {}) ---", productName, quantity);

            // 1. Search Logic
            String sku = catalogService.searchProductSku(productName);
            assertNotNull(sku, "SKU not found for: " + productName);

            // 2. Offer Logic
            String offerId = offerService.getOfferIdForProduct(sku);

            // 3. Add to Cart Logic
            cartService.addToCart(sku, quantity, offerId);

            // 4. Update Shared State
            testContext.setCurrentSku(sku);
            testContext.setCurrentOfferId(offerId);
        }
    }
}