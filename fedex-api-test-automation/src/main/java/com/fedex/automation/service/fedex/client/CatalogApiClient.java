package com.fedex.automation.service.fedex.client;

import com.fedex.automation.config.AdobeConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static io.restassured.RestAssured.given;

@Component
@RequiredArgsConstructor
public class CatalogApiClient {

    private final AdobeConfig adobeConfig;

    /**
     * Executes the search against Adobe GraphQL.
     * @param requestBody The GraphQL wrapper object
     * @param spec The RequestSpecification (must include logging filter)
     */
    public Response searchProducts(Object requestBody, RequestSpecification spec) {
        // Enforce usage of the injected spec
        RequestSpecification request = (spec != null) ? given().spec(spec) : given();

        return request
                .contentType(ContentType.JSON)
                .header("X-Api-Key", adobeConfig.getApiKey())
                .header("Magento-Environment-Id", adobeConfig.getEnvironmentId())
                .header("Magento-Website-Code", adobeConfig.getWebsiteCode())
                .header("Magento-Store-Code", adobeConfig.getStoreCode())
                .header("Magento-Store-View-Code", adobeConfig.getStoreViewCode())
                .body(requestBody)
                .post(adobeConfig.getGraphqlEndpoint());
    }
}