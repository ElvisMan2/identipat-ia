package com.mnk.identipatia.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.analysis.result.AnalysisResultReader;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListItem;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import com.mnk.identipatia.reporting.service.OperationalAnalysisResultPresenter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalAnalysisResultPresenterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisResultReader reader = new AnalysisResultReader(objectMapper);
    private final OperationalAnalysisResultPresenter presenter =
            new OperationalAnalysisResultPresenter();

    @Test
    void presentsV1OtherAndV2WithTheSameCitizenLabelsUsedByA1() throws Exception {
        var v1 = reader.read("analysis-result/1.0", objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/1.0",
                  "summary":"Resumen historico.",
                  "patentabilityAssessment":{"outcome":"INSUFFICIENT_INFORMATION","rationale":"Razon."},
                  "protectionOptions":[
                    {"type":"COPYRIGHT","applicability":"LIKELY","rationale":"Razon."},
                    {"type":"OTHER","applicability":"POSSIBLE","rationale":"Razon."}
                  ],
                  "observations":[],"warnings":[]
                }
                """));
        var v2 = reader.read("analysis-result/2.0", objectMapper.readTree("""
                {
                  "schemaVersion":"analysis-result/2.0",
                  "summary":"INVENTION_PATENT (POSSIBLE) y COPYRIGHT.",
                  "protectionOptions":[{
                    "type":"INVENTION_PATENT","applicability":"POSSIBLE",
                    "protectedSubjectMatter":"Materia.","rationale":"Razon.",
                    "legalBasis":[{"instrument":"DECISION_486","articles":["14"]}]
                  }],
                  "patentScreening":{
                    "applicable":true,
                    "article15":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]},
                    "article20":{"assessment":"NO_POTENTIAL_MATCH","rationale":"Sin coincidencias.","matches":[]}
                  },
                  "geneticResourceAccess":{
                    "assessment":"NOT_INDICATED","rationale":"No aplica.","missingInformation":[]
                  },
                  "observations":[],"warnings":[]
                }
                """));

        var presentedV1 = presenter.present(v1);
        var presentedV2 = presenter.present(v2);

        assertThat(presentedV1.modalities())
                .containsExactly("Derecho de autor", "Otra modalidad");
        assertThat(presentedV1.applicability())
                .extracting(value -> value.modality() + ": " + value.applicability())
                .containsExactly("Derecho de autor: Se relaciona claramente",
                        "Otra modalidad: Podría corresponder");
        assertThat(presentedV2.conclusion())
                .isEqualTo("patente de invención — podría corresponder y derecho de autor.");
        assertThat(presentedV2.modalities()).containsExactly("Patente de invención");
    }

    @Test
    void listItemCopiesModalitiesAndRedactsPiiFromToString() {
        List<String> modalities = new ArrayList<>(List.of("Derecho de autor"));
        OperationalAnalysisListItem item = new OperationalAnalysisListItem(
                UUID.randomUUID(), Instant.parse("2026-11-03T15:00:00Z"),
                AnalysisStatus.COMPLETED, "NombreSecreto", "ApellidoSecreto",
                "DNI", "DocumentoSecreto", AnalysisInputType.TEXT,
                "ConclusionSecreta", modalities, OperationalAnalysisOrigin.ADMIN, false);

        modalities.add("Otra modalidad");

        assertThat(item.identifiedModalities()).containsExactly("Derecho de autor");
        assertThat(item.toString()).doesNotContain(
                "NombreSecreto", "ApellidoSecreto", "DocumentoSecreto", "ConclusionSecreta");
    }
}
