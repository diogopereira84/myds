package com.fedex.automation.service.fedex.strategy;

import com.fedex.automation.model.fedex.CatalogItemCandidate;
import com.fedex.automation.service.fedex.SellerModel;

public interface ProductFilterStrategy {
    /**
     * Evaluates if a catalog item matches the specific seller model criteria.
     * @param item The catalog item candidate representing a single product item.
     * @param targetProductName The name requested in the test (used for exact matching in 1P).
     * @return true if valid.
     */
    boolean isValid(CatalogItemCandidate item, String targetProductName);

    SellerModel supportedModel();
}