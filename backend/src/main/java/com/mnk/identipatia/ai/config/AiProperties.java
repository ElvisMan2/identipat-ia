package com.mnk.identipatia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled;
    private String provider = "openai";

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String provider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
