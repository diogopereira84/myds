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
public class FedExSteps {

    @Autowired private CatalogService catalogService;
    @Autowired private ConfiguratorService configuratorService;
    @Autowired private TestContext testContext;

    @When("I configure and add the following 1P products to the cart:")
    public void iConfigureAndAddTheFollowing1PProductsToTheCart(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String productName = row.get("productName");
            int quantity = Integer.parseInt(row.get("quantity"));

            // Check if sellerModel is provided in table, else default to 1P
            String sellerModel = row.getOrDefault("sellerModel", "1P");

            log.info("--- Processing Item: {} (Model: {}) ---", productName, sellerModel);

            // 1. Search using 1P Strategy
            String foundSku = catalogService.searchProductSku(productName, sellerModel);
            assertNotNull(foundSku, "SKU should not be null for product: " + productName);

            // 2. Resolve Partner ID (Logic remains same)
            String partnerProductId = resolvePartnerId(productName);

            // 3. Add to Cart
            configuratorService.add1PConfiguredItemToCart(foundSku, partnerProductId, quantity);

            // 4. Update Shared State
            testContext.setCurrentSku(foundSku);
        }
    }

    private String resolvePartnerId(String productName) {
        // Mapping logic...
        return "CVAFLY1020";
    }
}