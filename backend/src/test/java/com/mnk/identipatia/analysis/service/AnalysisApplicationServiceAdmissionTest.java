package com.mnk.identipatia.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.ai.GenerativeAiProvider;
import com.mnk.identipatia.ai.config.AiProperties;
import com.mnk.identipatia.analysis.config.AnalysisProperties;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.repository.AnalysisRepository;
import com.mnk.identipatia.analysis.repository.StoredAnalysisResultRepository;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.legal.service.TermsAcceptanceService;
import com.mnk.identipatia.model.User;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceClient;
import com.mnk.identipatia.preprocessing.client.PreprocessingServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisApplicationServiceAdmissionTest {
    @Mock AnalysisRepository analysisRepository;
    @Mock StoredAnalysisResultRepository resultRepository;
    @Mock TermsAcceptanceService termsAcceptanceService;
    @Mock AnalysisProperties analysisProperties;
    @Mock AnalysisProperties.Pdf pdfProperties;
    @Mock AnalysisProperties.Audio audioProperties;
    @Mock AiProperties aiProperties;
    @Mock ObjectProvider<GenerativeAiProvider> providers;
    @Mock GenerativeAiProvider provider;
    @Mock PreprocessingServiceClient preprocessingClient;
    @Mock AnalysisAdmissionService admissionService;

    private AnalysisApplicationService service;
    private AnalysisAccessContext admin;

    @BeforeEach
    void setUp() {
        lenient().when(analysisProperties.pdf()).thenReturn(pdfProperties);
        lenient().when(analysisProperties.audio()).thenReturn(audioProperties);
        lenient().when(pdfProperties.maxFileSizeBytes()).thenReturn(10_000L);
        lenient().when(audioProperties.maxFileSizeBytes()).thenReturn(10_000L);
        when(aiProperties.enabled()).thenReturn(true);
        when(providers.orderedStream()).thenReturn(Stream.of(provider));
        service = new AnalysisApplicationService(
                analysisRepository, resultRepository, termsAcceptanceService, analysisProperties,
                aiProperties, providers, new ObjectMapper(), preprocessingClient, admissionService);
        User user = new User();
        user.setUserId(42L);
        admin = AnalysisAccessContext.admin(user);
    }

    @Test
    void rejectedPdfNeverCallsPython() {
        when(admissionService.reserve(admin, AnalysisInputType.PDF)).thenThrow(capacity());
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", "%PDF-valid".getBytes());

        assertThatThrownBy(() -> service.createPdf(admin, pdf))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ANALYSIS_CAPACITY_EXCEEDED");
        verify(preprocessingClient, never()).extractPdf(any());
    }

    @Test
    void rejectedAudioNeverCallsWhisper() {
        when(admissionService.reserve(admin, AnalysisInputType.AUDIO)).thenThrow(capacity());
        MockMultipartFile audio = new MockMultipartFile(
                "file", "sample.webm", "audio/webm", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createAudio(admin, audio)).isInstanceOf(ApiException.class);
        verify(preprocessingClient, never()).transcribeAudio(any(), any());
    }

    @Test
    void preprocessingFailureReleasesPdfReservation() {
        AnalysisAdmissionService.Reservation reservation =
                new AnalysisAdmissionService.Reservation(java.util.UUID.randomUUID(), true);
        when(admissionService.reserve(admin, AnalysisInputType.PDF)).thenReturn(reservation);
        when(preprocessingClient.extractPdf(any()))
                .thenThrow(new PreprocessingServiceUnavailableException("timeout"));
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", "%PDF-valid".getBytes());

        assertThatThrownBy(() -> service.createPdf(admin, pdf)).isInstanceOf(ApiException.class);
        verify(admissionService).release(reservation);
    }

    private static ApiException capacity() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "ANALYSIS_CAPACITY_EXCEEDED", "Capacity exceeded", 10L);
    }
}
