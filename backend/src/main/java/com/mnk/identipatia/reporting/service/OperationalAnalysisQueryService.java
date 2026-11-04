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
import com.mnk.identipatia.dto.PagedResponse;
import com.mnk.identipatia.guidance.repository.GuidanceViewRepository;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListItem;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.repository.OperationalAnalysisSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OperationalAnalysisQueryService {
    private final AnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final StoredAnalysisResultRepository resultRepository;
    private final GuidanceViewRepository guidanceViewRepository;
    private final AnalysisResultReader resultReader;
    private final OperationalAnalysisResultPresenter resultPresenter;

    public OperationalAnalysisQueryService(
            AnalysisRepository analysisRepository,
            UserRepository userRepository,
            StoredAnalysisResultRepository resultRepository,
            GuidanceViewRepository guidanceViewRepository,
            ObjectMapper objectMapper,
            OperationalAnalysisResultPresenter resultPresenter) {
        this.analysisRepository = analysisRepository;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.guidanceViewRepository = guidanceViewRepository;
        this.resultReader = new AnalysisResultReader(objectMapper);
        this.resultPresenter = resultPresenter;
    }

    @Transactional(readOnly = true)
    public PagedResponse<OperationalAnalysisListItem> findAll(
            OperationalAnalysisListCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Page<Analysis> analyses = analysisRepository.findAll(
                OperationalAnalysisSpecifications.matching(criteria.filters()), criteria.toPageable());
        if (analyses.isEmpty()) {
            return new PagedResponse<>(
                    java.util.List.of(),
                    analyses.getNumber(),
                    analyses.getSize(),
                    analyses.getTotalElements(),
                    analyses.getTotalPages(),
                    analyses.isFirst(),
                    analyses.isLast());
        }

        Set<UUID> analysisIds = analyses.stream()
                .map(Analysis::getAnalysisId)
                .collect(Collectors.toSet());
        Set<Long> userIds = analyses.stream()
                .map(Analysis::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> users = index(userRepository.findAllById(userIds), User::getUserId);
        Map<UUID, AnalysisResult> results = readResults(
                resultRepository.findAllById(analysisIds));
        Set<UUID> guidanceConsulted = guidanceViewRepository
                .findDistinctAnalysisIdsByAnalysisIdIn(analysisIds);

        return PagedResponse.from(analyses.map(analysis -> item(
                analysis, users, results, guidanceConsulted)));
    }

    private OperationalAnalysisListItem item(
            Analysis analysis,
            Map<Long, User> users,
            Map<UUID, AnalysisResult> results,
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
                ? new OperationalAnalysisResultPresenter.PresentedResult(null, java.util.List.of(), java.util.List.of())
                : resultPresenter.present(result);

        return new OperationalAnalysisListItem(
                analysisId,
                analysis.getCreatedAt(),
                analysis.getStatus(),
                user.getFirstName(),
                lastNames(user),
                user.getDoiType(),
                user.getDoi(),
                analysis.getInputType(),
                presented.conclusion(),
                presented.modalities(),
                analysis.getSessionId() == null
                        ? OperationalAnalysisOrigin.ADMIN
                        : OperationalAnalysisOrigin.STANDARD,
                guidanceConsulted.contains(analysisId));
    }

    private Map<UUID, AnalysisResult> readResults(Collection<StoredAnalysisResult> storedResults) {
        return storedResults.stream().collect(Collectors.toUnmodifiableMap(
                StoredAnalysisResult::getAnalysisId,
                stored -> {
                    try {
                        return resultReader.read(stored.getSchemaVersion(), stored.getResultJson());
                    } catch (JsonProcessingException | IllegalArgumentException exception) {
                        throw structural(stored.getAnalysisId(),
                                "Stored analysis result is invalid or unsupported", exception);
                    }
                }));
    }

    private static String lastNames(User user) {
        return Stream.of(user.getPaternalLastName(), user.getMaternalLastName())
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
