package com.mnk.identipatia.guidance.service;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ProtectionGuideContentHasher {
    /** Canonical form: protectionType + LF + trimmed description + LF + sanitized HTML. */
    public String sha256(ProtectionType protectionType, String shortDescription, String sanitizedHtml) {
        String canonical = protectionType.name() + "\n" + shortDescription.trim() + "\n" + sanitizedHtml;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
