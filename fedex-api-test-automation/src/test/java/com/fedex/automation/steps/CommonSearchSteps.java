package com.fedex.automation.steps;

import com.fedex.automation.enums.Vendor;
import com.fedex.automation.service.search.SearchStrategyFactory;
import com.fedex.automation.service.search.VendorSearchStrategy;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class CommonSearchSteps {

    private final SearchStrategyFactory searchStrategyFactory;

    // The Regex automatically matches the Enum values!
    @When("^I search for the following (PRINTFUL|ESSENDANT|COMPANYBOX|FEDEX|NAVITOR) products:$")
    public void iSearchForTheFollowingVendorProducts(Vendor vendor, DataTable dataTable) {
        log.info("--- Initiating {} Product Search ---", vendor.name());

        // Get the correct implementation dynamically
        VendorSearchStrategy searchStrategy = searchStrategyFactory.getStrategy(vendor);

        // Extract products and execute the search
        List<Map<String, String>> products = dataTable.asMaps(String.class, String.class);
        for (Map<String, String> product : products) {
            String productName = product.get("category");

            // Delegate the actual search logic to the specific vendor strategy
            searchStrategy.searchProduct(productName);
        }
    }
}