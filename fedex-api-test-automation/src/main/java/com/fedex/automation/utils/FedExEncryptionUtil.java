package com.fedex.automation.utils;

import javax.crypto.Cipher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FedExEncryptionUtil {

    private static final String RSA_ALG = "RSA";

    // PROVEN BY SOURCE CODE (rsaes-oaep.js):
    // It uses 'rusha.js' (SHA-1) for hashing.
    // It uses MGF1 with SHA-1.
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z0-9\\s]+)-----([\\s\\S]*?)-----END \\1-----"
    );

    private FedExEncryptionUtil() {
        // utility class
    }

    /**
     * Encrypts credit card data matching 'paymentScreen.js' logic exactly.
     * Source: paymentScreen.js line 937
     * Format: 'M' + Number + '=' + YY + MM + ':' + CVV
     * * @param ccNumber     Raw 16-digit card number (UNMASKED)
     * @param month        Month (e.g., "2" or "02")
     * @param year         Year (e.g., "2035")
     * @param cvv          CVV code
     * @param publicKeyPEM RSA public key string (PEM format)
     * @return URL-Encoded Base64 encrypted string
     */
    public static String encryptCreditCard(String ccNumber, String month, String year, String cvv, String publicKeyPEM) {
        try {
            // 1. Build the specific payload string expected by the gateway
            String payload = buildLegacyPayload(ccNumber, month, year, cvv);

            // 2. Parse the Key
            PublicKey publicKey = parsePublicKey(publicKeyPEM);

            // 3. Encrypt using OAEP + SHA-1
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // 4. Base64 Encode
            String rawBase64 = Base64.getEncoder().encodeToString(encryptedBytes);

            // 5. URL Encode (Prevents '+' corruption during transmission)
            return URLEncoder.encode(rawBase64, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credit card data", e);
        }
    }

    /**
     * Replicates JS Logic: 'M' + ccNum + '=' + YY + MM + ':' + CVV
     */
    static String buildLegacyPayload(String ccNumber, String month, String year, String cvv) {
        String cleanCC = digitsOnly(required(ccNumber, "ccNumber"));
        String cleanCVV = required(cvv, "cvv").trim();

        // JS Logic: const yr = obj.year.substring(2, 4);
        String yy = normalizeYear(year);

        // JS Logic: pads month to 2 chars
        String mm = normalizeMonth(month);

        return "M" + cleanCC + "=" + yy + mm + ":" + cleanCVV;
    }

    static String normalizeYear(String year) {
        String y = digitsOnly(year);
        if (y.length() == 4) return y.substring(2); // "2035" -> "35"
        if (y.length() == 2) return y;
        throw new IllegalArgumentException("Invalid year format (need 4 digits): " + year);
    }

    static String normalizeMonth(String month) {
        String m = digitsOnly(month);
        if (m.length() == 1) return "0" + m;
        if (m.length() == 2) return m;
        throw new IllegalArgumentException("Invalid month format: " + month);
    }

    static PublicKey parsePublicKey(String publicKeyPEM) throws Exception {
        String pem = required(publicKeyPEM, "publicKeyPEM").trim();
        String base64Body = extractPemBody(pem);
        if (base64Body == null) {
            base64Body = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Body);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance(RSA_ALG).generatePublic(spec);
    }

    static String extractPemBody(String pem) {
        Matcher m = PEM_BLOCK.matcher(pem);
        if (!m.find()) return null;
        return m.group(2).replaceAll("\\s+", "");
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private static String required(String value, String fieldName) {
        if (value == null) throw new IllegalArgumentException(fieldName + " must not be null.");
        return value;
    }
}