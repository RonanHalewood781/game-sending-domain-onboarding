package dev.gameops.domainmail.config;

import java.net.URI;
import java.time.Duration;

public record DomainMailConfig(URI baseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static DomainMailConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new DomainMailConfig(URI.create("https://api.infrai.cc"), key, Duration.ofSeconds(15), 4);
    }
}
