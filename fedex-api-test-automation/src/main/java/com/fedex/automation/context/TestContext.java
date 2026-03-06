package com.fedex.automation.context;

import com.fedex.automation.model.printful.AuthNonceResponse;
import com.fedex.automation.model.printful.PrintfulVariant;
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
import java.util.Map;

@Data
@Component
@ScenarioScope
public class TestContext {

    private String currentSku;
    private String currentOfferId;
    private String sellerModel;
    private String shopId;
    private String shopSku;

    private CartContext cartData;
    private EstimateShipMethodResponse selectedShippingMethod;
    private JsonNode rateResponse;
    private String placedOrderNumber;
    private JsonNode checkoutDetails;
    private JsonNode unifiedDataLayer;
    // Add this to your Printful Flow Tracking section
    private AuthNonceResponse authNonceResponse;
    private String printfulTemporaryFileKey;

    // --- Dynamic BDD Data ---
    private Map<String, String> shippingAddress;
    private Map<String, String> paymentDetails;

    // --- 1P Flow Tracking (The 6 Steps) ---
    private String sessionId;
    private String originalDocId;
    private String printReadyDocId;
    private String configuratorStateId;
    private String configuratorPayload;
    private ObjectNode currentConfiguredProductNode;

    // Dynamic 1P Flow
    private String currentProductId;
    private StaticProduct staticProductDetails;
    private String currentProductVersion;

    // --- Printful Flow Tracking ---
    private String printfulSessionId;
    private String externalProductId;
    private String printfulPhpSessIdCookie;
    private String printfulFormKeyCookie;

    // --- Printful Checkout State ---
    private String printfulProductId; // e.g., "146"
    private String printfulSelectedColor; // e.g., "Gold"
    private Map<String, Integer> printfulSelectedQuantities; // e.g., {"S": 1, "2XL": 2}
    private List<PrintfulVariant> printfulFinalCheckoutVariants;
    private String printfulSelectedTechnique; // e.g., "dtg"

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

    // Helper method to resolve SKU from previously searched products
    public String getSkuByProductName(String productName) {
        return searchedProducts.stream()
                .filter(p -> productName.equals(p.getProductName()))
                .map(ProductItemContext::getSku)
                .findFirst()
                .orElse(null);
    }
}