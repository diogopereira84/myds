package com.fedex.automation.service.mirakl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.config.MiraklConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}