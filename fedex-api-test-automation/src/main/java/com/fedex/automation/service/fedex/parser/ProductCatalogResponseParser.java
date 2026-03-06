package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.model.fedex.product.MenuHierarchyResponse;
import com.fedex.automation.model.fedex.product.StaticProductResponse;
import com.fedex.automation.service.fedex.exception.ProductCatalogErrorCode;
import com.fedex.automation.service.fedex.exception.ProductCatalogOperationException;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogResponseParser {

    private final ObjectMapper objectMapper;

    public ProductCatalogResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MenuHierarchyResponse parseMenuHierarchy(String responseBody) {
        try {
            MenuHierarchyResponse menuHierarchy = objectMapper.readValue(responseBody, MenuHierarchyResponse.class);
            if (menuHierarchy == null || menuHierarchy.getProductMenuDetails() == null) {
                throw new ProductCatalogOperationException(
                        ProductCatalogErrorCode.NO_MENU_DETAILS,
                        "Menu hierarchy response missing productMenuDetails."
                );
            }
            return menuHierarchy;
        } catch (ProductCatalogOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProductCatalogOperationException(ProductCatalogErrorCode.PARSE_ERROR, "Error parsing menu hierarchy", ex);
        }
    }

    public StaticProductResponse parseProductDetails(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, StaticProductResponse.class);
        } catch (Exception ex) {
            throw new ProductCatalogOperationException(ProductCatalogErrorCode.PARSE_ERROR, "Error parsing product details", ex);
        }
    }
}

