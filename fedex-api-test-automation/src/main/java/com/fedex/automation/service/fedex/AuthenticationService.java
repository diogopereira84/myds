package com.fedex.automation.service.fedex;

import com.fedex.automation.utils.CurlLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
public class AuthenticationService {

    private static final String AUTH_URL = "https://authtest.fedex.com/am/json/realms/root/realms/alpha/authenticate";
    private static final String LOGIN_COOKIE_URL = "https://apitest.fedex.com/user/v2/logincookie";

    // --- HEADERS FROM YOUR CURL COMMAND ---
    private static final String REFERER = "https://wwwtest.fedex.com/secure-login/";
    private static final String ORIGIN = "https://wwwtest.fedex.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0";

    // Note: Escaping quotes for Java strings
    private static final String SEC_CH_UA = "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Microsoft Edge\";v=\"144\"";
    private static final String SEC_CH_UA_PLATFORM = "\"Windows\"";
    private static final String ACCEPT_API_VERSION = "protocol=1.0,resource=2.1";

    // Minified Device Profile
    private static final String DEVICE_PROFILE_JSON = "{\"identifier\":\"1069463759-4256806750-2601894210\",\"metadata\":{\"hardware\":{\"cpuClass\":null,\"deviceMemory\":8,\"hardwareConcurrency\":8,\"maxTouchPoints\":0,\"oscpu\":null,\"display\":{\"width\":1536,\"height\":864,\"pixelDepth\":24,\"angle\":0}},\"browser\":{\"userAgent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0\",\"appName\":\"Netscape\",\"appCodeName\":\"Edge\",\"appVersion\":\"5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0\",\"appMinorVersion\":null,\"buildID\":null,\"product\":\"Gecko\",\"productSub\":\"20030107\",\"vendor\":\"Google Inc.\",\"vendorSub\":\"\",\"browserLanguage\":null,\"plugins\":\"internal-pdf-viewer;internal-pdf-viewer;internal-pdf-viewer;internal-pdf-viewer;internal-pdf-viewer;undefined;\"},\"platform\":{\"language\":\"en-US\",\"platform\":\"Win32\",\"userLanguage\":null,\"systemLanguage\":null,\"deviceName\":\"Windows (Browser)\",\"fonts\":\"cursive;monospace;sans-serif;fantasy;Arial;Arial Black;Arial Narrow;Bookman Old Style;Bradley Hand ITC;Century;Century Gothic;Comic Sans MS;Courier;Courier New;Georgia;Impact;Lucida Console;Monotype Corsiva;Papyrus;Tahoma;Trebuchet MS;Verdana;\",\"timezone\":180}}}";

