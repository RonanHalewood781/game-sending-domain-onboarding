package dev.gameops.domainmail.client;

import dev.gameops.domainmail.config.DomainMailConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient implements SendingDomainGateway {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern STATUS = Pattern.compile("\\\"verification\\\"\\s*:\\s*\\{[^}]*\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"error\\\"\\s*:\\s*\\{[^}]*\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final DomainMailConfig config;
    private final HttpClient http;

    public InfraiEmailClient(DomainMailConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiEmailClient(DomainMailConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void requestVerification(String domain, String idempotencyKey) {
        String body = "{\"domain\":\"" + jsonEscape(domain) + "\"}";
        HttpRequest request = baseRequest("/v1/email/domain/verify")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        sendEnvelope(request);
    }

    @Override
    public String verificationStatus(String domain) {
        String encoded = URLEncoder.encode(domain, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = baseRequest("/v1/email/domain/get/" + encoded).GET().build();
        String envelope = sendEnvelope(request);
        Matcher status = STATUS.matcher(envelope);
        if (!status.find()) throw new InfraiException("MALFORMED_ENVELOPE", 502);
        return status.group(1);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(config.baseUri() + path))
                .timeout(config.requestTimeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Accept", "application/json");
    }

    private String sendEnvelope(HttpRequest request) {
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpResponse<String> response = exchange(request);
            String body = response.body();
            Matcher ok = OK.matcher(body);
            if (!ok.find()) throw new InfraiException("MALFORMED_ENVELOPE", response.statusCode());
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.parseBoolean(ok.group(1))) {
                Matcher code = ERROR_CODE.matcher(body);
                throw new InfraiException(code.find() ? code.group(1) : "REQUEST_REJECTED", response.statusCode());
            }
            if (response.statusCode() >= 500) throw new InfraiException("TRANSPORT_FAILURE", response.statusCode());
            return body;
        }
        throw new InfraiException("RATE_LIMITED", 429);
    }

    private HttpResponse<String> exchange(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InfraiException("TRANSPORT_FAILURE", 503, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", 503, e);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .filter(value -> value.matches("\\d+"))
                .map(Long::parseLong)
                .map(Duration::ofSeconds)
                .orElse(Duration.ofMillis(250L * (1L << (attempt - 1))));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", 503, e);
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int httpStatus;

        public InfraiException(String code, int httpStatus) {
            super(code);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public InfraiException(String code, int httpStatus, Throwable cause) {
            super(code, cause);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() { return code; }
        public int httpStatus() { return httpStatus; }
    }
}
