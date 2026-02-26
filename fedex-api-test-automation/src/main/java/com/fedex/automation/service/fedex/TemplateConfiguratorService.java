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

            // 1. Use the new Cross-Domain Builder (staging2 origin, same-site)
            Response response = sessionService.configuratorRequest(sessionService.getBaseUrl(), sessionService.getBaseUrl() + "/")
                    .baseUri(apiGatewayUri)
                    .header(FedExConstants.HEADER_CLIENT_ID, magentoUiClientId)
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
                .header(FedExConstants.HEADER_CLIENT_ID, apiGatewayClientId)
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

            payloadNode.remove("configuratorStateId");
            payloadNode.remove("expirationDateTime");
            payloadNode.remove("isEditable");
            payloadNode.putArray("errors");

            JsonNode productNode = payloadNode.path("product");
            if (productNode.isObject()) {
                ObjectNode pNode = (ObjectNode) productNode;
                pNode.put("qty", String.valueOf(quantity));
                if (pNode.has("id")) pNode.put("id", pNode.get("id").asText());
                pNode.put("userProductName", "SimpleText");

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
                    .baseUri(sessionService.getBaseUrl())
                    .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Adrum", "isAjax:true")
                    .header("Referer", refererUrl)
                    .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
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

        applyDynamicFeatures((ArrayNode) productNode.get("features"), bddFeatures);
        linkDocumentsToStateProductNode(productNode, configParams);

        configParams.set("product", productNode);

        // 2. Use the new Cross-Domain Builder (wwwtest origin, same-site)
        Response response = sessionService.configuratorRequest(baseUrlWww, baseUrlWww + "/fxo-client-modules/redirect/print-products/configure")
                .baseUri(apiGatewayUri)
                .header(FedExConstants.HEADER_CLIENT_ID, apiGatewayClientId)
                .queryParam(FedExConstants.PARAM_CLIENT_NAME, FedExConstants.INTEGRATOR_ID_POD2)
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
            // Document Associations
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
            pg.put("start", 1).put("end", 1).put("width", 8.5).put("height", 11.0).put("orientation", "PORTRAIT");
            pageGroups.add(pg);

            contentAssoc.add(docAssoc);

            // User Workspace Files
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