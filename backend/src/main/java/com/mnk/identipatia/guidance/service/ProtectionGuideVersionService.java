package com.mnk.identipatia.guidance.service;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.model.ProtectionGuideVersionCounter;
import com.mnk.identipatia.guidance.repository.ProtectionGuideVersionCounterRepository;
import org.springframework.stereotype.Service;

@Service
public class ProtectionGuideVersionService {
    private final ProtectionGuideVersionCounterRepository repository;

    public ProtectionGuideVersionService(ProtectionGuideVersionCounterRepository repository) {
        this.repository = repository;
    }

    public long nextVersion(ProtectionType protectionType) {
        ProtectionGuideVersionCounter counter = repository.findByProtectionTypeForUpdate(protectionType)
                .orElseThrow(() -> new IllegalStateException("Missing protection guide version counter"));
        long version = counter.getNextVersion();
        counter.setNextVersion(version + 1);
        return version;
    }

    public void lock(ProtectionType protectionType) {
        repository.findByProtectionTypeForUpdate(protectionType)
                .orElseThrow(() -> new IllegalStateException("Missing protection guide version counter"));
    }
}
