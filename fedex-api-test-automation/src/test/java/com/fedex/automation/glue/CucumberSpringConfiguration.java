package com.fedex.automation.glue;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import com.fedex.automation.AutomationFrameworkApplication; // Ensure this imports your main app class

@CucumberContextConfiguration
@SpringBootTest(classes = AutomationFrameworkApplication.class)
public class CucumberSpringConfiguration {
    // This class must be empty.
    // It exists solely to glue Cucumber and Spring Boot together.
}