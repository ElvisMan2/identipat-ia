package com.mnk.identipatia.ai.prompt;

import com.mnk.identipatia.ai.model.AiMessage;
import com.mnk.identipatia.ai.model.AiMessageRole;
import com.mnk.identipatia.ai.model.PromptReference;
import com.mnk.identipatia.ai.model.RenderedPrompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}", Pattern.MULTILINE);
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");
    private final PromptRegistry registry;

    public PromptRenderer(PromptRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public RenderedPrompt render(PromptReference reference, Map<String, String> variables) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(variables, "variables must not be null");
        variables.forEach(PromptRenderer::validateVariable);

        PromptTemplates templates = registry.load(reference);
        String systemTemplate = normalizeLineEndings(templates.systemTemplate());
        String userTemplate = normalizeLineEndings(templates.userTemplate());
        String templateSnapshot = snapshot(systemTemplate, userTemplate);

        Set<String> usedVariables = new HashSet<>();
        String system = replace(systemTemplate, variables, usedVariables);
        String user = replace(userTemplate, variables, usedVariables);
        if (!usedVariables.containsAll(variables.keySet())) {
            Set<String> unused = new HashSet<>(variables.keySet());
            unused.removeAll(usedVariables);
            throw new IllegalArgumentException("Unused prompt variables: " + String.join(",", unused));
        }

        String renderedSnapshot = snapshot(system, user);
        return new RenderedPrompt(
                reference,
                java.util.List.of(
                        new AiMessage(AiMessageRole.SYSTEM, system),
                        new AiMessage(AiMessageRole.USER, user)),
                sha256(templateSnapshot),
                sha256(renderedSnapshot),
                renderedSnapshot);
    }

    static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String snapshot(String system, String user) {
        return "[SYSTEM]\n" + system + "\n\n[USER]\n" + user;
    }

    private static String replace(String template, Map<String, String> variables, Set<String> used) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!VARIABLE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid prompt placeholder name");
            }
            if (!variables.containsKey(name)) {
                throw new IllegalArgumentException("Unresolved prompt placeholder: " + name);
            }
            used.add(name);
            matcher.appendReplacement(result, Matcher.quoteReplacement(normalizeLineEndings(variables.get(name))));
        }
        matcher.appendTail(result);
        String rendered = result.toString();
        if (rendered.contains("{{") || rendered.contains("}}")) {
            throw new IllegalArgumentException("Malformed or unresolved prompt placeholder");
        }
        return rendered;
    }

    private static void validateVariable(String name, String value) {
        if (name == null || !VARIABLE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid prompt variable name");
        }
        Objects.requireNonNull(value, "Prompt variable value must not be null");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
