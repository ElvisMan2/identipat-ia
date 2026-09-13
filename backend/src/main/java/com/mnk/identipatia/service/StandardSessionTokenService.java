package com.mnk.identipatia.service;

import com.mnk.identipatia.config.StandardSessionProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class StandardSessionTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] pepper;

    public StandardSessionTokenService(StandardSessionProperties properties) {
        this.pepper = properties.decodedPepper();
    }

    public String generateToken() {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public byte[] hash(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to protect STANDARD session token", ex);
        }
    }
}
