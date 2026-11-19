package com.mnk.identipatia.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline evaluator for the fixture's actual positiveSignals/negativeSignals format.
 *
 * Deterministic operators: exists, equals, in, nonEmpty, sameAs. The fixture's
 * allowed, absentBecauseOf, asserts, automatic, considers, containsConcept,
 * createdBy, derivedFrom, duplicates, requires, and triggeredBy operators need
 * legal, semantic, or causal interpretation and are never marked as passing.
 * Any other operator is reported as UNSUPPORTED_OPERATOR.
 */
final class F14EV3ExpectationEvaluator {

    enum Status {
        PASS,
        FAIL,
        NEEDS_EXPERT_REVIEW,
        NOT_APPLICABLE,
        UNSUPPORTED_OPERATOR
    }

    record CheckResult(
            String group,
            int index,
            String path,
            String operator,
            JsonNode expected,
            Status status,
            String actual,
            String evidence,
            String rationale) {
    }

    record Evaluation(List<CheckResult> checks) {
        Evaluation {
            checks = List.copyOf(checks);
        }

        long passed() {
            return checks.stream().filter(check -> check.status() == Status.PASS).count();
        }

        long failed() {
            return checks.stream().filter(check -> check.status() == Status.FAIL).count();
        }

        long needsExpertReview() {
            return checks.stream().filter(check -> check.status() == Status.NEEDS_EXPERT_REVIEW).count();
        }

        long unsupportedOperators() {
            return checks.stream().filter(check -> check.status() == Status.UNSUPPORTED_OPERATOR).count();
        }

        boolean allDeterministicChecksPass() {
            return !checks.isEmpty() && failed() == 0 && unsupportedOperators() == 0;
        }

        boolean approvedWithoutExpertReview() {
            return allDeterministicChecksPass() && needsExpertReview() == 0;
        }
    }

    private static final Pattern SEGMENT = Pattern.compile("^([^\\[]+)(?:\\[([^=\\]]+)=([^\\]]+)\\])?$");
    private static final java.util.Set<String> DETERMINISTIC_OPERATORS = java.util.Set.of(
            "exists", "equals", "in", "nonEmpty", "sameAs");
    private static final java.util.Set<String> EXPERT_REVIEW_OPERATORS = java.util.Set.of(
            "absentBecauseOf", "allowed", "asserts", "automatic", "considers", "containsConcept",
            "createdBy", "derivedFrom", "duplicates", "requires", "triggeredBy");

    Evaluation evaluate(JsonNode testCase, JsonNode output) {
        List<CheckResult> checks = new ArrayList<>();
        evaluateGroup(testCase.path("positiveSignals"), "mustHave", false, output, checks);
        evaluateGroup(testCase.path("negativeSignals"), "mustNotHave", true, output, checks);
        evaluateAdditionalModalities(testCase, output, checks);
        return new Evaluation(checks);
    }

    List<CheckResult> evaluateConditionalSignals(JsonNode testCase) {
        return evaluateConditionalSignals(testCase, null);
    }