    public Map<String, String> login(String username, String password, Map<String, String> initialCookies) {
        log.info("Starting Auth Flow at: {}", AUTH_URL);

        Map<String, String> currentCookies = new HashMap<>();
        if (initialCookies != null) {
            currentCookies.putAll(initialCookies);
        }

        try {
            // ==========================================
            // Step 1: Initial Handshake (_si=0)
            // ==========================================
            log.info(">>> STEP 1: Initial Handshake (_si=0)");
            Response initResp = given()
                    .filter(new CurlLoggingFilter()) // Adds cURL to logs
                    .header("accept", "application/json")
                    .header("accept-api-version", ACCEPT_API_VERSION)
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("content-type", "application/json")
                    .header("origin", ORIGIN)
                    .header("priority", "u=1, i")
                    .header("referer", REFERER)
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-site")
                    .header("user-agent", USER_AGENT)
                    .header("x-locale", "en_US")
                    .header("x-requested-with", "forgerock-sdk") // Critical Header
                    .cookies(currentCookies)
                    .queryParam("authIndexType", "service")
                    .queryParam("authIndexValue", "user_login")
                    .queryParam("_si", "0")
                    .body("")
                    .post(AUTH_URL);

            if (initResp.getStatusCode() != 200) {
                logError("Step 1", initResp);
                return new HashMap<>();
            }

            // Accumulate cookies
            if (initResp.getCookies() != null) currentCookies.putAll(initResp.getCookies());

            String authId1 = initResp.jsonPath().getString("authId");
            log.info("Step 1 Success. AuthId: {}", authId1);

            // ==========================================
            // Step 2: Send Device Profile (_si=1)
            // ==========================================
            log.info(">>> STEP 2: Device Profile (_si=1)");
            Map<String, Object> devicePayload = new HashMap<>();
            devicePayload.put("authId", authId1);
            devicePayload.put("callbacks", createCallbackPayload("DeviceProfileCallback", "IDToken1", DEVICE_PROFILE_JSON));

            Response deviceResp = given()
                    .filter(new CurlLoggingFilter())
                    .header("accept", "application/json")
                    .header("accept-api-version", ACCEPT_API_VERSION)
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("content-type", "application/json")
                    .header("origin", ORIGIN)
                    .header("priority", "u=1, i")
                    .header("referer", REFERER)
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-site")
                    .header("user-agent", USER_AGENT)
                    .header("x-locale", "en_US")
                    .header("x-requested-with", "forgerock-sdk")
                    .cookies(currentCookies)
                    .queryParam("authIndexType", "service")
                    .queryParam("authIndexValue", "user_login")
                    .queryParam("_si", "1")
                    .body(devicePayload)
                    .post(AUTH_URL);

            if (deviceResp.getStatusCode() != 200) {
                logError("Step 2 (Device Profile)", deviceResp);
                throw new RuntimeException("Auth Step 2 failed with status " + deviceResp.getStatusCode());
            }

            if (deviceResp.getCookies() != null) currentCookies.putAll(deviceResp.getCookies());

            String authId2 = deviceResp.jsonPath().getString("authId");
            log.info("Step 2 Success. AuthId: {}", authId2);

            // ==========================================
            // Step 3: Send Credentials (_si=2)
            // ==========================================
            log.info(">>> STEP 3: Credentials (_si=2)");
            Map<String, Object> credsPayload = new HashMap<>();
            credsPayload.put("authId", authId2);
            credsPayload.put("callbacks", createCredentialsPayload(username, password));

            Response tokenResp = given()
                    .filter(new CurlLoggingFilter())
                    .header("accept", "application/json")
                    .header("accept-api-version", ACCEPT_API_VERSION)
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("content-type", "application/json")
                    .header("origin", ORIGIN)
                    .header("priority", "u=1, i")
                    .header("referer", REFERER)
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-site")
                    .header("user-agent", USER_AGENT)
                    .header("x-locale", "en_US")
                    .header("x-requested-with", "forgerock-sdk")
                    .cookies(currentCookies)
                    .queryParam("authIndexType", "service")
                    .queryParam("authIndexValue", "user_login")
                    .queryParam("_si", "2")
                    .body(credsPayload)
                    .post(AUTH_URL);

            if (tokenResp.getStatusCode() != 200) {
                logError("Step 3 (Credentials)", tokenResp);
                throw new RuntimeException("Auth Step 3 failed with status " + tokenResp.getStatusCode());
            }

            String tokenId = tokenResp.jsonPath().getString("tokenId");
            if (tokenId == null) tokenId = tokenResp.jsonPath().getString("access_token");

            log.info("Token Generated. Exchanging for cookies...");

            // ==========================================
            // Step 4: Get Login Cookies
            // ==========================================
            log.info(">>> STEP 4: Token Exchange");
            Response cookieResp = given()
                    .filter(new CurlLoggingFilter())
                    .header("Authorization", "Bearer " + tokenId)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Referer", REFERER)
                    .contentType(ContentType.JSON)
                    .cookies(currentCookies)
                    .get(LOGIN_COOKIE_URL);

            if (cookieResp.getCookies() != null) {
                currentCookies.putAll(cookieResp.getCookies());
            }

            return currentCookies;

        } catch (Exception e) {
            log.error("Login flow exception: {}", e.getMessage());
            throw new RuntimeException("Authentication Service Error: " + e.getMessage());
        }
    }

    private void logError(String step, Response response) {
        log.error("{} Failed. Status: {}", step, response.getStatusCode());
        log.error("Response Body: {}", response.getBody().asString());
    }

    private Object[] createCallbackPayload(String type, String inputName, String inputValue) {
        Map<String, Object> input = new HashMap<>();
        input.put("name", inputName);
        input.put("value", inputValue);
        Map<String, Object> callback = new HashMap<>();
        callback.put("type", type);
        callback.put("input", new Object[]{input});
        return new Object[]{callback};
    }

    private Object[] createCredentialsPayload(String username, String password) {
        Map<String, Object> nameInput = new HashMap<>();
        nameInput.put("name", "IDToken1");
        nameInput.put("value", username);
        Map<String, Object> nameCallback = new HashMap<>();
        nameCallback.put("type", "NameCallback");
        nameCallback.put("input", new Object[]{nameInput});
        Map<String, Object> passInput = new HashMap<>();
        passInput.put("name", "IDToken2");
        passInput.put("value", password);
        Map<String, Object> passCallback = new HashMap<>();
        passCallback.put("type", "PasswordCallback");
        passCallback.put("input", new Object[]{passInput});
        return new Object[]{nameCallback, passCallback};
    }
}