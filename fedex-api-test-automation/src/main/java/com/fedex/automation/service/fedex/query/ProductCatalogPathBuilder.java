package com.fedex.automation.service.fedex.query;

import com.fedex.automation.service.fedex.exception.ProductCatalogErrorCode;
import com.fedex.automation.service.fedex.exception.ProductCatalogOperationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogPathBuilder {

    @Value("${endpoint.product.menu.hierarchy}")
    private String menuHierarchyEndpoint;

    @Value("${endpoint.product.details}")
    private String productDetailsEndpoint;

    public String menuHierarchyPath() {
        return menuHierarchyEndpoint;
    }

    public String buildProductDetailsPath(String productId, String version) {
        if (productId == null || productId.isBlank()) {
            throw new ProductCatalogOperationException(
                    ProductCatalogErrorCode.INVALID_REQUEST,
                    "productId must be provided to fetch product details."
            );
        }

        String versionSuffix = (version != null && !version.trim().isEmpty()) ? "-" + version : "";

        String path = productDetailsEndpoint
                .replace("{productId}", productId)
                .replace("{version}", versionSuffix);

        if (path.contains("-v2.json") && !"-v2".equals(versionSuffix)) {
            path = path.replace("-v2.json", versionSuffix + ".json");
        }

        return path;
    }

    public String normalizeSkuBaseId(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ProductCatalogOperationException(
                    ProductCatalogErrorCode.INVALID_REQUEST,
                    "SKU must be provided to resolve menu hierarchy."
            );
        }

        String normalizedId = sku.split("-", 2)[0];
        if (normalizedId.isBlank()) {
            throw new ProductCatalogOperationException(
                    ProductCatalogErrorCode.INVALID_REQUEST,
                    "SKU normalization produced an empty base id: " + sku
            );
        }

        return normalizedId;
    }
}

