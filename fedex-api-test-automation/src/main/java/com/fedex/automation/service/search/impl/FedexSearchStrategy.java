package com.fedex.automation.service.search.impl;

import com.fedex.automation.context.TestContext;
import com.fedex.automation.enums.Vendor;
import com.fedex.automation.service.fedex.CatalogService;
import com.fedex.automation.service.search.VendorSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FedexSearchStrategy implements VendorSearchStrategy {

    private final CatalogService catalogService;
    private final TestContext testContext;

    @Override
    public Vendor getVendor() {
        return Vendor.FEDEX;
    }

    @Override
    public void searchProduct(String productName) {
        log.info("Executing Fedex-specific search for: {}", productName);

        // Fedex is a 1P vendor
        String sku = catalogService.searchProductSku(productName, "1P");

        // Store it in the decoupled context list
        TestContext.ProductItemContext itemContext = new TestContext.ProductItemContext();
        itemContext.setProductName(productName);
        itemContext.setSku(sku);
        testContext.getSearchedProducts().add(itemContext);

        log.info("Fedex Search Complete. Saved SKU [{}] to context for product [{}]", sku, productName);
    }
}