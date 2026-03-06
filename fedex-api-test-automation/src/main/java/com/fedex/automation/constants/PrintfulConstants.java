package com.fedex.automation.constants;

import java.util.regex.Pattern;

public final class PrintfulConstants {

    private PrintfulConstants() {
        // Restrict instantiation
    }

    // Known Fixed Constants
    public static final String PRINTFUL_CATEGORY_ID = "18";

    // Cookie Keys
    public static final String COOKIE_PHPSESSID = "PHPSESSID";
    public static final String COOKIE_FORM_KEY = "form_key";

    // Pre-compiled Regex Patterns (Better performance)
    public static final Pattern URL_FORM_KEY_PATTERN = Pattern.compile("form_key/([a-zA-Z0-9]+)");
    public static final Pattern HTML_FORM_KEY_PATTERN = Pattern.compile("name=\"form_key\"\\s+value=\"([a-zA-Z0-9]+)\"");
    public static final Pattern SESSION_ID_PATTERN = Pattern.compile("session_id=([a-f0-9]{32})", Pattern.CASE_INSENSITIVE);
    public static final Pattern UUID_PATTERN = Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})", Pattern.CASE_INSENSITIVE);
}