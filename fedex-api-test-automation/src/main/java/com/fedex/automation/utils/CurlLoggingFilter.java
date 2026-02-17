package com.fedex.automation.utils;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
public class CurlLoggingFilter implements Filter {

    @Value("${logging.curl.enabled:false}")
    private boolean curlEnabled;

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {

        // --- 1. Request Logging ---
        if (curlEnabled) {
            logCurl(requestSpec);
        } else {
            log.info("Request: {} {}", requestSpec.getMethod(), requestSpec.getURI());
        }

        // --- 2. Execution ---
        Response response = ctx.next(requestSpec, responseSpec);

        // --- 3. Response Logging (DEBUG Level) ---
        if (log.isDebugEnabled()) {
            log.debug("Response Status: {} {}", response.getStatusCode(), response.getStatusLine());

            String contentType = response.getContentType();
            if (contentType != null && (contentType.contains("json") || contentType.contains("xml") || contentType.contains("text"))) {
                log.debug("Response Body:\n{}", response.getBody().asPrettyString());
            } else {
                log.debug("Response Body: [Content-Type: {} - Binary or Non-printable]", contentType);
            }
        }

        return response;
    }

    private void logCurl(FilterableRequestSpecification requestSpec) {
        StringBuilder curl = new StringBuilder("\n--------------------------------------------------------------------------------\n");
        curl.append("curl --location --request ").append(requestSpec.getMethod());
        curl.append(" '").append(requestSpec.getURI()).append("'");

        // Headers
        for (Header header : requestSpec.getHeaders()) {
            curl.append(" \\\n--header '").append(header.getName()).append(": ").append(header.getValue()).append("'");
        }

        // Cookies - FIXED: Cookies object doesn't have isEmpty(), used size() > 0
        if (requestSpec.getCookies().size() > 0) {
            String cookieString = requestSpec.getCookies().asList().stream()
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));
            curl.append(" \\\n--header 'Cookie: ").append(cookieString).append("'");
        }

        // Body
        if (requestSpec.getBody() != null) {
            String body = requestSpec.getBody().toString();
            // Escape single quotes for shell safety
            body = body.replace("'", "'\\''");
            curl.append(" \\\n--data-raw '").append(body).append("'");
        }

        curl.append("\n--------------------------------------------------------------------------------");
        log.info(curl.toString());
    }
}