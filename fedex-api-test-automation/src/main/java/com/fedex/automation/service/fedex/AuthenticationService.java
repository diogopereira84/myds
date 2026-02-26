package com.fedex.automation.service.fedex;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuthenticationService {

    @Value("${endpoint.auth.secure-login}")
    private String loginPageUrl;

    public Map<String, String> login(String username, String password, Map<String, String> initialCookies) {
        log.info("Starting Browser-based Auth Flow at: {}", loginPageUrl);

        Map<String, String> restAssuredCookies = new HashMap<>();

        List<String> launchArgs = List.of(
                "--disable-blink-features=AutomationControlled"
        );

        try (Playwright playwright = Playwright.create()) {

            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(launchArgs)
                    .setSlowMo(100);

            log.info("Launching browser with args: {}", launchArgs);
            Browser browser = playwright.chromium().launch(options);

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setLocale("en-US")
                    .setViewportSize(null);

            BrowserContext context = browser.newContext(contextOptions);
            Page page = context.newPage();

            log.info("Navigating to login page...");
            page.navigate(loginPageUrl);

            handleCookieConsent(page);

            log.info("Filling credentials...");
            page.fill("#username", username);
            page.fill("#password", password);

            log.info("Clicking login...");
            page.click("#login_button");

            log.info("Waiting for successful login state...");
            try {
                page.waitForCondition(() ->
                                context.cookies().stream().anyMatch(c ->
                                        c.name.contains("FDX_LOGIN") ||
                                                c.name.equalsIgnoreCase("x-action-token") ||
                                                c.name.equalsIgnoreCase("fdx_login")
                                ),
                        new Page.WaitForConditionOptions().setTimeout(30000)
                );
            } catch (Exception e) {
                log.error("Login timed out. Check the open browser window for errors.");
                throw new RuntimeException("Timeout waiting for login cookies.", e);
            }

            log.info("Login successful. Extracting cookies...");

            List<Cookie> playwrightCookies = context.cookies();
            for (Cookie c : playwrightCookies) {
                restAssuredCookies.put(c.name, c.value);
            }

            log.info("Extracted {} cookies.", restAssuredCookies.size());

        } catch (Exception e) {
            log.error("Browser Login failed: {}", e.getMessage());
            throw new RuntimeException("Browser Login Failed", e);
        }

        return restAssuredCookies;
    }

    private void handleCookieConsent(Page page) {
        log.info("Checking for cookie banners...");
        try {
            String selector = "button:has-text('Accept All Cookies'), " +
                    "button:has-text('Accept Cookies'), " +
                    "button:has-text('I Accept'), " +
                    "button:has-text('Agree'), " +
                    "#onetrust-accept-btn-handler";

            Locator cookieBtn = page.locator(selector).first();

            if (cookieBtn.isVisible()) {
                log.info("Cookie banner found. Clicking accept...");
                cookieBtn.click();
                page.waitForTimeout(1000);
            } else {
                log.info("No cookie banner detected.");
            }
        } catch (Exception e) {
            log.warn("Cookie banner handling skipped: {}", e.getMessage());
        }
    }
}