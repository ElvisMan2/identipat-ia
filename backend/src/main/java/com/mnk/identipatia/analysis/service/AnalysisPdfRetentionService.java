package com.mnk.identipatia.analysis.service;

import com.mnk.identipatia.analysis.config.AnalysisProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Service
public class AnalysisPdfRetentionService {
    private final JdbcTemplate jdbc;
    private final AnalysisProperties properties;
    private final Clock clock;

    public AnalysisPdfRetentionService(JdbcTemplate jdbc, AnalysisProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /** Deletes only documents whose analysis has remained terminal past the support window. */
    @Scheduled(fixedDelayString = "#{@analysisPdfCleanupIntervalMillis}")
    @Transactional
    public int deleteExpiredTerminalDocuments() {
        Instant cutoff = clock.instant().minus(properties.pdf().retention());
        return jdbc.update("""
                DELETE FROM analysis_pdf_documents document
                USING analyses analysis
                WHERE document.analysis_id = analysis.analysis_id
                  AND analysis.status IN ('COMPLETED', 'FAILED')
                  AND analysis.updated_at < ?
                """, Timestamp.from(cutoff));
    }
}
