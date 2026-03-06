package com.fedex.automation.service.fedex.strategy;

import com.fedex.automation.service.fedex.SellerModel;
import com.fedex.automation.service.fedex.exception.CatalogErrorCode;
import com.fedex.automation.service.fedex.exception.CatalogOperationException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductFilterStrategyRegistry {

    private final Map<SellerModel, ProductFilterStrategy> strategiesByModel;

    public ProductFilterStrategyRegistry(List<ProductFilterStrategy> strategies) {
        Map<SellerModel, ProductFilterStrategy> registry = new EnumMap<>(SellerModel.class);
        for (ProductFilterStrategy strategy : strategies) {
            ProductFilterStrategy previous = registry.put(strategy.supportedModel(), strategy);
            if (previous != null) {
                throw new CatalogOperationException(
                        CatalogErrorCode.STRATEGY_NOT_FOUND,
                        "Duplicate strategy for seller model: " + strategy.supportedModel().externalValue()
                );
            }
        }
        this.strategiesByModel = Map.copyOf(registry);
    }

    public ProductFilterStrategy getOrThrow(SellerModel model) {
        ProductFilterStrategy strategy = strategiesByModel.get(model);
        if (strategy == null) {
            throw new CatalogOperationException(
                    CatalogErrorCode.STRATEGY_NOT_FOUND,
                    "No strategy found for Seller Model: " + model.externalValue()
            );
        }
        return strategy;
    }
}

