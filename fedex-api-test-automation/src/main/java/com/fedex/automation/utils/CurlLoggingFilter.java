package com.fedex.automation.utils;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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

        if (curlEnabled) {
            logCurl(requestSpec);
        } else {
            log.info("Request: {} {}", requestSpec.getMethod(), buildFullUrl(requestSpec));
        }

        Response response = ctx.next(requestSpec, responseSpec);

        if (log.isDebugEnabled()) {
            log.debug("Response Status: {} {}", response.getStatusCode(), response.getStatusLine());
            if (isPrintable(response.getContentType())) {
                log.debug("Response Body:\n{}", response.getBody().asPrettyString());
            }
        }

        return response;
    }

    private void logCurl(FilterableRequestSpecification requestSpec) {
        StringBuilder curl = new StringBuilder("\n--------------------------------------------------------------------------------\n");
        curl.append("curl --location --request ").append(requestSpec.getMethod());

        String fullUrl = buildFullUrl(requestSpec);
        curl.append(" '").append(fullUrl).append("'");

        // Headers
        for (Header header : requestSpec.getHeaders()) {
            curl.append(" \\\n--header '").append(header.getName()).append(": ").append(header.getValue()).append("'");
        }

        // Cookies
        if (requestSpec.getCookies().size() > 0) {
            String cookieString = requestSpec.getCookies().asList().stream()
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));
            curl.append(" \\\n--header 'Cookie: ").append(cookieString).append("'");
        }

        // Body or Form Params
        // 1. Check Body (JSON/Raw)
        if (requestSpec.getBody() != null) {
            String body = requestSpec.getBody().toString().replace("'", "'\\''");
            curl.append(" \\\n--data-raw '").append(body).append("'");
        }
        // 2. Check Form Params (x-www-form-urlencoded) - FIX: Handle ArrayList values & incorrect Generic Type
        else if (!requestSpec.getFormParams().isEmpty()) {
            StringBuilder formString = new StringBuilder();

            // CRITICAL FIX: RestAssured interface says Map<String, String>, but implementation returns Map<String, Object>.
            // We strictly cast to Map<String, Object> to avoid ClassCastException when value is a List.
            @SuppressWarnings("unchecked")
            Map<String, Object> formParams = (Map<String, Object>) (Map) requestSpec.getFormParams();

            for (Map.Entry<String, Object> entry : formParams.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof List) {
                    // Handle multi-value fields like 'street[]'
                    for (Object v : (List<?>) value) {
                        if (formString.length() > 0) formString.append("&");
                        formString.append(key).append("=").append(v);
                    }
                } else {
                    // Handle standard single value fields
                    if (formString.length() > 0) formString.append("&");
                    formString.append(key).append("=").append(value);
                }
            }
            curl.append(" \\\n--data-raw '").append(formString).append("'");
        }

        curl.append("\n--------------------------------------------------------------------------------");
        log.info(curl.toString());
    }

    private String buildFullUrl(FilterableRequestSpecification requestSpec) {
        String url = requestSpec.getURI();
        Map<String, String> queryParams = requestSpec.getQueryParams();

        if (!queryParams.isEmpty()) {
            String queryString = queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));

            if (url.contains("?")) {
                url += "&" + queryString;
            } else {
                url += "?" + queryString;
            }
        }
        return url;
    }

    private boolean isPrintable(String contentType) {
        return contentType != null && (contentType.contains("json") || contentType.contains("xml") || contentType.contains("text"));
    }
}