    List<CheckResult> evaluateConditionalSignals(JsonNode testCase, JsonNode applicabilityRules) {
        List<CheckResult> results = new ArrayList<>();
        JsonNode signals = testCase.path("conditionalSignals");
        if (!signals.isArray()) return List.of();
        for (int i = 0; i < signals.size(); i++) {
            JsonNode signal = signals.get(i);
            String path = "conditionalSignals[" + i + "]";
            JsonNode rule = signal.path("applicabilityRule");
            JsonNode adjudication = applicabilityRule(applicabilityRules,
                    testCase.path("id").asText(), i);
            String input = testCase.path("input").asText().toLowerCase();
            boolean hasRule = adjudication != null || rule.path("inputContains").isTextual()
                    || rule.path("inputDoesNotContain").isTextual();
            boolean applicable = (!rule.path("inputContains").isTextual()
                    || inputAffirms(input, rule.path("inputContains").asText()))
                    && (!rule.path("inputDoesNotContain").isTextual()
                    || !inputAffirms(input, rule.path("inputDoesNotContain").asText()));
            Status status;
            if (adjudication != null && adjudication.path("status").isTextual()) {
                status = Status.valueOf(adjudication.path("status").asText());
            } else if (adjudication != null && adjudication.path("notApplicableWhenInputContains").isTextual()) {
                status = inputAffirms(input, adjudication.path("notApplicableWhenInputContains").asText())
                        ? Status.NOT_APPLICABLE : Status.NEEDS_EXPERT_REVIEW;
            } else if (adjudication != null && adjudication.path("notApplicableWhenInputOmits").isTextual()) {
                status = inputAffirms(input, adjudication.path("notApplicableWhenInputOmits").asText())
                        ? Status.NEEDS_EXPERT_REVIEW : Status.NOT_APPLICABLE;
            } else {
                status = !hasRule || applicable ? Status.NEEDS_EXPERT_REVIEW : Status.NOT_APPLICABLE;
            }
            String evidence = "Input applicability rule: " + (adjudication == null ? rule : adjudication)
                    + "; input=" + testCase.path("input").asText();
            results.add(new CheckResult("conditional", i, path, "applicabilityRule",
                    signal.get("assertion"), status, applicable ? "applicable" : "not established",
                    evidence, adjudication == null ? signal.path("condition").asText()
                            : adjudication.path("rationale").asText(signal.path("condition").asText())));
        }
        return List.copyOf(results);
    }

    private static JsonNode applicabilityRule(JsonNode rules, String caseId, int index) {
        if (rules == null || !rules.path("rules").isArray()) return null;
        for (JsonNode rule : rules.path("rules")) {
            if (caseId.equals(rule.path("caseId").asText()) && index == rule.path("index").asInt(-1)) return rule;
        }
        return null;
    }

    static boolean inputAffirms(String input, String phrase) {
        String normalized = input.toLowerCase();
        String needle = phrase.toLowerCase();
        int index = normalized.indexOf(needle);
        if (index < 0) return false;
        String prefix = normalized.substring(Math.max(0, index - 64), index);
        int sentenceStart = Math.max(prefix.lastIndexOf('.'), Math.max(prefix.lastIndexOf(';'), prefix.lastIndexOf(':')));
        String localPrefix = prefix.substring(sentenceStart + 1);
        boolean negated = Pattern.compile("\\b(no|sin|niega|niegan|nunca|ning[uú]n|ninguna|ninguno)\\b")
                .matcher(localPrefix).find();
        boolean hypothetical = Pattern.compile("\\b(si|cuando|en caso de|podr[ií]a|hipot[eé]ticamente|por ejemplo)\\b")
                .matcher(localPrefix).find();
        return !negated && !hypothetical;
    }

    private static void evaluateAdditionalModalities(JsonNode testCase, JsonNode output, List<CheckResult> checks) {
        JsonNode review = testCase.path("reviewAdditionalModalities");
        Set<String> supported = new java.util.LinkedHashSet<>();
        if (review.path("enabled").asBoolean(false)) {
            review.path("supportedModalities").forEach(node -> supported.add(node.asText()));
        } else {
            for (JsonNode signal : testCase.path("positiveSignals")) {
                String path = signal.path("path").asText("");
                java.util.regex.Matcher matcher = Pattern.compile("^protectionOptions\\[type=([^\\]]+)\\].*$")
                        .matcher(path);
                if (matcher.matches()) supported.add(matcher.group(1));
            }
        }
        List<JsonNode> modalities = resolve(output, "protectionOptions");
        if (modalities.size() != 1 || !modalities.getFirst().isArray()) return;
        int index = 0;
        for (JsonNode option : modalities.getFirst()) {
            String type = option.path("type").asText("");
            if (!supported.contains(type)) {
                checks.add(new CheckResult("additionalModalities", index++, "protectionOptions[type=" + type + "]",
                        "supportedModalities", null, Status.NEEDS_EXPERT_REVIEW, render(List.of(option)),
                        "Present modality is outside the case's explicitly expected set; review whether the input supports it.",
                        review.path("rationale").asText("Additional modality requires legal-functional review.")));
            }
        }
    }

