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
public class NavitorSearchStrategy implements VendorSearchStrategy {

    private final CatalogService catalogService;
    private final TestContext testContext;

    @Override
    public Vendor getVendor() {
        return Vendor.NAVITOR;
    }

    @Override
    public void searchProduct(String productName) {
        log.info("Executing Navitor-specific search for: {}", productName);

        // Navitor is a 3P vendor, so we pass "3P" to the CatalogService
        String sku = catalogService.searchProductSku(productName, "3P");

        // Store it in the decoupled context list
        TestContext.ProductItemContext itemContext = new TestContext.ProductItemContext();
        itemContext.setProductName(productName);
        itemContext.setSku(sku);
        testContext.getSearchedProducts().add(itemContext);

        log.info("Navitor Search Complete. Saved SKU [{}] to context for product [{}]", sku, productName);
    }
}