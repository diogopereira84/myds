package com.fedex.automation.service.mirakl.client;

import com.fedex.automation.config.MiraklConfig;
import io.restassured.specification.RequestSpecification;
import org.springframework.stereotype.Component;

import static io.restassured.RestAssured.given;

@Component
public class MiraklRequestFactory {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String MIME_APPLICATION_JSON = "application/json";

    private final MiraklConfig miraklConfig;
    private final RequestSpecification defaultRequestSpec;

    public MiraklRequestFactory(MiraklConfig miraklConfig, RequestSpecification defaultRequestSpec) {
        this.miraklConfig = miraklConfig;
        this.defaultRequestSpec = defaultRequestSpec;
    }

    public RequestSpecification baseRequest() {
        return given()
                .spec(defaultRequestSpec)
                .baseUri(miraklConfig.getBaseUrl())
                .header(HEADER_AUTHORIZATION, miraklConfig.getApiKey())
                .header(HEADER_ACCEPT, MIME_APPLICATION_JSON);
    }
}

