package com.mnk.identipatia.guidance.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Component
public class ProtectionGuideSanitizer {
    public static final String POLICY_VERSION = "protection-guide-html/1.0";

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "h1", "h2", "h3", "h4", "div", "strong", "em", "u", "s", "ul", "ol", "li", "img")
            .allowStyling()
            .allowAttributes("alt").onElements("img")
            .allowAttributes("src").matching((elementName, attributeName, value) ->
                isAllowedHttps(value) ? value : null).onElements("img")
            .allowElements((elementName, attributes) -> {
                removeAttribute(attributes, "target");
                removeAttribute(attributes, "rel");
                attributes.add("target");
                attributes.add("_blank");
                attributes.add("rel");
                attributes.add("noopener noreferrer");
                return elementName;
            }, "a")
            .allowUrlProtocols("https")
            .allowAttributes("href").matching((elementName, attributeName, value) ->
                    isAllowedHttps(value) ? value : null).onElements("a")
            .toFactory();

    public String sanitize(String html) {
        return POLICY.sanitize(html);
    }

    public boolean hasVisibleText(String sanitizedHtml) {
        return !extractVisibleText(sanitizedHtml).isBlank();
    }

    public String extractVisibleText(String sanitizedHtml) {
        StringBuilder text = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(sanitizedHtml), new HTMLEditorKit.ParserCallback() {
                @Override
                public void handleText(char[] data, int position) {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(data);
                }

                @Override
                public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if (tag == HTML.Tag.BR) {
                        text.append(' ');
                    }
                }
            }, true);
        } catch (IOException exception) {
            throw new IllegalArgumentException("The protection guide HTML could not be parsed", exception);
        }
        return text.toString().trim();
    }

    private static boolean isAllowedHttps(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static void removeAttribute(List<String> attributes, String name) {
        for (int index = attributes.size() - 2; index >= 0; index -= 2) {
            if (name.equalsIgnoreCase(attributes.get(index))) {
                attributes.remove(index + 1);
                attributes.remove(index);
            }
        }
    }
}
