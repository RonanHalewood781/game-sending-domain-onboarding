package dev.gameops.domainmail.service;

import dev.gameops.domainmail.client.SendingDomainGateway;
import dev.gameops.domainmail.domain.GameMailWorkload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class SendingDomainOnboardingService {
    public enum ReleaseDecision { READY_TO_SEND, HOLD_FOR_DOMAIN, HOLD_FOR_MODERATION }

    public record OnboardingResult(String domain, String verificationStatus, ReleaseDecision decision) {}

    private final SendingDomainGateway domains;

    public SendingDomainOnboardingService(SendingDomainGateway domains) {
        this.domains = domains;
    }

    public OnboardingResult onboard(String domain, GameMailWorkload workload) {
        String normalized = domain.strip().toLowerCase(Locale.ROOT);
        String operation = normalized + ":" + workload.event().eventId();
        String idempotencyKey = UUID.nameUUIDFromBytes(operation.getBytes(StandardCharsets.UTF_8)).toString();

        domains.requestVerification(normalized, idempotencyKey);
        String status = domains.verificationStatus(normalized);
        ReleaseDecision decision = decide(status, workload.moderationQueue().pendingReviews());
        return new OnboardingResult(normalized, status, decision);
    }

    static ReleaseDecision decide(String verificationStatus, int pendingReviews) {
        if (!"verified".equalsIgnoreCase(verificationStatus)) return ReleaseDecision.HOLD_FOR_DOMAIN;
        if (pendingReviews > 0) return ReleaseDecision.HOLD_FOR_MODERATION;
        return ReleaseDecision.READY_TO_SEND;
    }
}