    private static void evaluateGroup(
            JsonNode signals,
            String group,
            boolean negativeGroup,
            JsonNode output,
            List<CheckResult> checks) {
        if (!signals.isArray()) {
            checks.add(new CheckResult(group, 0, "", "", null, Status.FAIL,
                    signals.toString(), "fixture expectation collection is missing or is not an array", ""));
            return;
        }
        for (int index = 0; index < signals.size(); index++) {
            JsonNode signal = signals.get(index);
            String path = signal.path("path").asText("");
            String operator = signal.path("operator").asText("");
            JsonNode expected = signal.get("value");
            String rationale = signal.path("rationale").asText("");
            List<JsonNode> actualValues = resolve(output, path);
            checks.add(evaluateOne(group, index, negativeGroup, path, operator, expected,
                    rationale, actualValues, output));
        }
    }

    private static CheckResult evaluateOne(
            String group,
            int index,
            boolean negativeGroup,
            String path,
            String operator,
            JsonNode expected,
            String rationale,
            List<JsonNode> actualValues,
            JsonNode output) {
        String actual = render(actualValues);
        String evidence = "JSONPath `" + path + "` resolved to " + actual;
        if (!DETERMINISTIC_OPERATORS.contains(operator) && !EXPERT_REVIEW_OPERATORS.contains(operator)) {
            return result(group, index, path, operator, expected, Status.UNSUPPORTED_OPERATOR, actual,
                    evidence + "; operator `" + operator + "` is not implemented", rationale);
        }
        if (expected == null) {
            return result(group, index, path, operator, null, Status.FAIL, actual,
                    evidence + "; fixture value is missing", rationale);
        }
        if (EXPERT_REVIEW_OPERATORS.contains(operator)) {
            return result(group, index, path, operator, expected, Status.NEEDS_EXPERT_REVIEW, actual,
                    evidence + "; operator `" + operator + "` needs semantic or causal interpretation", rationale);
        }
        boolean pass;
        String verification;
        switch (operator) {
            case "exists" -> {
                if (!expected.isBoolean()) {
                    return result(group, index, path, operator, expected, Status.UNSUPPORTED_OPERATOR, actual,
                            evidence + "; `exists` requires a boolean fixture value", rationale);
                }
                boolean exists = !actualValues.isEmpty();
                pass = negativeGroup ? !exists : exists == expected.booleanValue();
                verification = negativeGroup ? "forbidden path does not exist"
                        : "resolved-path existence equals " + expected.booleanValue();
            }
            case "equals" -> {
                if (actualValues.size() != 1) {
                    return missingOrAmbiguous(group, index, path, operator, expected, actual, evidence, rationale);
                }
                pass = actualValues.getFirst().equals(expected);
                if (negativeGroup) {
                    pass = !pass;
                }
                verification = negativeGroup ? "resolved value does not equal forbidden value" : "resolved value equals expected value";
            }
            case "in" -> {
                if (actualValues.size() != 1 || !expected.isArray()) {
                    return missingOrAmbiguous(group, index, path, operator, expected, actual, evidence, rationale);
                }
                pass = false;
                for (JsonNode candidate : expected) {
                    if (actualValues.getFirst().equals(candidate)) {
                        pass = true;
                        break;
                    }
                }
                if (negativeGroup) {
                    pass = !pass;
                }
                verification = negativeGroup ? "resolved value is outside forbidden values" : "resolved value is one of the allowed values";
            }
            case "nonEmpty" -> {
                if (actualValues.size() != 1 || !expected.isBoolean()) {
                    return missingOrAmbiguous(group, index, path, operator, expected, actual, evidence, rationale);
                }
                if (negativeGroup && !expected.booleanValue()) {
                    return result(group, index, path, operator, expected, Status.UNSUPPORTED_OPERATOR, actual,
                            evidence + "; a negative `nonEmpty` signal must identify the forbidden non-empty state with value=true", rationale);
                }
                JsonNode actualNode = actualValues.getFirst();
                boolean nonEmpty = actualNode.isArray() ? !actualNode.isEmpty()
                        : actualNode.isTextual() && !actualNode.asText().isBlank();
                if (!actualNode.isArray() && !actualNode.isTextual()) {
                    return result(group, index, path, operator, expected, Status.UNSUPPORTED_OPERATOR, actual,
                            evidence + "; `nonEmpty` supports arrays and strings only", rationale);
                }
                pass = negativeGroup ? !nonEmpty : nonEmpty == expected.booleanValue();
                verification = negativeGroup ? "forbidden non-empty value is absent" : "non-empty state equals expected value";
            }
            case "sameAs" -> {
                if (!expected.isTextual() || actualValues.size() != 1) {
                    return missingOrAmbiguous(group, index, path, operator, expected, actual, evidence, rationale);
                }
                List<JsonNode> comparison = resolve(output, expected.asText());
                if (comparison.size() != 1) {
                    return result(group, index, path, operator, expected, Status.FAIL, actual,
                            evidence + "; comparison JSONPath `" + expected.asText() + "` did not resolve to exactly one value", rationale);
                }
                boolean same = actualValues.getFirst().equals(comparison.getFirst());
                pass = negativeGroup ? !same : same;
                verification = negativeGroup ? "resolved value differs from prohibited comparison value" : "resolved values are equal";
                evidence += "; comparison JSONPath `" + expected.asText() + "` resolved to " + comparison.getFirst();
            }
            default -> throw new IllegalStateException("Operator dispatch is incomplete");
        }
        return result(group, index, path, operator, expected, pass ? Status.PASS : Status.FAIL,
                actual, evidence + "; verified: " + verification, rationale);
    }

