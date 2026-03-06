package com.fedex.automation.service.fedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.constants.FedExConstants;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguratorService {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Value("${endpoint.cart.product.add}")
    private String cartProductAddEndpoint;

    @Value("${fedex.constants.header.x-requested-with}")
    private String headerXRequestedWith;

    @Value("${fedex.constants.value.xmlhttprequest}")
    private String valueXmlHttpRequest;

    public void add1PConfiguredItemToCart(String sku, String partnerProductId, int quantity) {
        String configuratorStateId = UUID.randomUUID().toString();
        String configuratorSessionId = UUID.randomUUID().toString();
        String expirationTime = Instant.now().plusSeconds(86400).toString();

        log.info("Adding 1P Item [SKU: {}, PartnerID: {}, Qty: {}]", sku, partnerProductId, quantity);

        ObjectNode payloadNode = loadConfiguratorTemplate();
        payloadNode.put("configuratorStateId", configuratorStateId);
        payloadNode.put("expirationDateTime", expirationTime);
        payloadNode.put("configuratorSessionId", configuratorSessionId);
        payloadNode.put("integratorProductReference", sku);

        ObjectNode productNode = (ObjectNode) payloadNode.path("product");
        productNode.put("qty", quantity);
        productNode.put("partnerProductId", partnerProductId);
        productNode.put("instanceId", System.currentTimeMillis());
        updateProductProperty(productNode, "PRODUCT_QTY_SET", String.valueOf(quantity));

        String jsonPayload = payloadNode.toString();

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Adrum", "isAjax:true")
                .header("Origin", "https://staging2.office.fedex.com")
                .header("Referer", "https://staging2.office.fedex.com/default/configurator/index/index/responseid/" + configuratorStateId)
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .formParam("data", jsonPayload)
                .formParam("itemId", "")
                .post(cartProductAddEndpoint);

        if (response.statusCode() != 200) {
            log.error("Failed to add 1P item. Status: {}, Body: {}", response.statusCode(), response.body().asString());
        }
        assertEquals(200, response.statusCode(), "Expected 200 OK from Cart Product Add");
        log.info("Successfully added 1P item to cart.");
    }

    private ObjectNode loadConfiguratorTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("templates/1P_ConfiguratorAddToCartTemplate.json")) {
            if (is == null) {
                throw new IllegalStateException("Configurator add-to-cart template not found.");
            }
            JsonNode node = objectMapper.readTree(is);
            if (!node.isObject()) {
                throw new IllegalStateException("Configurator template root must be a JSON object.");
            }
            return (ObjectNode) node;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load configurator add-to-cart template.", e);
        }
    }

    private void updateProductProperty(ObjectNode productNode, String propertyName, String value) {
        JsonNode properties = productNode.path("properties");
        if (!properties.isArray()) {
            return;
        }
        for (JsonNode prop : properties) {
            if (propertyName.equals(prop.path("name").asText())) {
                ((ObjectNode) prop).put("value", value);
                return;
            }
        }
    }
}