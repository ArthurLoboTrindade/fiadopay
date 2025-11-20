package edu.ucsal.fiadopay.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class WebhookSignatureService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;

    public WebhookSignatureService(@Value("${fiadopay.webhook-secret}") String secret) {
        this.secret = secret;
    }

    public String generateSignature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKey);

            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new SignatureGenerationException("Failed to generate HMAC signature", e);
        }
    }

    public static class SignatureGenerationException extends RuntimeException {
        public SignatureGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
