package com.fedex.automation.steps;

import com.fedex.automation.service.fedex.SessionService;
import io.cucumber.java.en.Given;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class CustomerLoginSteps {

    @Autowired
    private SessionService sessionService;

    @Given("I am logged in as a registered user with username {string} and password {string}")
    public void iAmLoggedInAsARegisteredUser(String username, String password) {
        log.info("--- [Step] Login as Registered User: {} ---", username);
        sessionService.login(username, password);
    }
}