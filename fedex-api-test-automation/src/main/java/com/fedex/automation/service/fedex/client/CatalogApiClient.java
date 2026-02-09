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

    // CHANGED: Accepts 'Object requestBody' instead of 'String query'
    public Response searchProducts(Object requestBody, RequestSpecification spec) {
        RequestSpecification finalSpec = (spec != null) ? given().spec(spec) : given();

        return finalSpec
                .contentType(ContentType.JSON)
                .header("X-Api-Key", adobeConfig.getApiKey())
                .header("Magento-Environment-Id", adobeConfig.getEnvironmentId())
                .header("Magento-Website-Code", adobeConfig.getWebsiteCode())
                .header("Magento-Store-Code", adobeConfig.getStoreCode())
                .header("Magento-Store-View-Code", adobeConfig.getStoreViewCode())
                .body(requestBody) // RestAssured + Jackson will make this valid JSON
                .post(adobeConfig.getGraphqlEndpoint());
    }
}