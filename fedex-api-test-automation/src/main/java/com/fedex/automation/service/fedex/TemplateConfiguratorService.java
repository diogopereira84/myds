package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.context.TestContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateConfiguratorService {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final TestContext testContext;

    private static final String API_GATEWAY_CLIENT_ID = "l735d628c13a154cc2abab4ecc50fe0558";

    public void createConfiguratorState(String productName, Map<String, String> bddFeatures) throws Exception {
        String templateName = "templates/1P_" + productName.replaceAll("\\s+", "") + "_Template.json";
        log.info("Loading JSON Template: {}", templateName);

        InputStream is = getClass().getClassLoader().getResourceAsStream(templateName);
        if (is == null) {
            throw new IllegalArgumentException("Could not find template file: " + templateName);
        }

        ObjectNode payload = (ObjectNode) objectMapper.readTree(is);

        // This inner object is EXACTLY what the Cart Add endpoint expects
        ObjectNode configParams = (ObjectNode) payload.path("configuratorStateParameters");

        String stateId = UUID.randomUUID().toString();
        String sessionId = testContext.getSessionId() != null ? testContext.getSessionId() : UUID.randomUUID().toString();

        // Inject dynamic IDs
        configParams.put("configuratorStateId", stateId);
        configParams.put("configuratorSessionId", sessionId);

        linkDocumentsToState(configParams);
        applyDynamicFeatures((ArrayNode) configParams.at("/product/features"), bddFeatures);

        log.info("Submitting Configurator State to API...");
        Response response = sessionService.authenticatedRequest()
                .baseUri("https://api.test.office.fedex.com")
                .header("client_id", API_GATEWAY_CLIENT_ID)
                .queryParam("ClientName", "POD2.0")
                .contentType(ContentType.JSON)
                .body(payload.toString())
                .post("/application/fedexoffice/v2/configuratorstates");

        assertEquals(200, response.statusCode(), "Failed to create configurator state.");

        // SAVE IDs AND THE FULL JSON PAYLOAD NEEDED FOR THE CART
        testContext.setConfiguratorStateId(stateId);
        testContext.setConfiguratorPayload(configParams.toString());

        log.info("Configurator State created successfully. State ID: {}", stateId);
    }

    private void linkDocumentsToState(ObjectNode configParams) {
        String originalDocId = testContext.getOriginalDocId();
        String printReadyDocId = testContext.getPrintReadyDocId();

        if (originalDocId != null && printReadyDocId != null) {
            ArrayNode contentAssoc = (ArrayNode) configParams.at("/product/contentAssociations");
            if (!contentAssoc.isMissingNode() && contentAssoc.size() > 0) {
                ObjectNode firstAssoc = (ObjectNode) contentAssoc.get(0);
                firstAssoc.put("parentContentReference", originalDocId);
                firstAssoc.put("contentReference", printReadyDocId);
            }

            ArrayNode files = (ArrayNode) configParams.at("/userWorkspace/files");
            if (!files.isMissingNode() && files.size() > 0) {
                ((ObjectNode) files.get(0)).put("id", originalDocId);
            }
        }
    }

    private void applyDynamicFeatures(ArrayNode featuresArray, Map<String, String> bddFeatures) {
        if (bddFeatures == null || bddFeatures.isEmpty() || featuresArray.isMissingNode()) return;

        for (JsonNode featureNode : featuresArray) {
            String featureName = featureNode.get("name").asText();
            if (bddFeatures.containsKey(featureName)) {
                ObjectNode choiceNode = (ObjectNode) featureNode.get("choice");
                String newChoiceName = bddFeatures.get(featureName);
                log.info("Overriding template feature: [{}] -> [{}]", featureName, newChoiceName);
                choiceNode.put("name", newChoiceName);
            }
        }
    }

    public void addConfiguredItemToCart() {
        String stateId = testContext.getConfiguratorStateId();
        String fullPayload = testContext.getConfiguratorPayload();

        assertNotNull(stateId, "Configurator State ID is missing.");
        assertNotNull(fullPayload, "Configurator State Payload is missing.");

        log.info("Adding Document Product to Cart [StateId: {}]...", stateId);

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                // THESE AJAX HEADERS PREVENT THE 302 REDIRECT
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Adrum", "isAjax:true")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .formParam("data", fullPayload) // PASSING THE ENTIRE JSON
                .formParam("itemId", "")
                .post("/default/cart/product/add");

        if (response.statusCode() != 200) {
            log.error("Failed to add to cart. Status: {}, Body: {}", response.statusCode(), response.body().asString());
        }

        assertEquals(200, response.statusCode(), "Failed to add configured product to cart");
        log.info("Document Product successfully added to cart! Response: {}", response.body().asString());
    }
}