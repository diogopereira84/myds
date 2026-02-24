// src/test/java/com/fedex/automation/steps/Document1PAssertionSteps.java
package com.fedex.automation.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.service.fedex.CartService;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class Document1PAssertionSteps {

    @Autowired
    private TestContext testContext;

    @Autowired
    private CartService cartService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Then("I verify the Configurator Session was successfully created")
    public void iVerifyTheConfiguratorSessionWasSuccessfullyCreated() throws Exception {
        Response response = testContext.getLastResponse();
        assertEquals(200, response.statusCode(), "Configurator Session API failed.");

        JsonNode rootNode = mapper.readTree(response.asString());

        // Deep Verification: No backend processing errors
        JsonNode errors = rootNode.path("errors");
        assertTrue(errors.isMissingNode() || errors.isEmpty(), "Session API returned logical errors: " + errors);

        // Data Validation: Ensure URL and Session ID exist
        String configUrl = rootNode.at("/output/configuratorSession/configuratorURL").asText();
        assertFalse(configUrl.isEmpty(), "Configurator URL missing from payload!");

        String sessionId = configUrl.substring(configUrl.lastIndexOf("/") + 1);
        testContext.setSessionId(sessionId);
        log.info("Verified Configurator Session ID: {}", sessionId);
    }

    @Then("I verify the Configurator State was successfully created")
    public void iVerifyTheConfiguratorStateWasSuccessfullyCreated() throws Exception {
        Response response = testContext.getLastResponse();
        assertEquals(200, response.statusCode(), "Configurator State API failed.");

        JsonNode rootNode = mapper.readTree(response.asString());

        JsonNode errors = rootNode.path("errors");
        assertTrue(errors.isMissingNode() || errors.isEmpty(), "State API returned logical errors: " + errors);

        // The CART FIX: Extract the state directly from the response and save it to context
        JsonNode stateNode = rootNode.at("/output/configuratorState");
        assertFalse(stateNode.isMissingNode(), "output.configuratorState missing from response!");

        testContext.setConfiguratorPayload(stateNode.toString());
        log.info("Verified Configurator State and staged for Cart Add.");
    }

    @Then("I verify the configured product was added to the Magento cart")
    public void iVerifyTheConfiguredProductWasAddedToTheMagentoCart() {
        Response response = testContext.getLastResponse();
        assertEquals(200, response.statusCode(), "Add to Cart API failed.");
        log.info("Verified Product pushed to Cart payload.");
    }

    @Then("I verify the product is visible in the cart via section load")
    public void iVerifyTheProductIsVisibleInTheCartViaSectionLoad() {
        cartService.verifyItemInCart();
        log.info("Verified item count > 0 in Magento Customer Section Load.");
    }
}