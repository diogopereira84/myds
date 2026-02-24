// src/main/java/com/fedex/automation/service/fedex/TemplateConfiguratorService.java
package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.context.TestContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateConfiguratorService {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final TestContext testContext;

    @Value("${fedex.api.gateway.base-uri}")
    private String apiGatewayUri;

    public void createConfiguratorSession() {
        String sku = testContext.getCurrentSku();
        String productId = testContext.getCurrentProductId();
        log.info("--- [Action] Creating Configurator Session for SKU: {} ---", sku);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("templates/1P_ConfiguratorSessionsTemplate.json");
            if (is == null) throw new IllegalArgumentException("Template file not found.");
            String rawPayload = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String finalPayload = rawPayload
                    .replace("\"1534436209752-4-3\"", "\"" + sku + "\"")
                    .replace("\"1456773326927\"", "\"" + productId + "\"");

            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(finalPayload);
            JsonNode productNode = payloadNode.at("/configuratorSessionParameters/configuratorOptions/product");
            testContext.setCurrentConfiguredProductNode((ObjectNode) productNode.deepCopy());

            Response response = sessionService.authenticatedRequest()
                    .baseUri(apiGatewayUri)
                    .header(FedExConstants.HEADER_CLIENT_ID, FedExConstants.MAGENTO_UI_CLIENT_ID)
                    .header("Origin", sessionService.getBaseUrl())
                    .header("Referer", sessionService.getBaseUrl() + "/")
                    .contentType(ContentType.JSON)
                    .body(finalPayload)
                    .post(FedExConstants.ENDPOINT_CONFIG_SESSIONS);

            testContext.setLastResponse(response);

        } catch (Exception e) {
            fail("Error executing Configurator Session creation: " + e.getMessage());
        }
    }

    public void searchConfiguratorSession() {
        String sessionId = testContext.getSessionId();
        log.info("--- [Action] Searching Configurator Session for ID: {} ---", sessionId);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.putObject("configuratorSessionSearchCriteria").put("configuratorSessionId", sessionId);

        Response response = sessionService.authenticatedRequest()
                .baseUri(apiGatewayUri)
                .header(FedExConstants.HEADER_CLIENT_ID, FedExConstants.API_GATEWAY_CLIENT_ID)
                .contentType(ContentType.JSON)
                .body(payload.toString())
                .post(FedExConstants.ENDPOINT_CONFIG_SEARCH);

        testContext.setLastResponse(response);
    }

    public void createConfiguratorState(String templatePrefix, Map<String, String> bddFeatures) throws Exception {
        log.info("--- [Action] Creating Configurator State ---");

        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode configParams = payload.putObject("configuratorStateParameters");

        String stateId = UUID.randomUUID().toString();
        configParams.put("configuratorStateId", stateId);
        configParams.put("configuratorSessionId", testContext.getSessionId());

        if (testContext.getCurrentSku() != null) {
            configParams.put("integratorProductReference", testContext.getCurrentSku());
        }

        ObjectNode productNode = testContext.getCurrentConfiguredProductNode().deepCopy();

        applyDynamicFeatures((ArrayNode) productNode.get("features"), bddFeatures);
        linkDocumentsToStateProductNode(productNode, configParams);

        configParams.set("product", productNode);

        Response response = sessionService.authenticatedRequest()
                .baseUri(apiGatewayUri)
                .header(FedExConstants.HEADER_CLIENT_ID, FedExConstants.API_GATEWAY_CLIENT_ID)
                .queryParam(FedExConstants.PARAM_CLIENT_NAME, FedExConstants.INTEGRATOR_ID_POD2)
                .contentType(ContentType.JSON)
                .body(payload.toString())
                .post(FedExConstants.ENDPOINT_CONFIG_STATES);

        testContext.setLastResponse(response);
        testContext.setConfiguratorStateId(stateId);
    }

    public void addConfiguredItemToCart(int quantity) {
        String stateId = testContext.getConfiguratorStateId();
        String fullPayload = testContext.getConfiguratorPayload();

        log.info("--- [Action] Adding Document Product to Cart [StateId: {}, Qty: {}] ---", stateId, quantity);

        try {
            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(fullPayload);

            // EXACT UI MIMICKING: Remove metadata rejected by the Cart API
            payloadNode.remove("configuratorStateId");
            payloadNode.remove("expirationDateTime");
            payloadNode.remove("isEditable");

            // EXACT UI MIMICKING: Inject empty errors array
            payloadNode.putArray("errors");

            JsonNode productNode = payloadNode.path("product");
            if (productNode.isObject()) {
                ObjectNode pNode = (ObjectNode) productNode;

                // EXACT UI MIMICKING: Force strict Strings to prevent Magento Deserializer 503 Crashes
                pNode.put("qty", String.valueOf(quantity));

                if (pNode.has("id")) {
                    pNode.put("id", pNode.get("id").asText());
                }

                // EXACT UI MIMICKING: Inject user product name
                pNode.put("userProductName", "SimpleText");

                // Fix nested contentReqId data types
                JsonNode contentAssoc = pNode.path("contentAssociations");
                if (contentAssoc.isArray() && contentAssoc.size() > 0) {
                    JsonNode assocNode = contentAssoc.get(0);
                    if (assocNode.has("contentReqId")) {
                        ((ObjectNode) assocNode).put("contentReqId", assocNode.get("contentReqId").asText());
                    }
                }
            }

            String urlEncodedData = URLEncoder.encode(payloadNode.toString(), StandardCharsets.UTF_8.toString());
            String rawBody = "data=" + urlEncodedData + "&itemId=";

            String refererUrl = sessionService.getBaseUrl() + "/default/configurator/index/index/responseid/" + stateId;

            Response response = sessionService.authenticatedRequest()
                    .baseUri(sessionService.getBaseUrl()) // Route exactly to staging2.office.fedex.com
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Adrum", "isAjax:true")
                    .header("Referer", refererUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .body(rawBody)
                    .post("/default/cart/product/add");

            testContext.setLastResponse(response);

        } catch (Exception e) {
            fail("Failed to push item to cart: " + e.getMessage());
        }
    }

    private void linkDocumentsToStateProductNode(ObjectNode productNode, ObjectNode configParams) {
        String originalDocId = testContext.getOriginalDocId();
        String printReadyDocId = testContext.getPrintReadyDocId();

        if (originalDocId != null && printReadyDocId != null) {
            ArrayNode contentAssoc = productNode.withArray("contentAssociations");
            contentAssoc.removeAll();

            ObjectNode docAssoc = objectMapper.createObjectNode();
            docAssoc.put("parentContentReference", originalDocId);
            docAssoc.put("contentReference", printReadyDocId);
            docAssoc.put("contentType", "application/pdf");
            docAssoc.put("fileSizeBytes", 0);
            docAssoc.put("fileName", "SimpleText.pdf");
            docAssoc.put("printReady", true);
            docAssoc.put("contentReqId", "1483999952979");
            docAssoc.put("name", productNode.path("name").asText("Multi Sheet"));
            docAssoc.put("purpose", "MAIN_CONTENT");

            ArrayNode pageGroups = docAssoc.putArray("pageGroups");
            ObjectNode pg = objectMapper.createObjectNode();
            pg.put("start", 1).put("end", 1).put("width", 8.5).put("height", 11.0).put("orientation", "PORTRAIT");
            pageGroups.add(pg);

            docAssoc.put("physicalContent", false);
            contentAssoc.add(docAssoc);

            ObjectNode userWorkspace = configParams.with("userWorkspace");
            ArrayNode files = userWorkspace.withArray("files");
            files.removeAll();

            ObjectNode fileNode = objectMapper.createObjectNode();
            fileNode.put("name", "SimpleText.pdf").put("id", originalDocId).put("size", 27028).put("uploadDateTime", Instant.now().toString());
            files.add(fileNode);
        }
    }

    private void applyDynamicFeatures(ArrayNode featuresArray, Map<String, String> bddFeatures) {
        if (bddFeatures == null || bddFeatures.isEmpty() || featuresArray == null || featuresArray.isMissingNode()) return;

        for (JsonNode featureNode : featuresArray) {
            String featureName = featureNode.get("name").asText();
            if (bddFeatures.containsKey(featureName)) {
                ((ObjectNode) featureNode.get("choice")).put("name", bddFeatures.get(featureName));
            }
        }
    }
}