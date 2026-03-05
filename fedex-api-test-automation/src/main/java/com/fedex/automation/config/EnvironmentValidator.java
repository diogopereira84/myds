package com.fedex.automation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EnvironmentValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentValidator.class);

    private EnvironmentValidator() {
        // utility class
    }

    public static void validate(Environment environment,
                                boolean validationEnabled,
                                String requiredEnvKeys,
                                String appProfileName) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment must not be null.");
        }

        LOGGER.info("Active profiles: {}", String.join(",", environment.getActiveProfiles()));
        LOGGER.info("Configured profile marker: {}", appProfileName);

        if (!validationEnabled) {
            LOGGER.warn("Environment validation disabled (env.validation.enabled=false).");
            return;
        }

        List<String> missingVars = getMissingRequiredKeys(environment, requiredEnvKeys);

        if (!missingVars.isEmpty()) {
            LOGGER.error("\n===================================================================");
            LOGGER.error(" CRITICAL: MISSING ENVIRONMENT VARIABLES ");
            LOGGER.error("The framework cannot start because the following required variables are not set:");

            for (String var : missingVars) {
                LOGGER.error("  -> {}", var);
            }

            LOGGER.error("Please configure them in your IntelliJ Run Configuration or export them in your terminal.");
            LOGGER.error("===================================================================\n");

            throw new IllegalStateException("Test execution aborted due to missing environment variables.");
        }

        LOGGER.info(" All mandatory environment variables are successfully loaded.");
    }

    private static List<String> getMissingRequiredKeys(Environment environment, String requiredEnvKeys) {
        List<String> missingVars = new ArrayList<>();
        if (requiredEnvKeys == null || requiredEnvKeys.isBlank()) {
            return missingVars;
        }

        Arrays.stream(requiredEnvKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .forEach(key -> {
                    String value = environment.getProperty(key);
                    if (value == null || value.isBlank()) {
                        missingVars.add(key);
                    }
                });
        return missingVars;
    }
}