package com.fedex.automation.service.fedex.strategy;

import com.fedex.automation.model.fedex.CatalogItemCandidate;
import com.fedex.automation.service.fedex.SellerModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for 1P (FedEx Office) Products.
 * ID: "1P"
 * Criteria: product.name must EXACTLY match the targetProductName (case-insensitive).
 */
@Slf4j
@Component
public class OnePProductFilter implements ProductFilterStrategy {

    @Override
    public boolean isValid(CatalogItemCandidate item, String targetProductName) {
        String actualName = item.name();
        boolean match = actualName != null && actualName.equalsIgnoreCase(targetProductName);

        if (match) {
            log.info("1P Strategy Match: '{}' matches target '{}'", actualName, targetProductName);
        }

        return match;
    }

    @Override
    public SellerModel supportedModel() {
        return SellerModel.ONE_P;
    }
}