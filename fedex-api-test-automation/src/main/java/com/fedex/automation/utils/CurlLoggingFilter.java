package com.fedex.automation.utils;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CurlLoggingFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {

        StringBuilder curl = new StringBuilder("curl -v");

        // Method
        curl.append(" -X ").append(requestSpec.getMethod());

        // URL
        curl.append(" '").append(requestSpec.getURI()).append("'");

        // Headers
        for (Header header : requestSpec.getHeaders()) {
            if (!header.getName().equalsIgnoreCase("Content-Length") &&
                    !header.getName().equalsIgnoreCase("Host")) {
                curl.append(" -H '").append(header.getName()).append(": ").append(header.getValue()).append("'");
            }
        }

        // Form Params (Fix: Handle List/ArrayList values)
        Map<String, ?> formParams = requestSpec.getFormParams();
        if (formParams != null && !formParams.isEmpty()) {
            String paramString = formParams.entrySet().stream()
                    .map(e -> {
                        String key = e.getKey();
                        Object val = e.getValue();
                        // Handle multi-value parameters (e.g., street[]) stored as List
                        if (val instanceof List) {
                            return ((List<?>) val).stream()
                                    .map(v -> key + "=" + v)
                                    .collect(Collectors.joining("&"));
                        } else {
                            return key + "=" + val;
                        }
                    })
                    .collect(Collectors.joining("&"));

            curl.append(" -d '").append(paramString).append("'");
        }
        // JSON Body
        else if (requestSpec.getBody() != null) {
            String body = requestSpec.getBody().toString();
            // Simple escape for single quotes to prevent breaking shell command
            body = body.replace("'", "'\\''");
            curl.append(" -d '").append(body).append("'");
        }

        log.info("\n=== Generated cURL ===\n{}\n======================\n", curl);

        return ctx.next(requestSpec, responseSpec);
    }
}