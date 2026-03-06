package com.fedex.automation.service.fedex.strategy;

import com.fedex.automation.model.fedex.CatalogItemCandidate;
import com.fedex.automation.service.fedex.SellerModel;
import org.springframework.stereotype.Component;

/**
 * Strategy for 3P (Marketplace) Products.
 * ID: "3P"
 * Criteria: Attributes 'mirakl_sync' must be 'yes' and 'mirakl_shop_ids' must exist.
 */
@Component
public class ThreePProductFilter implements ProductFilterStrategy {

    @Override
    public boolean isValid(CatalogItemCandidate item, String targetProductName) {
        boolean isMiraklSync = "yes".equalsIgnoreCase(item.attribute("mirakl_sync"));
        boolean hasMiraklShopId = !item.attribute("mirakl_shop_ids").isBlank();
        return isMiraklSync && hasMiraklShopId;
    }

    @Override
    public SellerModel supportedModel() {
        return SellerModel.THREE_P;
    }
}