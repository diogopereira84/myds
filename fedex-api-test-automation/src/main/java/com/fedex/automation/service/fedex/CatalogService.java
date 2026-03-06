package com.fedex.automation.service.fedex;

import com.fedex.automation.model.fedex.CatalogItemCandidate;
import com.fedex.automation.model.graphql.GraphqlRequestBody;
import com.fedex.automation.service.fedex.client.CatalogApiClient;
import com.fedex.automation.service.fedex.exception.CatalogErrorCode;
import com.fedex.automation.service.fedex.exception.CatalogOperationException;
import com.fedex.automation.service.fedex.parser.CatalogResponseParser;
import com.fedex.automation.service.fedex.query.CatalogQueryBuilder;
import com.fedex.automation.service.fedex.strategy.ProductFilterStrategy;
import com.fedex.automation.service.fedex.strategy.ProductFilterStrategyRegistry;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final SellerModel DEFAULT_SELLER_MODEL = SellerModel.THREE_P;

    private final CatalogApiClient apiClient;
    private final RequestSpecification defaultRequestSpec;
    private final CatalogQueryBuilder queryBuilder;
    private final CatalogResponseParser responseParser;
    private final ProductFilterStrategyRegistry strategyRegistry;

    /**
     * Searches for a product SKU using the specified Seller Model strategy.
     * @param productName The product name to search for.
     * @param sellerModel "1P" or "3P". Defaults to "3P" if null.
     * @return The found SKU.
     */
    public String searchProductSku(String productName, String sellerModel) {
        validateProductName(productName);

        SellerModel model = normalizeSellerModel(sellerModel);
        log.info("--- Searching Catalog for '{}' using Strategy: {} ---", productName, model.externalValue());

        ProductFilterStrategy strategy = strategyRegistry.getOrThrow(model);
        GraphqlRequestBody requestBody = queryBuilder.buildProductSearchRequest(productName);
        String responseBody = apiClient.requestProductSearchBody(requestBody, defaultRequestSpec);
        List<CatalogItemCandidate> candidates = responseParser.extractCandidatesOrThrow(responseBody, productName);

        return resolveSku(candidates, productName, strategy);
    }

    // Overload for backward compatibility (defaults to 3P)
    public String searchProductSku(String productName) {
        return searchProductSku(productName, DEFAULT_SELLER_MODEL.externalValue());
    }

    private String resolveSku(List<CatalogItemCandidate> candidates, String productName, ProductFilterStrategy strategy) {
        for (CatalogItemCandidate candidate : candidates) {
            if (strategy.isValid(candidate, productName)) {
                log.info("SKU Resolved: {} (Name: {})", candidate.sku(), candidate.name());
                return candidate.sku();
            }
        }

        throw new CatalogOperationException(
                CatalogErrorCode.NO_STRATEGY_MATCH,
                "Products found, but none matched criteria for strategy: " + strategy.getClass().getSimpleName()
        );
    }

    private void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CatalogOperationException(CatalogErrorCode.INVALID_INPUT, "productName must be provided");
        }
    }

    private SellerModel normalizeSellerModel(String sellerModel) {
        try {
            return SellerModel.fromNullableInput(sellerModel);
        } catch (IllegalArgumentException ex) {
            throw new CatalogOperationException(CatalogErrorCode.INVALID_INPUT, ex.getMessage(), ex);
        }
    }
}