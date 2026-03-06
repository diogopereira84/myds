package com.fedex.automation.service.fedex.client;

import com.fedex.automation.config.AdobeConfig;
import com.fedex.automation.model.graphql.GraphqlRequestBody;
import com.fedex.automation.service.fedex.exception.CatalogErrorCode;
import com.fedex.automation.service.fedex.exception.CatalogOperationException;
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
    public String requestProductSearchBody(GraphqlRequestBody requestBody, RequestSpecification spec) {
        // Enforce usage of the injected spec
        RequestSpecification request = (spec != null) ? given().spec(spec) : given();

        Response response = request
                .contentType(ContentType.JSON)
                .header("X-Api-Key", adobeConfig.getApiKey())
                .header("Magento-Environment-Id", adobeConfig.getEnvironmentId())
                .header("Magento-Website-Code", adobeConfig.getWebsiteCode())
                .header("Magento-Store-Code", adobeConfig.getStoreCode())
                .header("Magento-Store-View-Code", adobeConfig.getStoreViewCode())
                .body(requestBody)
                .post(adobeConfig.getGraphqlEndpoint());

        if (response == null) {
            throw new CatalogOperationException(CatalogErrorCode.NULL_RESPONSE, "Catalog API response was null");
        }

        String responseBody = response.asString();
        int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            String statusLine = response.getStatusLine();
            throw new CatalogOperationException(
                    CatalogErrorCode.UPSTREAM_STATUS_ERROR,
                    "Catalog API failed with status " + statusLine + ". Body: " + truncate(responseBody)
            );
        }

        return responseBody;
    }


    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        int max = 800;
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}