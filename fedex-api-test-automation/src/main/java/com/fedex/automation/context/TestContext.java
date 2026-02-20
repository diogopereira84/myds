package com.fedex.automation.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fedex.automation.model.fedex.CartContext;
import com.fedex.automation.model.fedex.EstimateShipMethodResponse;
import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.stereotype.Component;

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

    // --- NEW: Document 1P Flow Tracking ---
    private String sessionId;
    private String originalDocId;
    private String printReadyDocId;
    private String configuratorStateId;
    private String configuratorPayload; // Stores the full JSON needed for Add to Cart
}