package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.product.MenuHierarchyResponse;
import com.fedex.automation.model.fedex.product.StaticProductResponse;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogFlowService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Value("${base.url.www}")
    private String wwwBaseUrl;

    @Value("${endpoint.product.menu.hierarchy}")
    private String menuHierarchyEndpoint;

    @Value("${endpoint.product.details}")
    private String productDetailsEndpoint;

    public MenuHierarchyResponse.ProductMenuDetail resolveProductMenuDetailFromSku(String sku) {
        String normalizedId = sku.split("-")[0];
        log.info("Normalized SKU [{}] to Base ID [{}]", sku, normalizedId);
        log.info("Fetching Menu Hierarchy to resolve Product ID for Base ID: {}", normalizedId);

        Response response = sessionService.authenticatedRequest()
                .baseUri(wwwBaseUrl)
                .header("accept", "*/*")
                .header("sec-fetch-site", "same-site")
                .get(menuHierarchyEndpoint);

        response.then().statusCode(200);

        try {
            MenuHierarchyResponse menuHierarchy = objectMapper.readValue(response.asString(), MenuHierarchyResponse.class);

            MenuHierarchyResponse.ProductMenuDetail matchedDetail = menuHierarchy.getProductMenuDetails().stream()
                    .filter(detail -> normalizedId.equals(detail.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find matching menu detail for ID: " + normalizedId));

            log.info("Successfully resolved Product ID [{}] and Version [{}] from Menu Details.",
                    matchedDetail.getProductId(), matchedDetail.getVersion());

            return matchedDetail;

        } catch (Exception e) {
            log.error("Failed to parse Menu Hierarchy Response.", e);
            throw new RuntimeException("Error parsing menu hierarchy", e);
        }
    }

    public StaticProductResponse getProductDetails(String productId, String version) {
        // Construct the suffix. If version is "v2", suffix becomes "-v2". If null/empty, it becomes ""
        String versionSuffix = (version != null && !version.trim().isEmpty()) ? "-" + version : "";

        String path = productDetailsEndpoint
                .replace("{productId}", productId)
                .replace("{version}", versionSuffix);

        // Fallback safety in case the application.properties wasn't updated correctly
        if (path.contains("-v2.json") && !versionSuffix.equals("-v2")) {
            path = path.replace("-v2.json", versionSuffix + ".json");
        }

        log.info("Fetching Static Product Details for ID: {} with Version: '{}' from Domain: {}. Path: {}",
                productId, version, wwwBaseUrl, path);

        Response response = sessionService.authenticatedRequest()
                .baseUri(wwwBaseUrl)
                .header("accept", "*/*")
                .header("sec-fetch-site", "same-site")
                .get(path);

        response.then().statusCode(200);

        try {
            return objectMapper.readValue(response.asString(), StaticProductResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse StaticProductResponse. Body: {}", response.asString());
            throw new RuntimeException("Error parsing product details", e);
        }
    }
}