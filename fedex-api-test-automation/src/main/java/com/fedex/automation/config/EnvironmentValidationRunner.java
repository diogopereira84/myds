package com.fedex.automation.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "env.validation.enabled", havingValue = "true", matchIfMissing = true)
public class EnvironmentValidationRunner implements ApplicationRunner {

    private final Environment environment;

    @Value("${env.validation.enabled:true}")
    private boolean validationEnabled;

    @Value("${env.validation.required:TEST_ENV,SANDBOX_PUBLIC_APIKEY,SANDBOX_ENV_ID,MIRAKL_APIKEY,API_GATEWAY_CLIENT_ID,MAGENTO_UI_CLIENT_ID}")
    private String requiredEnvKeys;

    @Value("${app.profile.name:unset}")
    private String appProfileName;

    @Override
    public void run(ApplicationArguments args) {
        LoggerFactory.getLogger(EnvironmentValidationRunner.class)
                .info("Environment validation enabled={}, requiredKeys={}", validationEnabled, requiredEnvKeys);
        EnvironmentValidator.validate(environment, validationEnabled, requiredEnvKeys, appProfileName);
    }
}
