package com.fedex.automation.service.fedex.client;

import com.fedex.automation.constants.FedExConstants;
import com.fedex.automation.model.fedex.ConfiguratorAddToCartPayload;
import com.fedex.automation.service.fedex.SessionService;
import com.fedex.automation.service.fedex.exception.ConfiguratorErrorCode;
import com.fedex.automation.service.fedex.exception.ConfiguratorOperationException;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfiguratorApiClient {

    private static final int HTTP_OK = 200;
    private static final String HEADER_ACCEPT = "application/json, text/javascript, */*; q=0.01";
    private static final String HEADER_ADRUM = "isAjax:true";
    private static final String HEADER_SEC_FETCH_DEST = "empty";
    private static final String HEADER_SEC_FETCH_MODE = "cors";
    private static final String HEADER_SEC_FETCH_SITE = "same-origin";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_ITEM_ID = "itemId";
    private static final String VALUE_EMPTY = "";

    private final SessionService sessionService;

    @Value("${endpoint.cart.product.add}")
    private String cartProductAddEndpoint;

    public Response requestAddConfiguredItemToCart(ConfiguratorAddToCartPayload payload) {
        if (payload == null) {
            throw new ConfiguratorOperationException(ConfiguratorErrorCode.INVALID_REQUEST, "Configurator payload cannot be null.");
        }
        if (payload.configuratorStateId() == null || payload.configuratorStateId().isBlank()) {
            throw new ConfiguratorOperationException(ConfiguratorErrorCode.INVALID_REQUEST, "configuratorStateId must be provided.");
        }
        if (payload.payloadJson() == null || payload.payloadJson().isBlank()) {
            throw new ConfiguratorOperationException(ConfiguratorErrorCode.INVALID_REQUEST, "payloadJson must be provided.");
        }

        String baseUrl = sessionService.getBaseUrl();

        Response response = sessionService.authenticatedRequest()
                .contentType(ContentType.URLENC)
                .header("Accept", HEADER_ACCEPT)
                .header("Adrum", HEADER_ADRUM)
                .header("Origin", baseUrl)
                .header("Referer", baseUrl + "/default/configurator/index/index/responseid/" + payload.configuratorStateId())
                .header(FedExConstants.HEADER_X_REQUESTED_WITH, FedExConstants.VALUE_XMLHTTPREQUEST)
                .header("Sec-Fetch-Dest", HEADER_SEC_FETCH_DEST)
                .header("Sec-Fetch-Mode", HEADER_SEC_FETCH_MODE)
                .header("Sec-Fetch-Site", HEADER_SEC_FETCH_SITE)
                .formParam(FIELD_DATA, payload.payloadJson())
                .formParam(FIELD_ITEM_ID, VALUE_EMPTY)
                .post(cartProductAddEndpoint);

        if (response == null) {
            throw new ConfiguratorOperationException(ConfiguratorErrorCode.NULL_RESPONSE, "Add configured item response was null.");
        }

        if (response.statusCode() != HTTP_OK) {
            throw new ConfiguratorOperationException(
                    ConfiguratorErrorCode.UPSTREAM_STATUS_ERROR,
                    "Failed to add 1P item. Status: " + response.statusCode() + ". Body: " + safeBody(response)
            );
        }

        return response;
    }

    private String safeBody(Response response) {
        try {
            return response.asString();
        } catch (Exception ex) {
            return "<unavailable>";
        }
    }
}

