package com.fedex.automation.service.fedex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fedex.automation.model.fedex.ConfiguratorAddToCartPayload;
import com.fedex.automation.service.fedex.exception.ConfiguratorErrorCode;
import com.fedex.automation.service.fedex.exception.ConfiguratorOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConfiguratorPayloadBuilder {

    private static final String TEMPLATE_PATH = "templates/1P_ConfiguratorAddToCartTemplate.json";
    private static final String PRODUCT_PROPERTY_QTY_SET = "PRODUCT_QTY_SET";
    private static final long DEFAULT_EXPIRATION_SECONDS = 86400L;

    private static final String KEY_CONFIGURATOR_STATE_ID = "configuratorStateId";
    private static final String KEY_EXPIRATION_DATE_TIME = "expirationDateTime";
    private static final String KEY_CONFIGURATOR_SESSION_ID = "configuratorSessionId";
    private static final String KEY_INTEGRATOR_PRODUCT_REFERENCE = "integratorProductReference";
    private static final String KEY_PRODUCT = "product";
    private static final String KEY_QTY = "qty";
    private static final String KEY_PARTNER_PRODUCT_ID = "partnerProductId";
    private static final String KEY_INSTANCE_ID = "instanceId";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_NAME = "name";
    private static final String KEY_VALUE = "value";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ConfiguratorAddToCartPayload buildAddToCartPayload(String sku, String partnerProductId, int quantity) {
        String configuratorStateId = UUID.randomUUID().toString();
        String configuratorSessionId = UUID.randomUUID().toString();
        String expirationTime = Instant.now(clock).plusSeconds(DEFAULT_EXPIRATION_SECONDS).toString();

        ObjectNode payloadNode = loadConfiguratorTemplate();
        payloadNode.put(KEY_CONFIGURATOR_STATE_ID, configuratorStateId);
        payloadNode.put(KEY_EXPIRATION_DATE_TIME, expirationTime);
        payloadNode.put(KEY_CONFIGURATOR_SESSION_ID, configuratorSessionId);
        payloadNode.put(KEY_INTEGRATOR_PRODUCT_REFERENCE, sku);

        JsonNode productNodeValue = payloadNode.get(KEY_PRODUCT);
        if (!(productNodeValue instanceof ObjectNode productNode)) {
            throw new ConfiguratorOperationException(
                    ConfiguratorErrorCode.TEMPLATE_SCHEMA_ERROR,
                    "Configurator template missing product node."
            );
        }

        productNode.put(KEY_QTY, quantity);
        productNode.put(KEY_PARTNER_PRODUCT_ID, partnerProductId);
        productNode.put(KEY_INSTANCE_ID, clock.millis());
        updateQuantityProperty(productNode, String.valueOf(quantity));

        return new ConfiguratorAddToCartPayload(configuratorStateId, payloadNode.toString());
    }

    private ObjectNode loadConfiguratorTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                throw new ConfiguratorOperationException(
                        ConfiguratorErrorCode.TEMPLATE_NOT_FOUND,
                        "Configurator add-to-cart template not found: " + TEMPLATE_PATH
                );
            }
            JsonNode node = objectMapper.readTree(is);
            if (!node.isObject()) {
                throw new ConfiguratorOperationException(
                        ConfiguratorErrorCode.TEMPLATE_SCHEMA_ERROR,
                        "Configurator template root must be a JSON object."
                );
            }
            return (ObjectNode) node;
        } catch (ConfiguratorOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConfiguratorOperationException(
                    ConfiguratorErrorCode.TEMPLATE_PARSE_ERROR,
                    "Failed to load configurator add-to-cart template.",
                    ex
            );
        }
    }

    private void updateQuantityProperty(ObjectNode productNode, String value) {
        JsonNode properties = productNode.path(KEY_PROPERTIES);
        if (!properties.isArray()) {
            throw new ConfiguratorOperationException(
                    ConfiguratorErrorCode.TEMPLATE_SCHEMA_ERROR,
                    "Configurator template missing product properties array."
            );
        }
        for (JsonNode prop : properties) {
            if (PRODUCT_PROPERTY_QTY_SET.equals(prop.path(KEY_NAME).asText()) && prop instanceof ObjectNode objectNode) {
                objectNode.put(KEY_VALUE, value);
                return;
            }
        }
        throw new ConfiguratorOperationException(
                ConfiguratorErrorCode.PROPERTY_NOT_FOUND,
                "Configurator template missing property: " + PRODUCT_PROPERTY_QTY_SET
        );
    }
}
