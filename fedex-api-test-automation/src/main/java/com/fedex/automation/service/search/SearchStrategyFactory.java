package com.fedex.automation.service.search;

import com.fedex.automation.enums.Vendor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchStrategyFactory {

    private final Map<Vendor, VendorSearchStrategy> strategies;

    // Spring automatically injects a List of all classes that implement VendorSearchStrategy!
    public SearchStrategyFactory(List<VendorSearchStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(VendorSearchStrategy::getVendor, strategy -> strategy));
    }

    public VendorSearchStrategy getStrategy(Vendor vendor) {
        VendorSearchStrategy strategy = strategies.get(vendor);
        if (strategy == null) {
            throw new IllegalArgumentException("No search strategy implemented for Vendor: " + vendor);
        }
        return strategy;
    }
}