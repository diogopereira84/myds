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
    private CartContext cartData;
    private EstimateShipMethodResponse selectedShippingMethod;
    private JsonNode rateResponse;
    private String placedOrderNumber;

    /**
     * Stores the full 'output.checkout' JSON node from the submit order response.
     * This acts as the Source of Truth for BDD verification steps.
     */
    private JsonNode checkoutDetails;
}