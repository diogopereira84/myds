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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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

            // Fetch the base structure (15 valid features) to prevent backend rule invalidation
            ObjectNode baseProductNode = (ObjectNode) payloadNode.at("/configuratorSessionParameters/configuratorOptions/product");

            // Build strictly typed overrides from API Source of Truth
            ObjectNode dynamicProductNode = buildDynamicProductNode(baseProductNode, testContext.getStaticProductDetails(), Collections.emptyMap());

            ObjectNode configOptions = (ObjectNode) payloadNode.at("/configuratorSessionParameters/configuratorOptions");
            configOptions.set("product", dynamicProductNode);

            testContext.setCurrentConfiguredProductNode(dynamicProductNode);

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
            payloadNode.putArray("errors");

            JsonNode productNode = payloadNode.path("product");
            if (productNode.isObject()) {
                ObjectNode pNode = (ObjectNode) productNode;
                pNode.put("qty", quantity);
                pNode.put("userProductName", "SimpleText");

                // --- CRITICAL FIX: Magento Schema Normalization ---
                // The FedEx API returns dimensions as floats (11.0), but Magento strictly requires integers (11) where applicable.
                JsonNode contentAssoc = pNode.path("contentAssociations");
                if (contentAssoc.isArray()) {
                    for (JsonNode ca : contentAssoc) {
                        JsonNode pg = ca.path("pageGroups");
                        if (pg.isArray()) {
                            for (JsonNode group : pg) {
                                ObjectNode groupNode = (ObjectNode) group;
                                if (groupNode.has("width")) {
                                    double w = groupNode.get("width").asDouble();
                                    if (w == (long) w) groupNode.put("width", (long) w);
                                }
                                if (groupNode.has("height")) {
                                    double h = groupNode.get("height").asDouble();
                                    if (h == (long) h) groupNode.put("height", (long) h);
                                }
                            }
                        }
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

        // Apply dynamic BDD overrides to the clean base template
        ObjectNode baseProductNode = testContext.getCurrentConfiguredProductNode();
        ObjectNode productNode = buildDynamicProductNode(baseProductNode, testContext.getStaticProductDetails(), bddFeatures);

        productNode.put("userProductName", "SimpleText");
        productNode.put("minDPI", "150.0");
        productNode.put("proofRequired", false);

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

    /**
     * Engine that builds the Product Node mimicking the FedEx UI exactly by parsing the Domain Source of Truth
     * mapping between visual feature names and their strict internal properties/Long IDs.
     */
    private ObjectNode buildDynamicProductNode(ObjectNode baseProductNode, StaticProduct staticProduct, Map<String, String> bddFeatures) {
        ObjectNode productNode = baseProductNode.deepCopy();

        // Ensure IDs remain numerical to appease Magento validation
        productNode.put("id", Long.parseLong(staticProduct.getId()));
        productNode.put("version", staticProduct.getVersion());
        productNode.put("name", staticProduct.getName());
        productNode.put("instanceId", System.currentTimeMillis());

        ArrayNode featuresArray = (ArrayNode) productNode.get("features");
        double mediaWidth = 8.5;
        double mediaHeight = 11.0;

        for (JsonNode baseFeatureNode : featuresArray) {
            ObjectNode featureNode = (ObjectNode) baseFeatureNode;
            String featureName = featureNode.path("name").asText();

            ProductFeature staticFeature = staticProduct.getFeatures().stream()
                    .filter(f -> f.getName().equalsIgnoreCase(featureName))
                    .findFirst()
                    .orElse(null);

            if (staticFeature != null) {
                // Strict Cast
                featureNode.put("id", Long.parseLong(staticFeature.getId()));

                String desiredChoiceName = (bddFeatures != null) ? bddFeatures.get(featureName) : null;
                ProductChoice finalChoice = null;

                // 1. Check for requested BDD override
                if (desiredChoiceName != null) {
                    finalChoice = staticFeature.getChoices().stream()
                            .filter(c -> c.getName().equalsIgnoreCase(desiredChoiceName))
                            .findFirst()
                            .orElse(null);
                }

                // 2. Fallback to existing base choice
                if (finalChoice == null) {
                    String baseChoiceId = featureNode.path("choice").path("id").asText();
                    finalChoice = staticFeature.getChoices().stream()
                            .filter(c -> c.getId().equals(baseChoiceId))
                            .findFirst()
                            .orElse(staticFeature.getChoices().get(0));
                }

                ObjectNode choiceNode = (ObjectNode) featureNode.get("choice");
                choiceNode.put("id", Long.parseLong(finalChoice.getId()));
                choiceNode.put("name", finalChoice.getName());

                ArrayNode propertiesArray = choiceNode.putArray("properties");
                if (finalChoice.getProperties() != null) {
                    for (ProductProperty prop : finalChoice.getProperties()) {
                        ObjectNode propNode = objectMapper.createObjectNode();
                        propNode.put("id", Long.parseLong(prop.getId()));
                        propNode.put("name", prop.getName());
                        if (prop.getValue() != null) {
                            propNode.put("value", formatDecimalString(prop.getValue()));
                        }
                        propertiesArray.add(propNode);

                        // Capture constraints dynamically to inject globally
                        if ("Paper Size".equalsIgnoreCase(featureName)) {
                            if ("MEDIA_WIDTH".equals(prop.getName()) || "DISPLAY_WIDTH".equals(prop.getName())) {
                                try { mediaWidth = Double.parseDouble(prop.getValue()); } catch(Exception ignored) {}
                            }
                            if ("MEDIA_HEIGHT".equals(prop.getName()) || "DISPLAY_HEIGHT".equals(prop.getName())) {
                                try { mediaHeight = Double.parseDouble(prop.getValue()); } catch(Exception ignored) {}
                            }
                        }
                    }
                }
            }
        }

        ArrayNode propertiesArray = (ArrayNode) productNode.get("properties");
        if (propertiesArray != null) {
            for (JsonNode prop : propertiesArray) {
                ObjectNode propNode = (ObjectNode) prop;
                String propName = propNode.path("name").asText();

                if (propNode.has("id")) {
                    propNode.put("id", Long.parseLong(propNode.path("id").asText()));
                }

                // Overlay the dimensions chosen via feature selection (E.g. 11x17)
                if ("DEFAULT_IMAGE_WIDTH".equals(propName)) {
                    propNode.put("value", formatDecimalString(String.valueOf(mediaWidth)));
                } else if ("DEFAULT_IMAGE_HEIGHT".equals(propName)) {
                    propNode.put("value", formatDecimalString(String.valueOf(mediaHeight)));
                }
            }
        }

        return productNode;
    }

    private void linkDocumentsToStateProductNode(ObjectNode productNode, ObjectNode configParams) {
        String originalDocId = testContext.getOriginalDocId();
        String printReadyDocId = testContext.getPrintReadyDocId();

        if (originalDocId != null && printReadyDocId != null) {
            ArrayNode contentAssoc = productNode.putArray("contentAssociations");

            String contentReqId = "1483999952979"; // UI standard fallback
            StaticProduct staticProduct = testContext.getStaticProductDetails();
            if (staticProduct != null && staticProduct.getContentRequirements() != null && !staticProduct.getContentRequirements().isEmpty()) {
                contentReqId = staticProduct.getContentRequirements().get(0).getId();
            }

            ObjectNode docAssoc = objectMapper.createObjectNode();
            docAssoc.put("parentContentReference", originalDocId);
            docAssoc.put("contentReference", printReadyDocId);
            docAssoc.put("contentType", "application/pdf");
            docAssoc.put("fileSizeBytes", 0);
            docAssoc.put("fileName", "SimpleText.pdf");
            docAssoc.put("printReady", true);
            docAssoc.put("contentReqId", Long.parseLong(contentReqId));
            docAssoc.put("name", productNode.path("name").asText("Multi Sheet"));
            docAssoc.put("purpose", "MAIN_CONTENT");

            // Pull sizing dynamically based on mapped attributes
            double width = 8.5;
            double height = 11.0;
            for (JsonNode prop : productNode.path("properties")) {
                if ("DEFAULT_IMAGE_WIDTH".equals(prop.path("name").asText())) width = prop.path("value").asDouble(8.5);
                if ("DEFAULT_IMAGE_HEIGHT".equals(prop.path("name").asText())) height = prop.path("value").asDouble(11.0);
            }

            ArrayNode pageGroups = docAssoc.putArray("pageGroups");
            ObjectNode pg = objectMapper.createObjectNode();
            pg.put("start", 1).put("end", 1);

            // Format cleanly avoiding strictly typed mismatch downstream (e.g. 11 vs 11.0)
            if (width == (long) width) pg.put("width", (long) width);
            else pg.put("width", width);

            if (height == (long) height) pg.put("height", (long) height);
            else pg.put("height", height);

            pg.put("orientation", "PORTRAIT");
            pageGroups.add(pg);
            docAssoc.put("physicalContent", false);

            contentAssoc.add(docAssoc);

            ObjectNode userWorkspace = configParams.putObject("userWorkspace");
            ArrayNode files = userWorkspace.putArray("files");

            // Fix the precision mismatch that crashes parser schemas
            String formattedTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));

            ObjectNode fileNode = objectMapper.createObjectNode();
            fileNode.put("name", "SimpleText.pdf");
            fileNode.put("id", originalDocId);
            fileNode.put("size", 27028);
            fileNode.put("uploadDateTime", formattedTime);

            files.add(fileNode);
            userWorkspace.putArray("projects");
        }
    }

    /**
     * Forces standard whole numbers into integer format strings to match FedEx UI
     * e.g., "11.0" -> "11", "8.5" -> "8.5"
     */
    private String formatDecimalString(String val) {
        try {
            double dVal = Double.parseDouble(val);
            if (dVal == (long) dVal) return String.valueOf((long) dVal);
            return String.valueOf(dVal);
        } catch (Exception e) {
            return val;
        }
    }
}