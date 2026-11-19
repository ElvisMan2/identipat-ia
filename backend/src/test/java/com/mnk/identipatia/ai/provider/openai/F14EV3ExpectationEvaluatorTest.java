package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class F14EV3ExpectationEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final F14EV3ExpectationEvaluator evaluator = new F14EV3ExpectationEvaluator();

    @Test
    void evaluatesDeterministicChecksAndDefersAmbiguousAllowedOperator() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {
                  "positiveSignals": [
                    {"path":"protectionOptions[type=INVENTION_PATENT]", "operator":"exists", "value":true},
                    {"path":"traditionalKnowledgeAccess.assessment", "operator":"equals", "value":"NOT_INDICATED"},
                    {"path":"geneticResourceAccess.assessment", "operator":"in", "value":["NOT_INDICATED","INSUFFICIENT_INFORMATION"]},
                    {"path":"patentScreening.article15.matches[literal=e]", "operator":"exists", "value":true},
                    {"path":"protectionOptions[type=COPYRIGHT]", "operator":"allowed", "value":true},
                    {"path":"summary", "operator":"sameAs", "value":"traditionalKnowledgeAccess.rationale"}
                  ],
                  "negativeSignals": []
                }
                """);
        JsonNode output = mapper.readTree("""
                {
                  "protectionOptions":[{"type":"INVENTION_PATENT"},{"type":"COPYRIGHT"}],
                  "traditionalKnowledgeAccess":{"assessment":"NOT_INDICATED","rationale":"same text"},
                  "geneticResourceAccess":{"assessment":"INSUFFICIENT_INFORMATION"},
                  "patentScreening":{"article15":{"matches":[{"literal":"e"}]}},
                  "summary":"same text", "observations":["same text"]
                }
                """);

        F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, output);

        assertThat(evaluation.checks()).hasSize(6);
        assertThat(evaluation.passed()).isEqualTo(5);
        assertThat(evaluation.failed()).isZero();
        assertThat(evaluation.needsExpertReview()).isEqualTo(1);
        assertThat(evaluation.checks().get(4).status())
                .isEqualTo(F14EV3ExpectationEvaluator.Status.NEEDS_EXPERT_REVIEW);
        assertThat(evaluation.approvedWithoutExpertReview()).isFalse();
    }

    @Test
    void evaluatesNegativeExistenceEqualityAndNonEmptySignalsAsForbiddenConditions() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {
                  "positiveSignals": [],
                  "negativeSignals": [
                    {"path":"protectionOptions[type=INVENTION_PATENT]", "operator":"exists", "value":false},
                    {"path":"traditionalKnowledgeAccess.assessment", "operator":"equals", "value":"POTENTIALLY_REQUIRED"},
                    {"path":"traditionalKnowledgeAccess.missingInformation", "operator":"nonEmpty", "value":true},
                    {"path":"warnings", "operator":"sameAs", "value":"summary"}
                  ]
                }
                """);
        JsonNode output = mapper.readTree("""
                {
                  "protectionOptions":[],
                  "traditionalKnowledgeAccess":{"assessment":"NOT_INDICATED","missingInformation":[]},
                  "warnings":["warning"], "summary":"summary"
                }
                """);

        F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, output);

        assertThat(evaluation.checks()).extracting(F14EV3ExpectationEvaluator.CheckResult::status)
                .containsOnly(F14EV3ExpectationEvaluator.Status.PASS);
        assertThat(evaluation.approvedWithoutExpertReview()).isTrue();
    }

    @Test
    void reportsSemanticallyDefinedOperatorsForExpertReview() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {
                  "positiveSignals":[
                    {"path":"warnings", "operator":"containsConcept", "value":"termodinámica", "rationale":"conceptual"},
                    {"path":"patentScreening.article15.matches", "operator":"automatic", "value":false, "rationale":"causal"}
                  ],
                  "negativeSignals":[
                    {"path":"warnings", "operator":"requires", "value":"hardware externo", "rationale":"requires interpretation"}
                  ]
                }
                """);
        JsonNode output = mapper.readTree("""
                {"warnings":["El mecanismo descrito no resulta compatible con la termodinámica."],
                 "patentScreening":{"article15":{"matches":[]}}}
                """);

        F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, output);

        assertThat(evaluation.needsExpertReview()).isEqualTo(3);
        assertThat(evaluation.failed()).isZero();
        assertThat(evaluation.approvedWithoutExpertReview()).isFalse();
        assertThat(evaluation.checks()).allSatisfy(check ->
                assertThat(check.evidence()).contains("needs semantic or causal interpretation"));
    }

    @Test
    void identifiesUnknownOperatorsInsteadOfMarkingThemPassed() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {"positiveSignals":[{"path":"summary", "operator":"inventedOperator", "value":"x"}], "negativeSignals":[]}
                """);
        JsonNode output = mapper.readTree("{\"summary\":\"x\"}");

        F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, output);

        assertThat(evaluation.unsupportedOperators()).isEqualTo(1);
        assertThat(evaluation.allDeterministicChecksPass()).isFalse();
        assertThat(evaluation.checks().getFirst().evidence()).contains("not implemented");
    }

    @Test
    void missingValuesFailDeterministicChecksAndReturnConcreteEvidence() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {
                  "positiveSignals":[
                    {"path":"traditionalKnowledgeAccess.assessment", "operator":"equals", "value":"NOT_INDICATED"},
                    {"path":"protectionOptions[type=COPYRIGHT]", "operator":"exists", "value":true}
                  ],
                  "negativeSignals":[]
                }
                """);
        JsonNode output = mapper.readTree("{}");

        F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, output);

        assertThat(evaluation.failed()).isEqualTo(2);
        assertThat(evaluation.checks()).allSatisfy(check -> {
            assertThat(check.status()).isEqualTo(F14EV3ExpectationEvaluator.Status.FAIL);
            assertThat(check.evidence()).contains("<missing>");
        });
    }

    @Test
    void failingAssertionIsRepresentedAsFailureForTheCallerToAggregate() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {"positiveSignals":[{"path":"summary", "operator":"equals", "value":"expected"}], "negativeSignals":[]}
                """);
        JsonNode output = mapper.readTree("{\"summary\":\"actual\"}");

        F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, output);

        assertThat(evaluation.failed()).isEqualTo(1);
        assertThat(evaluation.checks().getFirst().status()).isEqualTo(F14EV3ExpectationEvaluator.Status.FAIL);
        assertThat(evaluation.checks().getFirst().evidence()).contains("verified: resolved value equals expected value");
    }

    @Test
    void marksNonApplicableConditionalExpectationsWithoutPassingOrFailingThem() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {"input":"No hay autorización declarada.","conditionalSignals":[
                  {"condition":"La empresa declara autorización","assertion":"mencionarla",
                   "applicabilityRule":{"inputContains":"autorización declarada"}}
                ]}
                """);

        var checks = evaluator.evaluateConditionalSignals(testCase);

        assertThat(checks).hasSize(1);
        assertThat(checks.getFirst().status()).isEqualTo(F14EV3ExpectationEvaluator.Status.NOT_APPLICABLE);
        assertThat(checks.getFirst().evidence()).contains("autorización declarada");
    }

    @Test
    void leavesApplicableConditionalExpectationsForExpertReview() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {"input":"La empresa declara autorización.","conditionalSignals":[
                  {"condition":"La empresa declara autorización","assertion":"mencionarla",
                   "applicabilityRule":{"inputContains":"declara autorización"}}
                ]}
                """);

        assertThat(evaluator.evaluateConditionalSignals(testCase).getFirst().status())
                .isEqualTo(F14EV3ExpectationEvaluator.Status.NEEDS_EXPERT_REVIEW);
    }

    @Test
    void negativeAndHypotheticalMentionsDoNotMakeConditionalPredicatesApplicable() {
        assertThat(F14EV3ExpectationEvaluator.inputAffirms(
                "No se ha aportado autorización suficiente para el caso.", "autorización suficiente")).isFalse();
        assertThat(F14EV3ExpectationEvaluator.inputAffirms(
                "Si después se incorpora un mecanismo técnico, habría que revisarlo.", "mecanismo técnico")).isFalse();
        assertThat(F14EV3ExpectationEvaluator.inputAffirms(
                "La empresa desarrolla un mecanismo técnico concreto y medido.", "mecanismo técnico concreto")).isTrue();
    }

    @Test
    void flagsAdditionalModalitiesForReviewWithConcreteEvidence() throws Exception {
        JsonNode testCase = mapper.readTree("""
                {"positiveSignals":[],"negativeSignals":[],
                 "reviewAdditionalModalities":{"enabled":true,"supportedModalities":["INVENTION_PATENT"],
                 "rationale":"review basis"}}
                """);
        JsonNode output = mapper.readTree("""
                {"protectionOptions":[{"type":"INVENTION_PATENT"},{"type":"COPYRIGHT"}]}
                """);

        var checks = evaluator.evaluate(testCase, output).checks();

        assertThat(checks).hasSize(1);
        assertThat(checks.getFirst().status()).isEqualTo(F14EV3ExpectationEvaluator.Status.NEEDS_EXPERT_REVIEW);
        assertThat(checks.getFirst().path()).isEqualTo("protectionOptions[type=COPYRIGHT]");
    }

    @Test
    void classifiesEveryOperatorCurrentlyUsedByTheGoldenSet() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/f1.4e/legal-evaluation-cases-v3.json")) {
            assertThat(stream).isNotNull();
            JsonNode fixture = mapper.readTree(stream);
            Set<String> operators = new LinkedHashSet<>();
            long conditionalCount = 0;
            long positiveCount = 0;
            long negativeCount = 0;
            JsonNode emptyOutput = mapper.createObjectNode();
            long reviewChecks = 0;
            long unsupportedChecks = 0;
            for (JsonNode testCase : fixture.path("cases")) {
                positiveCount += testCase.path("positiveSignals").size();
                negativeCount += testCase.path("negativeSignals").size();
                conditionalCount += testCase.path("conditionalSignals").size();
                for (JsonNode signal : testCase.path("positiveSignals")) {
                    operators.add(signal.path("operator").asText());
                }
                for (JsonNode signal : testCase.path("negativeSignals")) {
                    operators.add(signal.path("operator").asText());
                }
                F14EV3ExpectationEvaluator.Evaluation evaluation = evaluator.evaluate(testCase, emptyOutput);
                reviewChecks += evaluation.needsExpertReview();
                unsupportedChecks += evaluation.unsupportedOperators();
            }

            assertThat(fixture.path("cases").size()).isEqualTo(15);
            assertThat(positiveCount).isEqualTo(22);
            assertThat(negativeCount).isEqualTo(15);
            assertThat(conditionalCount).isEqualTo(11);
            assertThat(operators).containsExactlyInAnyOrder(
                    "absentBecauseOf", "allowed", "asserts", "automatic", "considers", "containsConcept",
                    "createdBy", "derivedFrom", "duplicates", "equals", "exists", "in", "nonEmpty",
                    "requires", "sameAs", "triggeredBy");
            assertThat(reviewChecks).isEqualTo(15);
            assertThat(unsupportedChecks).isZero();
            assertThat(positiveCount + negativeCount - reviewChecks - unsupportedChecks).isEqualTo(22);
        }
    }

    @Test
    void classifiesGoldenConditionalsAsNotApplicableOrExpertReviewUsingSeparateRules() throws Exception {
        try (InputStream casesStream = getClass().getResourceAsStream("/f1.4e/legal-evaluation-cases-v3.json");
             InputStream rulesStream = getClass().getResourceAsStream("/f1.4e/conditional-applicability-v1.json")) {
            JsonNode cases = mapper.readTree(casesStream).path("cases");
            JsonNode rules = mapper.createObjectNode().set("rules", mapper.readTree(rulesStream).path("rules"));
            long conditions = 0;
            long notApplicable = 0;
            long expertReview = 0;
            for (JsonNode testCase : cases) {
                conditions += testCase.path("conditionalSignals").size();
                var results = evaluator.evaluateConditionalSignals(testCase, rules);
                notApplicable += results.stream().filter(result -> result.status()
                        == F14EV3ExpectationEvaluator.Status.NOT_APPLICABLE).count();
                expertReview += results.stream().filter(result -> result.status()
                        == F14EV3ExpectationEvaluator.Status.NEEDS_EXPERT_REVIEW).count();
            }
            assertThat(conditions).isEqualTo(11);
            assertThat(rules.path("rules")).hasSize(11);
            assertThat(notApplicable).isEqualTo(9);
            assertThat(expertReview).isEqualTo(2);
        }
    }
}
