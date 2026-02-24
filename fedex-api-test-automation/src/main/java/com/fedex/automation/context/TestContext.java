// src/main/java/com/fedex/automation/context/TestContext.java
package com.fedex.automation.context;

import io.restassured.response.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.model.fedex.CartContext;
import com.fedex.automation.model.fedex.EstimateShipMethodResponse;
import com.fedex.automation.model.fedex.product.StaticProductResponse.StaticProduct;
import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ScenarioScope
public class TestContext {

    private String currentSku;
    private String currentOfferId;
    private String sellerModel;

    private CartContext cartData;
    private EstimateShipMethodResponse selectedShippingMethod;
    private JsonNode rateResponse;
    private String placedOrderNumber;
    private JsonNode checkoutDetails;
    private JsonNode unifiedDataLayer;

    // --- 1P Flow Tracking (The 6 Steps) ---
    private String sessionId; // Step 1
    private String originalDocId; // Step 3
    private String printReadyDocId; // Step 3
    private String configuratorStateId; // Step 2
    private String configuratorPayload; // Stored for Step 6
    private ObjectNode currentConfiguredProductNode; // Stored for Steps 4 & 5 (Rates)

    // Dynamic 1P Flow
    private String currentProductId;
    private StaticProduct staticProductDetails;

    // Decoupled Search & Add Tracking
    private List<ProductItemContext> searchedProducts = new ArrayList<>();

    private Response lastResponse;

    @Data
    public static class ProductItemContext {
        private String productName;
        private String sku;
        private String offerId;
        private String sellerModel;
    }
}