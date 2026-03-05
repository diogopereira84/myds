package com.fedex.automation.service.search;

import com.fedex.automation.enums.Vendor;

public interface VendorSearchStrategy {
    /**
     * Identifies which vendor this strategy applies to.
     */
    Vendor getVendor();

    /**
     * The specific implementation for how this vendor searches for products.
     */
    void searchProduct(String productName);
}