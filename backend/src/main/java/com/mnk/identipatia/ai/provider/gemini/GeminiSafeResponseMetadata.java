package com.mnk.identipatia.ai.provider.gemini;

import java.util.List;

record GeminiSafeResponseMetadata(
        List<String> finishReasons,
        String blockReason,
        int candidateCount,
        List<Integer> partCounts,
        boolean promptBlocked,
        List<Boolean> candidateBlocked,
        Integer promptTokenCount,
        Integer candidatesTokenCount,
        Integer thoughtsTokenCount,
        Integer totalTokenCount) {

    GeminiSafeResponseMetadata {
        finishReasons = List.copyOf(finishReasons);
        partCounts = List.copyOf(partCounts);
        candidateBlocked = List.copyOf(candidateBlocked);
    }
}
