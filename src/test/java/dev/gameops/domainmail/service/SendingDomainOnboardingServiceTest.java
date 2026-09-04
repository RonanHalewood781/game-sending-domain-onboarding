package dev.gameops.domainmail.service;

import dev.gameops.domainmail.client.SendingDomainGateway;
import dev.gameops.domainmail.domain.GameMailWorkload;

public final class SendingDomainOnboardingServiceTest {
    public static void main(String[] args) {
        verifiedDomainWithClearQueueCanRelease();
        verifiedDomainWithPendingReviewStaysHeld();
        System.out.println("SendingDomainOnboardingServiceTest: PASS");
    }

    private static void verifiedDomainWithClearQueueCanRelease() {
        var service = new SendingDomainOnboardingService(new StubDomains("verified"));
        var result = service.onboard("MAIL.GAME.EXAMPLE", workload(0));
        assertEquals(SendingDomainOnboardingService.ReleaseDecision.READY_TO_SEND, result.decision());
        assertEquals("mail.game.example", result.domain());
    }

    private static void verifiedDomainWithPendingReviewStaysHeld() {
        var service = new SendingDomainOnboardingService(new StubDomains("verified"));
        var result = service.onboard("mail.game.example", workload(3));
        assertEquals(SendingDomainOnboardingService.ReleaseDecision.HOLD_FOR_MODERATION, result.decision());
    }

    private static GameMailWorkload workload(int pendingReviews) {
        return new GameMailWorkload(
                new GameMailWorkload.PlayerGeneratedAsset("skin-9", "player-4"),
                new GameMailWorkload.LiveEvent("launch-night", "Launch Night"),
                new GameMailWorkload.ModerationQueue("ugc-release", pendingReviews));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }

    private record StubDomains(String status) implements SendingDomainGateway {
        @Override public void requestVerification(String domain, String idempotencyKey) {}
        @Override public String verificationStatus(String domain) { return status; }
    }
}
