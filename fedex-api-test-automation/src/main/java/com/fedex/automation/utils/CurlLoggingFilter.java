package com.fedex.automation.utils;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CurlLoggingFilter implements Filter {

    @Value("${logging.curl.enabled:false}")
    private boolean curlEnabled;

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {

        // 1. Log Request (Either Full CURL or Simple Summary)
        if (curlEnabled) {
            logCurl(requestSpec);
        } else {
            log.info("Request: {} {}", requestSpec.getMethod(), requestSpec.getURI());
        }

        // 2. Execute Request
        Response response = ctx.next(requestSpec, responseSpec);

        // 3. Log Response Body (Only if DEBUG is enabled in application.properties)
        if (log.isDebugEnabled()) {
            log.debug("Response Status: {}", response.getStatusCode());
            log.debug("Response Body: \n{}", response.getBody().asPrettyString());
        }

        return response;
    }

    private void logCurl(FilterableRequestSpecification requestSpec) {
        StringBuilder curl = new StringBuilder("curl");
        curl.append(" -X ").append(requestSpec.getMethod());
        curl.append(" '").append(requestSpec.getURI()).append("'");

        requestSpec.getHeaders().forEach(header ->
                curl.append(" -H '").append(header.getName()).append(": ").append(header.getValue()).append("'")
        );

        if (requestSpec.getBody() != null) {
            String body = requestSpec.getBody().toString().replace("'", "'\\''");
            curl.append(" --data-raw '").append(body).append("'");
        }

        log.info("--------------------------------------------------------------------------------");
        log.info(curl.toString());
        log.info("--------------------------------------------------------------------------------");
    }
}