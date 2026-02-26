package com.fedex.automation.config;

import com.fedex.automation.utils.CurlLoggingFilter;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RestConfig {

    private final CurlLoggingFilter curlLoggingFilter;

    @Bean
    public RequestSpecification defaultRequestSpec() {
        return new RequestSpecBuilder()
                .setRelaxedHTTPSValidation()
                // Filters removed from here to prevent double-logging,
                // as they are applied globally in the PostConstruct below.
                .build();
    }

    @PostConstruct
    public void configureGlobalRestAssured() {
        // Apply filters globally so every single RestAssured call is caught
        RestAssured.filters(curlLoggingFilter, new SafeAllureRestAssuredFilter());

        RestAssured.config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails());
    }

    /**
     * Custom wrapper to prevent Allure's "no test is running" error.
     * It only attaches request/response logs if a Cucumber scenario is actively running.
     */
    public static class SafeAllureRestAssuredFilter implements Filter {
        private final AllureRestAssured allureRestAssured = new AllureRestAssured();

        @Override
        public Response filter(FilterableRequestSpecification requestSpec,
                               FilterableResponseSpecification responseSpec,
                               FilterContext ctx) {

            // Check if Allure context is active before attempting to attach logs
            if (Allure.getLifecycle().getCurrentTestCase().isPresent()) {
                return allureRestAssured.filter(requestSpec, responseSpec, ctx);
            }

            // If no test is running (e.g., Spring context initialization), just proceed normally
            return ctx.next(requestSpec, responseSpec);
        }
    }
}