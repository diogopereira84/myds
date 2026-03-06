package com.fedex.automation.service.fedex;

import com.fedex.automation.model.fedex.product.MenuHierarchyResponse;
import com.fedex.automation.model.fedex.product.StaticProductResponse;
import com.fedex.automation.service.fedex.client.ProductCatalogApiClient;
import com.fedex.automation.service.fedex.exception.ProductCatalogErrorCode;
import com.fedex.automation.service.fedex.exception.ProductCatalogOperationException;
import com.fedex.automation.service.fedex.parser.ProductCatalogResponseParser;
import com.fedex.automation.service.fedex.query.ProductCatalogPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogFlowService {

    private final ProductCatalogApiClient apiClient;
    private final ProductCatalogResponseParser responseParser;
    private final ProductCatalogPathBuilder pathBuilder;

    @Value("${base.url.www}")
    private String wwwBaseUrl;

    public MenuHierarchyResponse.ProductMenuDetail resolveProductMenuDetailFromSku(String sku) {
        String normalizedId = pathBuilder.normalizeSkuBaseId(sku);
        log.info("Normalized SKU [{}] to Base ID [{}]", sku, normalizedId);
        log.info("Fetching Menu Hierarchy to resolve Product ID for Base ID: {}", normalizedId);

        String responseBody = apiClient.requestWwwBody(pathBuilder.menuHierarchyPath(), "Menu hierarchy request failed");
        MenuHierarchyResponse menuHierarchy = responseParser.parseMenuHierarchy(responseBody);

        return menuHierarchy.getProductMenuDetails().stream()
                .filter(detail -> normalizedId.equals(detail.getId()))
                .findFirst()
                .orElseThrow(() -> new ProductCatalogOperationException(
                        ProductCatalogErrorCode.MENU_DETAIL_NOT_FOUND,
                        "Could not find matching menu detail for ID: " + normalizedId
                ));
    }

    public StaticProductResponse getProductDetails(String productId, String version) {
        String path = pathBuilder.buildProductDetailsPath(productId, version);

        log.info("Fetching Static Product Details for ID: {} with Version: '{}' from Domain: {}. Path: {}",
                productId, version, wwwBaseUrl, path);

        String responseBody = apiClient.requestWwwBody(path, "Product details request failed");
        return responseParser.parseProductDetails(responseBody);
    }
}