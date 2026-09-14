package com.mnk.identipatia.analysis.service;

import java.text.Normalizer;

final class TextNormalizer {
    private TextNormalizer() {
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String lineNormalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return Normalizer.normalize(lineNormalized, Normalizer.Form.NFC).trim();
    }
}
