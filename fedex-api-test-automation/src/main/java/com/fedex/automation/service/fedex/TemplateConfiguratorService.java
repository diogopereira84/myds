package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.context.TestContext;
import com.fedex.automation.model.fedex.product.StaticProductResponse.*;
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

    @Value("${endpoint.configurator.sessions}")
    private String configSessionsEndpoint;

    @Value("${endpoint.configurator.search}")
    private String configSearchEndpoint;

    @Value("${endpoint.configurator.states}")
    private String configStatesEndpoint;

    @Value("${endpoint.cart.product.add}")
    private String cartProductAddEndpoint;

    @Value("${fedex.api.gateway.client-id}")
    private String apiGatewayClientId;

    @Value("${fedex.magento.ui.client-id}")
    private String magentoUiClientId;

    @Value("${base.url.www}")
    private String baseUrlWww;

    // --- Injected Constants ---
    @Value("${fedex.constants.integrator-id}")
    private String integratorIdPod2;

    @Value("${fedex.constants.header.client-id}")
    private String headerClientId;

    @Value("${fedex.constants.param.client-name}")
    private String paramClientName;

    @Value("${fedex.constants.header.x-requested-with}")
    private String headerXRequestedWith;

    @Value("${fedex.constants.value.xmlhttprequest}")
    private String valueXmlHttpRequest;

    public void createConfiguratorSession() {
        String sku = testContext.getCurrentSku();
        String productId = testContext.getCurrentProductId();
        String transactionId = UUID.randomUUID().toString();

        log.info("--- [Action] Creating Configurator Session [SKU: {}, ProductID: {}] ---", sku, productId);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("templates/1P_ConfiguratorSessionsTemplate.json");
            if (is == null) throw new IllegalArgumentException("Template file not found.");

            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(is);

            ObjectNode productSelector = (ObjectNode) payloadNode.at("/configuratorSessionParameters/configuratorOptions/productSelector");
            productSelector.put("productId", sku);

            ObjectNode productNode = (ObjectNode) payloadNode.at("/configuratorSessionParameters/configuratorOptions/product");
            productNode.put("id", productId);

            testContext.setCurrentConfiguredProductNode((ObjectNode) productNode.deepCopy());

            Response response = sessionService.configuratorRequest(sessionService.getBaseUrl(), sessionService.getBaseUrl() + "/")
                    .baseUri(apiGatewayUri)
                    .header(headerClientId, magentoUiClientId)
                    .header("x-transaction-id", transactionId)
                    .header("Accept", "*/*")
                    .contentType(ContentType.JSON)
                    .body(payloadNode.toString())
                    .post(configSessionsEndpoint);

            response.then().statusCode(200);
            testContext.setLastResponse(response);

            String configuratorUrl = response.jsonPath().getString("output.configuratorSession.configuratorURL");
            if (configuratorUrl != null && configuratorUrl.contains("/")) {
                String sessionId = configuratorUrl.substring(configuratorUrl.lastIndexOf("/") + 1);
                testContext.setSessionId(sessionId);
                log.info("Successfully created Configurator Session. Session ID: {}", sessionId);
            } else {
                fail("Failed to extract Session ID from configurator URL: " + configuratorUrl);
            }

        } catch (Exception e) {
            fail("Error executing Configurator Session creation: " + e.getMessage());
        }
    }

    public void searchConfiguratorSession() {
        String sessionId = testContext.getSessionId();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putObject("configuratorSessionSearchCriteria").put("configuratorSessionId", sessionId);

        Response response = sessionService.authenticatedRequest()
                .baseUri(apiGatewayUri)
                .header(headerClientId, apiGatewayClientId)
                .contentType(ContentType.JSON)
                .body(payload.toString())
                .post(configSearchEndpoint);

        testContext.setLastResponse(response);
    }

    public void addConfiguredItemToCart(int quantity) {
        String stateId = testContext.getConfiguratorStateId();
        String fullPayload = testContext.getConfiguratorPayload();

        try {
            ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(fullPayload);

            // FIX: Removed the .remove() calls so state data persists to cart like the UI
            payloadNode.putArray("errors");

            JsonNode productNode = payloadNode.path("product");
            if (productNode.isObject()) {
                ObjectNode pNode = (ObjectNode) productNode;
                // FIX: Pass qty as int, not String
                pNode.put("qty", quantity);
                pNode.put("userProductName", "SimpleText");
            }

            String urlEncodedData = URLEncoder.encode(payloadNode.toString(), StandardCharsets.UTF_8.toString());
            String rawBody = "data=" + urlEncodedData + "&itemId=";
            String refererUrl = sessionService.getBaseUrl() + "/default/configurator/index/index/responseid/" + stateId;

            Response response = sessionService.authenticatedRequest()
                    .baseUri(sessionService.getBaseUrl())
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Adrum", "isAjax:true")
                    .header("Referer", refererUrl)
                    .header(headerXRequestedWith, valueXmlHttpRequest)
                    .body(rawBody)
                    .post(cartProductAddEndpoint);

            testContext.setLastResponse(response);

        } catch (Exception e) {
            fail("Failed to push item to cart: " + e.getMessage());
        }
    }

    public void createConfiguratorState(String templatePrefix, Map<String, String> bddFeatures) throws Exception {
        log.info("--- [Action] Creating Configurator State ---");

        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode configParams = payload.putObject("configuratorStateParameters");

        configParams.put("configuratorSessionId", testContext.getSessionId());
        if (testContext.getCurrentSku() != null) {
            configParams.put("integratorProductReference", testContext.getCurrentSku());
        }
        configParams.put("expressCheckoutButtonSelected", false);
        configParams.put("isEditable", true);
        configParams.putArray("errors");
        configParams.putArray("customDocumentDetails");
        configParams.put("changeProduct", false);

        ObjectNode productNode = testContext.getCurrentConfiguredProductNode().deepCopy();
        productNode.put("userProductName", "SimpleText");
        productNode.put("minDPI", "150.0");
        productNode.put("proofRequired", false);

        // Apply decoupled dynamic BDD features
        applyDynamicFeatures((ArrayNode) productNode.get("features"), bddFeatures, testContext.getStaticProductDetails());
        linkDocumentsToStateProductNode(productNode, configParams);

        configParams.set("product", productNode);

        Response response = sessionService.configuratorRequest(baseUrlWww, baseUrlWww + "/fxo-client-modules/redirect/print-products/configure")
                .baseUri(apiGatewayUri)
                .header(headerClientId, apiGatewayClientId)
                .queryParam(paramClientName, integratorIdPod2)
                .header("Accept", "application/json, text/plain, */*")
                .contentType(ContentType.JSON)
                .body(payload.toString())
                .post(configStatesEndpoint);

        response.then().statusCode(200);
        testContext.setLastResponse(response);

        String stateId = response.jsonPath().getString("output.configuratorState.configuratorStateId");
        if (stateId == null) {
            fail("Failed to retrieve configuratorStateId from response.");
        }
        testContext.setConfiguratorStateId(stateId);
        log.info("Successfully created Configurator State. State ID: {}", stateId);

        ObjectNode returnedState = objectMapper.valueToTree(response.jsonPath().getMap("output.configuratorState"));
        testContext.setConfiguratorPayload(returnedState.toString());
    }

    private void linkDocumentsToStateProductNode(ObjectNode productNode, ObjectNode configParams) {
        String originalDocId = testContext.getOriginalDocId();
        String printReadyDocId = testContext.getPrintReadyDocId();

        if (originalDocId != null && printReadyDocId != null) {
            ArrayNode contentAssoc = productNode.putArray("contentAssociations");

            ObjectNode docAssoc = objectMapper.createObjectNode();
            docAssoc.put("parentContentReference", originalDocId);
            docAssoc.put("contentReference", printReadyDocId);
            docAssoc.put("contentType", "application/pdf");
            docAssoc.put("fileName", "SimpleText.pdf");
            docAssoc.put("contentReqId", "1483999952979");
            docAssoc.put("name", productNode.path("name").asText("Multi Sheet"));
            docAssoc.put("purpose", "MAIN_CONTENT");
            docAssoc.put("printReady", true);

            ArrayNode pageGroups = docAssoc.putArray("pageGroups");
            ObjectNode pg = objectMapper.createObjectNode();
            // FIX: Height changed to integer 11 to match UI parsing
            pg.put("start", 1).put("end", 1).put("width", 8.5).put("height", 11).put("orientation", "PORTRAIT");
            pageGroups.add(pg);

            contentAssoc.add(docAssoc);

            ObjectNode userWorkspace = configParams.putObject("userWorkspace");
            ArrayNode files = userWorkspace.putArray("files");

            ObjectNode fileNode = objectMapper.createObjectNode();
            fileNode.put("name", "SimpleText.pdf");
            fileNode.put("size", 27028);
            fileNode.put("uploadDateTime", Instant.now().toString());
            fileNode.put("id", originalDocId);

            files.add(fileNode);
            userWorkspace.putArray("projects");
        }
    }

    private void applyDynamicFeatures(ArrayNode payloadFeaturesArray, Map<String, String> bddFeatures, StaticProduct staticProductDetails) {
        if (bddFeatures == null || bddFeatures.isEmpty() || payloadFeaturesArray == null || staticProductDetails == null) return;

        for (JsonNode payloadFeatureNode : payloadFeaturesArray) {
            String featureName = payloadFeatureNode.path("name").asText();

            if (bddFeatures.containsKey(featureName)) {
                String desiredChoiceName = bddFeatures.get(featureName);

                ProductFeature staticFeature = staticProductDetails.getFeatures().stream()
                        .filter(f -> f.getName().equalsIgnoreCase(featureName))
                        .findFirst()
                        .orElse(null);

                if (staticFeature != null) {
                    ProductChoice staticChoice = staticFeature.getChoices().stream()
                            .filter(c -> c.getName().equalsIgnoreCase(desiredChoiceName))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    String.format("Invalid BDD Choice: '%s' is not a valid option for feature '%s'", desiredChoiceName, featureName)
                            ));

                    ObjectNode choiceNode = (ObjectNode) payloadFeatureNode.get("choice");
                    choiceNode.put("id", staticChoice.getId());
                    choiceNode.put("name", staticChoice.getName());

                    ArrayNode propertiesArray = choiceNode.putArray("properties");
                    if (staticChoice.getProperties() != null) {
                        for (ProductProperty prop : staticChoice.getProperties()) {
                            ObjectNode propNode = objectMapper.createObjectNode();
                            propNode.put("id", prop.getId());
                            propNode.put("name", prop.getName());
                            if (prop.getValue() != null) {
                                propNode.put("value", prop.getValue());
                            }
                            propertiesArray.add(propNode);
                        }
                    }
                    log.info("Successfully applied dynamic feature: {} -> {} (ID: {})", featureName, desiredChoiceName, staticChoice.getId());
                }
            }
        }
    }
}