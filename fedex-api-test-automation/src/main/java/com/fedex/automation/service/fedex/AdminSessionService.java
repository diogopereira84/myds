package com.fedex.automation.service.fedex;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Service
public class AdminSessionService {

    @Value("${base.url}")
    private String baseUrl;

    @Value("${admin.path}")
    private String adminPath;

    @Value("${admin.username}")
    private String username;

    @Value("${admin.password}")
    private String password;

    private final CookieFilter adminCookieFilter = new CookieFilter();
    private String adminBearerToken;

    /**
     * 1. Logs into the Admin Panel (Browser Session)
     */
    public void bootstrapAdminSession() {
        log.info("--- [Admin] Bootstrapping Admin Session ---");

        String loginUrl = baseUrl + adminPath + "/admin/auth/login/";
        Response pageResp = given().relaxedHTTPSValidation().filter(adminCookieFilter).get(loginUrl);
        String formKey = extractFormKey(pageResp.asString());

        Response loginResp = given()
                .relaxedHTTPSValidation()
                .filter(adminCookieFilter)
                .formParam("login[username]", username)
                .formParam("login[password]", password)
                .formParam("form_key", formKey)
                .post(loginUrl);

        if (loginResp.statusCode() != 200 && loginResp.statusCode() != 302) {
            fail("Admin Login failed! Status: " + loginResp.statusCode());
        }
        log.info("--- [Admin] Login Successful ---");
    }

    /**
     * 2. Resolves Increment ID (e.g., 2010...) to Entity ID (e.g., 5421)
     */
    public String resolveEntityId(String incrementId) {
        if (adminBearerToken == null) {
            getAdminBearerToken();
        }

        log.info("Resolving Entity ID for Order #{}", incrementId);

        Response response = given()
                .relaxedHTTPSValidation()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer " + adminBearerToken)
                .queryParam("searchCriteria[filter_groups][0][filters][0][field]", "increment_id")
                .queryParam("searchCriteria[filter_groups][0][filters][0][value]", incrementId)
                .get("/rest/V1/orders");

        int totalCount = response.jsonPath().getInt("total_count");
        if (totalCount == 0) {
            throw new RuntimeException("Order #" + incrementId + " not found via REST API.");
        }

        String entityId = response.jsonPath().getString("items[0].entity_id");
        log.info("Resolved Order #{} -> Entity ID: {}", incrementId, entityId);
        return entityId;
    }

    private void getAdminBearerToken() {
        Response response = given()
                .relaxedHTTPSValidation()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .body("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
                .post("/rest/V1/integration/admin/token");

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get Admin Token: " + response.asString());
        }
        this.adminBearerToken = response.asString().replace("\"", "");
    }

    public RequestSpecification adminRequest() {
        return given().relaxedHTTPSValidation().baseUri(baseUrl).filter(adminCookieFilter);
    }

    public String scrapeSendToMiraklUrl(String orderEntityId) {
        // Use the Entity ID to build the URL
        String url = baseUrl + adminPath + "/sales/order/view/order_id/" + orderEntityId;
        log.info("Accessing Order Page: {}", url);

        Response response = adminRequest().get(url);
        String html = response.asString();

        // Regex to find the Mirakl Send URL (matches escaped JSON or standard HTML)
        // Matches: "url":"...mirakl/order/send..." OR 'url':'...mirakl/order/send...'
        Pattern p = Pattern.compile("[\"']url[\"']\\s*:\\s*[\"']([^\"']*mirakl\\\\?/order\\\\?/send[^\"']*)[\"']");
        Matcher m = p.matcher(html);

        if (m.find()) {
            // Unescape the slashes (e.g., \/ -> /)
            return m.group(1).replace("\\/", "/");
        }

        // Backup Regex: Look for the raw button onclick or data-url attribute
        Pattern p2 = Pattern.compile("setLocation\\(['\"]([^'\"]*mirakl/order/send[^'\"]*)['\"]\\)");
        Matcher m2 = p2.matcher(html);
        if(m2.find()) {
            return m2.group(1);
        }

        throw new RuntimeException("Could not find 'Send to Mirakl' URL on page for ID " + orderEntityId + ". Is the button visible?");
    }

    private String extractFormKey(String html) {
        Pattern p = Pattern.compile("form_key\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }
}