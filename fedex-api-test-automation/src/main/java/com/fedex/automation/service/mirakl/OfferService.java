package com.fedex.automation.service.mirakl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fedex.automation.config.AdobeConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferService {

    @Autowired
    private AdobeConfig adobeConfig;

    @Autowired
    private ObjectMapper objectMapper;

    public String searchProductSku(String productName) {
        log.info("--- Searching Catalog for Product: '{}' ---", productName);

        // The GraphQL Query we validated
        String query = """
            {
              "query": "query productSearch($phrase: String!) { productSearch(phrase: $phrase, current_page: 1, page_size: 1) { items { productView { name sku } } } }",
              "variables": {
                "phrase": "%s"
              }
            }
            """.formatted(productName);

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Api-Key", adobeConfig.getApiKey())
                .header("Magento-Environment-Id", adobeConfig.getEnvironmentId())
                .header("Magento-Website-Code", adobeConfig.getWebsiteCode())
                .header("Magento-Store-Code", adobeConfig.getStoreCode())
                .header("Magento-Store-View-Code", adobeConfig.getStoreViewCode())
                .body(query)
                .post(adobeConfig.getGraphqlEndpoint())
                .then()
                .statusCode(200)
                .extract()
                .response();

        try {
            JsonNode root = objectMapper.readTree(response.asString());

            // Check for errors (like missing headers)
            if (root.has("errors")) {
                throw new RuntimeException("Adobe API Error: " + root.path("errors").toPrettyString());
            }

            JsonNode items = root.path("data")
                    .path("productSearch")
                    .path("items");

            if (items.isEmpty()) {
                throw new RuntimeException("No products found for phrase: " + productName);
            }

            String sku = items.get(0).path("productView").path("sku").asText();
            log.info("Found SKU: {}", sku);
            return sku;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Catalog response", e);
        }
    }
}