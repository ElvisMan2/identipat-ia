package com.mnk.identipatia.ai.prompt;

import com.mnk.identipatia.ai.model.PromptReference;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PromptRegistry {

    private static final String ROOT = "prompts/";

    public PromptTemplates load(PromptReference reference) {
        String directory = ROOT + reference.promptId() + "/v" + reference.version() + "/";
        return new PromptTemplates(
                read(directory + "system.md", reference),
                read(directory + "user.md", reference));
    }

    private String read(String path, PromptReference reference) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt not found: " + reference.promptId()
                    + " version " + reference.version());
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Prompt could not be read: " + reference.promptId()
                    + " version " + reference.version(), exception);
        }
    }
}
