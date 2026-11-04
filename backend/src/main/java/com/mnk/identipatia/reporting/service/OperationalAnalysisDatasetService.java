package com.mnk.identipatia.reporting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.model.StoredAnalysisResult;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.analysis.result.AnalysisResult;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.analysis.result.AnalysisResultV1;
import com.mnk.identipatia.analysis.result.v2.AnalysisResultV2;
import com.mnk.identipatia.guidance.repository.GuidanceViewRepository;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.reporting.dto.DiagnosticReport;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisExportCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisFilters;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisReportRow;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisSort;
import com.mnk.identipatia.reporting.model.AnalysisReportContext;
import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.repository.AnalysisReportContextRepository;
import com.mnk.identipatia.reporting.repository.OperationalAnalysisSpecifications;
import com.mnk.identipatia.reporting.repository.ReportDisclaimerRepository;
import com.mnk.identipatia.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OperationalAnalysisDatasetService {
    private final AnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final AnalysisReportContextRepository contextRepository;
    private final ReportDisclaimerRepository disclaimerRepository;
    private final GuidanceViewRepository guidanceViewRepository;
    private final AnalysisResultReader resultReader;
    private final DiagnosticReportAssemblerV1 assemblerV1;
    private final DiagnosticReportAssemblerV2 assemblerV2;
    private final OperationalDiagnosticReportTextRenderer textRenderer;
    private final OperationalAnalysisResultPresenter resultPresenter;

    public OperationalAnalysisDatasetService(
            AnalysisRepository analysisRepository,
            UserRepository userRepository,
            StoredAnalysisResultRepository resultRepository,
            AnalysisReportContextRepository contextRepository,
            ReportDisclaimerRepository disclaimerRepository,
            GuidanceViewRepository guidanceViewRepository,
            ObjectMapper objectMapper,
            DiagnosticReportAssemblerV1 assemblerV1,
            DiagnosticReportAssemblerV2 assemblerV2,
            OperationalDiagnosticReportTextRenderer textRenderer,
            OperationalAnalysisResultPresenter resultPresenter) {
        this.analysisRepository = analysisRepository;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.contextRepository = contextRepository;
        this.disclaimerRepository = disclaimerRepository;
        this.guidanceViewRepository = guidanceViewRepository;
        this.resultReader = new AnalysisResultReader(objectMapper);
        this.assemblerV1 = assemblerV1;
        this.assemblerV2 = assemblerV2;
        this.textRenderer = textRenderer;
        this.resultPresenter = resultPresenter;
    }

    @Transactional(readOnly = true)
    public List<OperationalAnalysisReportRow> build(OperationalAnalysisReportCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        OperationalAnalysisFilters filters = new OperationalAnalysisFilters(
                List.of(), criteria.from(), criteria.to(), null, null, null);
        List<Analysis> analyses = analysisRepository.findAll(
                OperationalAnalysisSpecifications.matching(filters),
                OperationalAnalysisSort.of(null, OperationalAnalysisExportCriteria.DEFAULT_SORT).toSort());
        return buildRows(analyses);
    }

    @Transactional(readOnly = true)
    public List<OperationalAnalysisReportRow> build(OperationalAnalysisExportCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        List<Analysis> analyses = analysisRepository.findAll(
                OperationalAnalysisSpecifications.matching(criteria.filters()), criteria.toSort());
        return buildRows(analyses);
    }

    private List<OperationalAnalysisReportRow> buildRows(List<Analysis> analyses) {
        if (analyses.isEmpty()) {
            return List.of();
        }

        Set<UUID> analysisIds = analyses.stream()
                .map(Analysis::getAnalysisId)
                .collect(Collectors.toSet());
        Set<Long> userIds = analyses.stream()
                .map(Analysis::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> users = index(userRepository.findAllById(userIds), User::getUserId);
        Map<UUID, StoredAnalysisResult> storedResults = index(
                resultRepository.findAllById(analysisIds), StoredAnalysisResult::getAnalysisId);
        Map<UUID, AnalysisReportContext> contexts = index(
                contextRepository.findAllById(analysisIds), AnalysisReportContext::getAnalysisId);

        validateContexts(contexts.values());
        Map<UUID, ReportDisclaimer> disclaimers = loadDisclaimers(contexts.values());
        Map<UUID, AnalysisResult> results = readResults(storedResults);
        Set<UUID> guidanceConsulted = guidanceViewRepository
                .findDistinctAnalysisIdsByAnalysisIdIn(analysisIds);

        return analyses.stream()
                .map(analysis -> row(analysis, users, results, contexts, disclaimers, guidanceConsulted))
                .toList();
    }

    private OperationalAnalysisReportRow row(
            Analysis analysis,
            Map<Long, User> users,
            Map<UUID, AnalysisResult> results,
            Map<UUID, AnalysisReportContext> contexts,
            Map<UUID, ReportDisclaimer> disclaimers,
            Set<UUID> guidanceConsulted) {
        UUID analysisId = analysis.getAnalysisId();
        User user = users.get(analysis.getUserId());
        if (user == null) {
            throw structural(analysisId, "Referenced user is unavailable");
        }

        AnalysisResult result = results.get(analysisId);
        if (analysis.getStatus() == AnalysisStatus.COMPLETED && result == null) {
            throw structural(analysisId, "Stored analysis result is unavailable");
        }
        OperationalAnalysisResultPresenter.PresentedResult presented = result == null
                ? new OperationalAnalysisResultPresenter.PresentedResult(null, List.of(), List.of())
                : resultPresenter.present(result);

        String reportText = null;
        boolean reportPdfAvailable = false;
        AnalysisReportContext context = contexts.get(analysisId);
        if (analysis.getStatus() == AnalysisStatus.COMPLETED && context != null) {
            ReportDisclaimer disclaimer = disclaimers.get(context.getDisclaimerId());
            DiagnosticReport report = assemble(analysis, result, context, disclaimer);
            reportText = textRenderer.render(report);
            reportPdfAvailable = true;
        }

        return new OperationalAnalysisReportRow(
                analysisId,
                presented.conclusion(),
                analysis.getStatus(),
                reportText,
                reportPdfAvailable,
                user.getFirstName(),
                lastNames(user),
                user.getDoiType(),
                user.getDoi(),
                user.getGender(),
                user.getBirthDate(),
                user.getPhone(),
                user.getMobilePhone(),
                user.getEmail(),
                user.getProfession(),
                analysis.getCreatedAt(),
                analysis.getInputType(),
                presented.modalities(),
                presented.applicability(),
                analysis.getSessionId() == null
                        ? OperationalAnalysisOrigin.ADMIN
                        : OperationalAnalysisOrigin.STANDARD,
                guidanceConsulted.contains(analysisId));
    }

    private Map<UUID, ReportDisclaimer> loadDisclaimers(Collection<AnalysisReportContext> contexts) {
        Set<UUID> disclaimerIds = contexts.stream()
                .map(AnalysisReportContext::getDisclaimerId)
                .collect(Collectors.toSet());
        if (disclaimerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ReportDisclaimer> disclaimers = index(
                disclaimerRepository.findAllById(disclaimerIds), ReportDisclaimer::getDisclaimerId);
        for (AnalysisReportContext context : contexts) {
            if (!disclaimers.containsKey(context.getDisclaimerId())) {
                throw structural(context.getAnalysisId(), "Referenced report disclaimer is unavailable");
            }
        }
        return disclaimers;
    }

    private void validateContexts(Collection<AnalysisReportContext> contexts) {
        for (AnalysisReportContext context : contexts) {
            try {
                DiagnosticReportPresentation.requireSupportedContext(context);
            } catch (IllegalArgumentException exception) {
                throw structural(context.getAnalysisId(), "Unsupported diagnostic report version", exception);
            }
        }
    }

    private Map<UUID, AnalysisResult> readResults(Map<UUID, StoredAnalysisResult> storedResults) {
        Map<UUID, AnalysisResult> results = new LinkedHashMap<>();
        storedResults.forEach((analysisId, stored) -> {
            try {
                results.put(analysisId,
                        resultReader.read(stored.getSchemaVersion(), stored.getResultJson()));
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw structural(analysisId, "Stored analysis result is invalid or unsupported", exception);
            }
        });
        return Collections.unmodifiableMap(results);
    }

    private DiagnosticReport assemble(
            Analysis analysis,
            AnalysisResult result,
            AnalysisReportContext context,
            ReportDisclaimer disclaimer) {
        try {
            if (result instanceof AnalysisResultV1 v1) {
                return assemblerV1.assemble(analysis, v1, context, disclaimer);
            }
            if (result instanceof AnalysisResultV2 v2) {
                return assemblerV2.assemble(analysis, v2, context, disclaimer);
            }
            throw new IllegalArgumentException("Unsupported in-memory analysis result type");
        } catch (RuntimeException exception) {
            throw structural(analysis.getAnalysisId(), "Diagnostic report could not be assembled", exception);
        }
    }

    private static String lastNames(User user) {
        return java.util.stream.Stream.of(user.getPaternalLastName(), user.getMaternalLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static <K, V> Map<K, V> index(Collection<V> values, Function<V, K> key) {
        return values.stream().collect(Collectors.toUnmodifiableMap(key, Function.identity()));
    }

    private static OperationalAnalysisDatasetException structural(UUID analysisId, String message) {
        return new OperationalAnalysisDatasetException(message + " for analysisId=" + analysisId);
    }

    private static OperationalAnalysisDatasetException structural(
            UUID analysisId, String message, Throwable cause) {
        return new OperationalAnalysisDatasetException(
                message + " for analysisId=" + analysisId, cause);
    }

}
