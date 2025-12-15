package com.fedex.automation.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

public class MiraklConfig {

    @Value("${mirakl.ApiKey}")
    private String apiKey;
}
