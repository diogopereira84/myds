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

    // NEW: Tracks if we are in a 1P or 3P flow
    private String sellerModel;

    private CartContext cartData;
    private EstimateShipMethodResponse selectedShippingMethod;
    private JsonNode rateResponse;
    private String placedOrderNumber;
    private JsonNode checkoutDetails;
    private JsonNode unifiedDataLayer;
}