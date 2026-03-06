package com.fedex.automation.service.mirakl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.config.MiraklConfig;
import com.fedex.automation.model.mirakl.MiraklShopOffersResponse;
import com.fedex.automation.service.mirakl.client.MiraklRequestFactory;
import com.fedex.automation.service.mirakl.exception.MiraklErrorCode;
import com.fedex.automation.service.mirakl.exception.MiraklOperationException;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferService {

    private static final int HTTP_OK = 200;
    private static final String QUERY_PRODUCT_IDS = "product_ids";
    private static final String QUERY_PRODUCT_SKU = "product_sku";
    private static final String JSON_PRODUCTS = "products";
    private static final String JSON_OFFERS = "offers";
    private static final String JSON_OFFER_ID = "offer_id";
    private static final String MSG_PARSE_PRODUCTS_RESPONSE = "Failed to parse Mirakl products response.";

    private final MiraklConfig miraklConfig;
    private final ObjectMapper objectMapper;
    private final MiraklRequestFactory requestFactory;

    public String getOfferIdForProduct(String sku) {
        requireNonBlank(sku, "sku cannot be null/blank.");
        log.info("--- Fetching Offer ID from Mirakl for SKU: '{}' ---", sku);

        Response response = requestFactory.baseRequest()
                .queryParam(QUERY_PRODUCT_IDS, sku)
                .get(miraklConfig.getOffersEndpoint());

        ensureHttpOk(response, "Fetch offer by SKU failed");

        JsonNode root = parseJson(response);
        JsonNode firstProduct = firstArrayNode(root.path(JSON_PRODUCTS), MiraklErrorCode.NO_PRODUCTS,
                "No products found in Mirakl response for SKU: " + sku);
        JsonNode firstOffer = firstArrayNode(firstProduct.path(JSON_OFFERS), MiraklErrorCode.NO_OFFERS,
                "No offers found in Mirakl response for SKU: " + sku);

        String offerId = firstOffer.path(JSON_OFFER_ID).asText("").trim();
        if (offerId.isEmpty()) {
            throw new MiraklOperationException(MiraklErrorCode.PARSE_ERROR,
                    "offer_id is missing/blank in Mirakl response for SKU: " + sku);
        }

        log.info("Found Offer ID: {}", offerId);
        return offerId;
    }

    public MiraklShopOffersResponse.MiraklOffer getOfferFromShop(String shopId, String targetSku) {
        requireNonBlank(shopId, "shopId cannot be null/blank.");
        requireNonBlank(targetSku, "targetSku cannot be null/blank.");

        log.info("--- Fetching Mirakl Offers for Shop ID: {} to find SKU: '{}' ---", shopId, targetSku);

        String endpoint = miraklConfig.getShopOffersEndpoint().replace("{shopId}", shopId);

        Response response = requestFactory.baseRequest()
                .queryParam(QUERY_PRODUCT_SKU, targetSku)
                .get(endpoint);

        ensureHttpOk(response, "Fetch offers by shop failed");

        MiraklShopOffersResponse offersResponse;
        try {
            offersResponse = response.as(MiraklShopOffersResponse.class);
        } catch (Exception ex) {
            throw new MiraklOperationException(
                    MiraklErrorCode.PARSE_ERROR,
                    "Failed to parse Mirakl shop offers response for shopId=" + shopId,
                    ex
            );
        }

        if (offersResponse == null || offersResponse.getOffers() == null || offersResponse.getOffers().isEmpty()) {
            throw new MiraklOperationException(MiraklErrorCode.NO_OFFERS,
                    "No offers found for Shop ID: " + shopId);
        }

        return offersResponse.getOffers().stream()
                .filter(offer -> targetSku.equals(offer.getProductSku()))
                .findFirst()
                .orElseThrow(() -> new MiraklOperationException(
                        MiraklErrorCode.OFFER_NOT_FOUND,
                        "Offer not found for SKU: " + targetSku + " in Shop: " + shopId
                ));
    }

    private void ensureHttpOk(Response response, String operation) {
        if (response == null) {
            throw new MiraklOperationException(MiraklErrorCode.NULL_RESPONSE, operation + ": response was null.");
        }

        int status = response.statusCode();
        if (status != HTTP_OK) {
            throw new MiraklOperationException(
                    MiraklErrorCode.UPSTREAM_STATUS_ERROR,
                    operation + ". Status: " + status + ". Body: " + safeBody(response)
            );
        }
    }

    private JsonNode parseJson(Response response) {
        try {
            String body = response.asString();
            if (body == null || body.isBlank()) {
                throw new MiraklOperationException(MiraklErrorCode.PARSE_ERROR, MSG_PARSE_PRODUCTS_RESPONSE + " Empty response body.");
            }
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            if (ex instanceof MiraklOperationException miraklOperationException) {
                throw miraklOperationException;
            }
            throw new MiraklOperationException(MiraklErrorCode.PARSE_ERROR, MSG_PARSE_PRODUCTS_RESPONSE, ex);
        }
    }

    private JsonNode firstArrayNode(JsonNode node, MiraklErrorCode errorCode, String message) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new MiraklOperationException(errorCode, message);
        }
        return node.get(0);
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MiraklOperationException(MiraklErrorCode.INVALID_INPUT, message);
        }
    }

    private String safeBody(Response response) {
        return Optional.ofNullable(response)
                .map(r -> {
                    try {
                        return r.asString();
                    } catch (Exception ex) {
                        return "<unavailable>";
                    }
                })
                .orElse("<unavailable>");
    }
}