    private static CheckResult missingOrAmbiguous(
            String group,
            int index,
            String path,
            String operator,
            JsonNode expected,
            String actual,
            String evidence,
            String rationale) {
        return result(group, index, path, operator, expected, Status.FAIL, actual,
                evidence + "; required value is missing, malformed, or ambiguous", rationale);
    }

    private static CheckResult result(
            String group,
            int index,
            String path,
            String operator,
            JsonNode expected,
            Status status,
            String actual,
            String evidence,
            String rationale) {
        return new CheckResult(group, index, path, operator,
                expected == null ? null : expected.deepCopy(), status, actual, evidence, rationale);
    }

    private static List<JsonNode> resolve(JsonNode root, String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<JsonNode> current = List.of(root);
        for (String segment : splitPath(path)) {
            Matcher matcher = SEGMENT.matcher(segment);
            if (!matcher.matches()) {
                return List.of();
            }
            String property = matcher.group(1);
            String selectorField = matcher.group(2);
            String selectorValue = matcher.group(3);
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode parent : current) {
                JsonNode child = parent.get(property);
                if (child == null) {
                    continue;
                }
                if (selectorField == null) {
                    next.add(child);
                } else if (child.isArray()) {
                    for (JsonNode candidate : child) {
                        JsonNode selector = candidate.get(selectorField);
                        if (selector != null && selector.asText().equals(selectorValue)) {
                            next.add(candidate);
                        }
                    }
                }
            }
            current = next;
            if (current.isEmpty()) {
                return List.of();
            }
        }
        return List.copyOf(current);
    }

    private static List<String> splitPath(String path) {
        List<String> segments = new ArrayList<>();
        int bracketDepth = 0;
        int start = 0;
        for (int i = 0; i < path.length(); i++) {
            char current = path.charAt(i);
            if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                bracketDepth--;
            } else if (current == '.' && bracketDepth == 0) {
                segments.add(path.substring(start, i));
                start = i + 1;
            }
        }
        segments.add(path.substring(start));
        return segments;
    }

    private static String render(List<JsonNode> values) {
        if (values.isEmpty()) {
            return "<missing>";
        }
        if (values.size() == 1) {
            return values.getFirst().toString();
        }
        ArrayNode array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        values.forEach(array::add);
        return array.toString();
    }
}
