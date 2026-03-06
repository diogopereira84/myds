package com.fedex.automation.service.mirakl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.config.MiraklConfig;
import com.fedex.automation.model.mirakl.MiraklShopOffersResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferService {

    private final MiraklConfig miraklConfig;
    private final ObjectMapper objectMapper;

    @Autowired
    private RequestSpecification defaultRequestSpec; // Inject

    public String getOfferIdForProduct(String sku) {
        log.info("--- Fetching Offer ID from Mirakl for SKU: '{}' ---", sku);

        Response response = given()
                .spec(defaultRequestSpec) // <--- Applies cURL filter & Relaxed SSL
                .baseUri(miraklConfig.getBaseUrl())
                .header("Authorization", miraklConfig.getApiKey())
                .header("Accept", "application/json")
                .queryParam("product_ids", sku)
                .get(miraklConfig.getOffersEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();

        try {
            JsonNode root = objectMapper.readTree(response.asString());
            JsonNode products = root.path("products");

            if (products.isEmpty()) {
                throw new RuntimeException("No products found in Mirakl response for SKU: " + sku);
            }

            JsonNode offers = products.get(0).path("offers");
            if (offers.isEmpty()) {
                throw new RuntimeException("No offers found in Mirakl response for SKU: " + sku);
            }

            String offerId = offers.get(0).path("offer_id").asText();
            log.info("Found Offer ID: {}", offerId);
            return offerId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Mirakl API response", e);
        }
    }

    public MiraklShopOffersResponse.MiraklOffer getOfferFromShop(String shopId, String targetSku) {
        log.info("--- Fetching Mirakl Offers for Shop ID: {} to find SKU: '{}' ---", shopId, targetSku);

        String endpoint = miraklConfig.getShopOffersEndpoint().replace("{shopId}", shopId);

        var request = given()
                .spec(defaultRequestSpec)
                .baseUri(miraklConfig.getBaseUrl())
                .header("Authorization", miraklConfig.getApiKey())
                .header("Accept", "application/json");

        // Prefer server-side filtering when supported by Mirakl
        if (targetSku != null && !targetSku.isBlank()) {
            request.queryParam("product_sku", targetSku);
        }

        Response response = request
                .get(endpoint)
                .then()
                .statusCode(200)
                .extract()
                .response();

        MiraklShopOffersResponse offersResponse = response.as(MiraklShopOffersResponse.class);

        if (offersResponse.getOffers() == null || offersResponse.getOffers().isEmpty()) {
            throw new RuntimeException("No offers found for Shop ID: " + shopId);
        }

        // Return the full offer object so we can access offerId AND shopSku
        return offersResponse.getOffers().stream()
                .filter(offer -> Objects.equals(targetSku, offer.getProductSku()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Offer not found for SKU: " + targetSku + " in Shop: " + shopId));
    }
}