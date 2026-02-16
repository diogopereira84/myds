package com.fedex.automation.steps;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.service.fedex.CatalogService;
import com.fedex.automation.service.fedex.ConfiguratorService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
public class FedEx1PSteps {

    @Autowired private CatalogService catalogService;
    @Autowired private ConfiguratorService configuratorService;
    @Autowired private TestContext testContext; // Inject Context

    @When("I configure and add the following 1P products to the cart:")
    public void iConfigureAndAddTheFollowing1PProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            int quantity = Integer.parseInt(row.get("quantity"));

            log.info("--- Processing 1P Item: {} (Qty: {}) ---", productName, quantity);

            // 1. Search
            String foundSku = catalogService.search1PProduct(productName);
            assertNotNull(foundSku, "SKU should not be null for 1P product: " + productName);
            log.info("Found 1P SKU: {}", foundSku);

            // 2. Resolve Partner ID
            String partnerProductId = resolvePartnerId(productName);

            // 3. Add to Cart
            configuratorService.add1PConfiguredItemToCart(foundSku, partnerProductId, quantity);

            // 4. UPDATE SHARED STATE
            testContext.setCurrentSku(foundSku);
        }
    }

    private String resolvePartnerId(String productName) {
        if (productName.equalsIgnoreCase("Flyers")) {
            return "CVAFLY1020";
        }
        return "CVAFLY1020";
    }
}