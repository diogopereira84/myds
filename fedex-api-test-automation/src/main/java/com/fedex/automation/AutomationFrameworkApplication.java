package com.fedex.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutomationFrameworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationFrameworkApplication.class, args);
    }

    // This makes 'ObjectMapper' available for @Autowired in Services and Tests
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}