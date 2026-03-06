package com.fedex.automation.utils;

import com.fedex.automation.constants.PrintfulConstants;
import io.restassured.response.Response;
import java.util.regex.Matcher;

public final class PrintfulExtractorUtil {

    private PrintfulExtractorUtil() {}

    public static String extractFormKey(Response response, String redirectLocation) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
        if (response.getCookies().containsKey(PrintfulConstants.COOKIE_FORM_KEY)) {
            return response.getCookies().get(PrintfulConstants.COOKIE_FORM_KEY);
        }

        String responseBody = response.getBody() != null ? response.asString() : "";
        String safeLocation = redirectLocation != null ? redirectLocation : "";

        Matcher locationMatcher = PrintfulConstants.URL_FORM_KEY_PATTERN.matcher(safeLocation);
        if (locationMatcher.find()) return locationMatcher.group(1);

        Matcher bodyUrlMatcher = PrintfulConstants.URL_FORM_KEY_PATTERN.matcher(responseBody);
        if (bodyUrlMatcher.find()) return bodyUrlMatcher.group(1);

        Matcher htmlMatcher = PrintfulConstants.HTML_FORM_KEY_PATTERN.matcher(responseBody);
        if (htmlMatcher.find()) return htmlMatcher.group(1);

        return null;
    }

    public static String extractSessionId(String redirectLocation, String responseBody) {
        String searchTarget = (redirectLocation != null ? redirectLocation : "") + responseBody;
        Matcher matcher = PrintfulConstants.SESSION_ID_PATTERN.matcher(searchTarget);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String extractExternalProductId(String redirectLocation, String responseBody) {
        String searchTarget = (redirectLocation != null ? redirectLocation : "") + responseBody;
        Matcher matcher = PrintfulConstants.UUID_PATTERN.matcher(searchTarget);
        return matcher.find() ? matcher.group(1) : null;
    }
}