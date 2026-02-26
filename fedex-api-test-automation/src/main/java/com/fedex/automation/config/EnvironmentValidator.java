package com.fedex.automation.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class EnvironmentValidator {

    @Value("${TEST_ENV:}")
    private String testEnv;

    @Value("${SANDBOX_PUBLIC_APIKEY:}")
    private String sandboxApiKey;

    @Value("${SANDBOX_ENV_ID:}")
    private String sandboxEnvId;

    @Value("${MIRAKL_APIKEY:}")
    private String miraklApiKey;

    @Value("${API_GATEWAY_CLIENT_ID:}")
    private String apiGatewayClientId;

    @Value("${MAGENTO_UI_CLIENT_ID:}")
    private String magentoUiClientId;

    @Value("${ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @PostConstruct
    public void validate() {
        List<String> missingVars = new ArrayList<>();

        if (testEnv == null || testEnv.isBlank()) missingVars.add("TEST_ENV");
        if (sandboxApiKey == null || sandboxApiKey.isBlank()) missingVars.add("SANDBOX_PUBLIC_APIKEY");
        if (sandboxEnvId == null || sandboxEnvId.isBlank()) missingVars.add("SANDBOX_ENV_ID");
        if (miraklApiKey == null || miraklApiKey.isBlank()) missingVars.add("MIRAKL_APIKEY");
        if (apiGatewayClientId == null || apiGatewayClientId.isBlank()) missingVars.add("API_GATEWAY_CLIENT_ID");
        if (magentoUiClientId == null || magentoUiClientId.isBlank()) missingVars.add("MAGENTO_UI_CLIENT_ID");
        if (adminUsername == null || adminUsername.isBlank()) missingVars.add("ADMIN_USERNAME");
        if (adminPassword == null || adminPassword.isBlank()) missingVars.add("ADMIN_PASSWORD");

        // If any variables are missing, log a clear error and abort
        if (!missingVars.isEmpty()) {
            log.error("\n===================================================================");
            log.error(" CRITICAL: MISSING ENVIRONMENT VARIABLES ");
            log.error("The framework cannot start because the following required variables are not set:");

            for (String var : missingVars) {
                log.error("  -> {}", var);
            }

            log.error("Please configure them in your IntelliJ Run Configuration or export them in your terminal.");
            log.error("===================================================================\n");

            throw new IllegalStateException("Test execution aborted due to missing environment variables.");
        }

        log.info(" All mandatory environment variables are successfully loaded.");
    }
}