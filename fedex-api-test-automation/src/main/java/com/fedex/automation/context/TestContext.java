package com.fedex.automation.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fedex.automation.model.fedex.CartContext;
import com.fedex.automation.model.fedex.EstimateShipMethodResponse;
import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * Shared container for test state.
 * Solves the "Null SKU" issue by allowing different Step classes
 * to share data for the current running Scenario.
 */
@Data
@Component
@ScenarioScope
public class TestContext {

    // The SKU of the item currently being tested
    private String currentSku;

    // The Offer ID (specific to 3P/Mirakl items)
    private String currentOfferId;

    // Data scraped from the Cart page (Quote ID, Item ID, etc.)
    private CartContext cartData;

    // The shipping method selected by the test
    private EstimateShipMethodResponse selectedShippingMethod;

    // Response from the Rate API
    private JsonNode rateResponse;

    // The final Order Number generated
    private String placedOrderNumber;
}