package com.fedex.automation.service.fedex;

import com.fedex.automation.model.fedex.ConfiguratorAddToCartPayload;
import com.fedex.automation.service.fedex.client.ConfiguratorApiClient;
import com.fedex.automation.service.fedex.exception.ConfiguratorErrorCode;
import com.fedex.automation.service.fedex.exception.ConfiguratorOperationException;
import com.fedex.automation.service.fedex.parser.ConfiguratorPayloadBuilder;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguratorService {

    private final ConfiguratorApiClient configuratorApiClient;
    private final ConfiguratorPayloadBuilder payloadBuilder;

    public Response add1PConfiguredItemToCart(String sku, String partnerProductId, int quantity) {
        validateRequired(sku, "SKU must be provided.");
        validateRequired(partnerProductId, "partnerProductId must be provided.");
        if (quantity <= 0) {
            throw new ConfiguratorOperationException(
                    ConfiguratorErrorCode.INVALID_REQUEST,
                    "quantity must be greater than zero."
            );
        }

        log.info("Adding 1P Item [SKU: {}, PartnerID: {}, Qty: {}]", sku, partnerProductId, quantity);

        ConfiguratorAddToCartPayload payload = payloadBuilder.buildAddToCartPayload(sku, partnerProductId, quantity);
        Response response = configuratorApiClient.requestAddConfiguredItemToCart(payload);

        log.info("Successfully added 1P item to cart.");
        return response;
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConfiguratorOperationException(ConfiguratorErrorCode.INVALID_REQUEST, message);
        }
    }
}